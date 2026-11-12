package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.KnowledgePointView;
import com.zhixue.course.dto.KpMatchProfile;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.KpKeyword;
import com.zhixue.course.entity.KnowledgePrereq;
import com.zhixue.course.mapper.KnowledgePointMapper;
import com.zhixue.course.mapper.KnowledgePrereqMapper;
import com.zhixue.course.mapper.KpKeywordMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识点只读查询服务：按编号取实体/契约视图、课程级映射画像（概念+误区+关键词）、
 * 先修边关系（1 跳先修/后继、批量边供 PRP 的 1—2 跳 BFS）。
 * <p>
 * 边界：不做知识点写入/导入（内容经 SQL 种子维护），不做章节与课程查询（CourseService），
 * 不做题库（QuestionBankService），也不做映射打分（diagnosis 模块只经本服务取画像，
 * 打分口径在 KpMappingService）。
 */
@Service
public class KnowledgePointService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final KpKeywordMapper kpKeywordMapper;
    private final KnowledgePrereqMapper knowledgePrereqMapper;

    public KnowledgePointService(KnowledgePointMapper knowledgePointMapper,
                                 KpKeywordMapper kpKeywordMapper,
                                 KnowledgePrereqMapper knowledgePrereqMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.kpKeywordMapper = kpKeywordMapper;
        this.knowledgePrereqMapper = knowledgePrereqMapper;
    }

    /**
     * 按业务编号查询知识点（接口契约文档 3 节附录示例）。
     *
     * @param kpId 知识点编号，如 KP0201
     * @return 知识点；不存在抛 40400
     */
    public KnowledgePoint getById(String kpId) {
        KnowledgePoint kp = findById(kpId);
        if (kp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在: " + kpId);
        }
        return kp;
    }

    /**
     * 按业务编号查询知识点，不存在返回 null。
     * <p>
     * 供视图映射使用：历史记录引用的知识点被停用/删除时，页面仍应能返回记录本身，
     * 只把 kpName 降级为空，而不是让整个查询返回 40400。
     *
     * @param kpId 知识点编号
     * @return 知识点或 null
     */
    public KnowledgePoint findById(String kpId) {
        if (kpId == null || kpId.isBlank()) {
            return null;
        }
        return knowledgePointMapper.selectById(kpId);
    }

    /**
     * 批量按业务编号查询知识点，供列表视图一次补齐 kpName，避免 N+1。
     *
     * @param kpIds 知识点编号集合，允许空值/重复
     * @return kpId → 知识点；不存在的编号不放入结果
     */
    public Map<String, KnowledgePoint> findByIds(Collection<String> kpIds) {
        List<String> ids = kpIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return knowledgePointMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(KnowledgePoint::getKpId, kp -> kp));
    }

    /**
     * 契约视图查询：返回 openapi KnowledgePoint 字段，补齐 chapterName，
     * 不暴露 createdBy/isActive/时间戳等内部列。
     * <p>
     * 契约 course 组要求"只返回 is_active=1"：停用知识点属下架内容，不得经主键直读；
     * 查不到一律抛 40400，与 getById/findById 的"内部历史降级"语义分离。
     */
    public KnowledgePointView getViewById(String kpId) {
        KnowledgePoint kp = (kpId == null || kpId.isBlank()) ? null
                : knowledgePointMapper.selectOne(new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getKpId, kpId)
                        .eq(KnowledgePoint::getIsActive, 1)
                        .last("LIMIT 1"));
        if (kp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在或已停用: " + kpId);
        }
        return toView(kp,
                kp.getChapterId() == null ? null : knowledgePointMapper.selectChapterTitle(kp.getChapterId()));
    }

    /** 实体 → 契约视图（chapterName 由调用方提供，内部列一律不下发） */
    private KnowledgePointView toView(KnowledgePoint kp, String chapterName) {
        return new KnowledgePointView(kp.getKpId(), kp.getCourseId(), kp.getChapterId(),
                chapterName, kp.getName(), kp.getConceptText(), kp.getMisconception(),
                kp.getTextbookPage(), kp.getTrialHint(), kp.getSortNo(), kp.getDimensionKey());
    }

    /**
     * 取某课程下全部启用知识点的映射画像（知识点 + 概念 + 错因 + 关键词）。
     * diagnosis 模块通过本方法拿数据，不直接访问 course 的表。
     * <p>
     * 返回的是课程**全部**启用知识点，不做章节裁剪：章节只影响映射时"参与排序的候选集合"，
     * 不能让候选范围反过来改变 IDF 的统计范围（否则阈值标尺随章漂移）。
     *
     * @param courseId 课程 ID
     * @return 画像列表；课程无知识点时返回空列表
     */
    public List<KpMatchProfile> listMatchProfiles(Long courseId) {
        List<KnowledgePoint> kps = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .eq(KnowledgePoint::getCourseId, courseId)
                        .eq(KnowledgePoint::getIsActive, 1));
        if (kps.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> kpIds = kps.stream().map(KnowledgePoint::getKpId).toList();
        List<KpKeyword> keywords = kpKeywordMapper.selectList(
                new LambdaQueryWrapper<KpKeyword>().in(KpKeyword::getKpId, kpIds));
        Map<String, List<KpKeyword>> byKp = keywords.stream()
                .collect(Collectors.groupingBy(KpKeyword::getKpId));
        return kps.stream()
                .map(kp -> new KpMatchProfile(kp.getKpId(), kp.getName(), kp.getChapterId(),
                        kp.getConceptText(), kp.getMisconception(),
                        byKp.getOrDefault(kp.getKpId(), Collections.emptyList())))
                .toList();
    }

    /** 直接先修知识点（1 跳）。 */
    public List<String> prerequisites(String kpId) {
        return knowledgePrereqMapper.selectList(new LambdaQueryWrapper<KnowledgePrereq>()
                        .eq(KnowledgePrereq::getKpId, kpId))
                .stream().map(KnowledgePrereq::getPrereqKpId).toList();
    }

    /** 直接后继知识点（1 跳）。 */
    public List<String> successors(String kpId) {
        return knowledgePrereqMapper.selectList(new LambdaQueryWrapper<KnowledgePrereq>()
                        .eq(KnowledgePrereq::getPrereqKpId, kpId))
                .stream().map(KnowledgePrereq::getKpId).toList();
    }

    /**
     * 批量取某组知识点作为“后继”时的先修边（一次查询）。
     * 供补漏排序做 1—2 跳先修遍历，避免逐知识点调用 prerequisites 造成 N+1。
     *
     * @param kpIds 知识点编号集合
     * @return 以 kpId 为后继的先修边；入参为空时返回空列表
     */
    public List<KnowledgePrereq> listPrereqEdges(Collection<String> kpIds) {
        if (kpIds == null || kpIds.isEmpty()) {
            return List.of();
        }
        return knowledgePrereqMapper.selectList(new LambdaQueryWrapper<KnowledgePrereq>()
                .in(KnowledgePrereq::getKpId, kpIds));
    }
}

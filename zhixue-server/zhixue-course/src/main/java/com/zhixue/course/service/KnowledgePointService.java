package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.KpMatchProfile;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.KpKeyword;
import com.zhixue.course.entity.KnowledgePrereq;
import com.zhixue.course.mapper.KnowledgePointMapper;
import com.zhixue.course.mapper.KnowledgePrereqMapper;
import com.zhixue.course.mapper.KpKeywordMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识点服务：完整分层切片示例的 service 层。
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
        KnowledgePoint kp = knowledgePointMapper.selectById(kpId);
        if (kp == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识点不存在: " + kpId);
        }
        return kp;
    }

    /**
     * 取某课程下全部启用知识点的映射画像（知识点 + 关键词）。
     * diagnosis 模块通过本方法拿数据，不直接访问 course 的表。
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
                .map(kp -> new KpMatchProfile(kp.getKpId(), kp.getName(), kp.getConceptText(),
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
}

package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.entity.RecommendationRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.RecommendationRecordMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 三层推荐（契约 V2：GET /api/recommendations）。
 * <p>
 * CONSOLIDATE 同点巩固（难度 1）→ VARIANT 先修补强/同点变式（难度 2）
 * → ADVANCE 后继进阶/同点进阶（难度 3）。空池返回空数组，不报错。
 * 有 logId 时落 recommendation 表，保证 is_adopted 有源。
 */
@Service
public class RecommendationService {

    private final StudentErrorRecordService errorRecordService;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final RecommendationRecordMapper recommendationRecordMapper;

    public RecommendationService(StudentErrorRecordService errorRecordService,
                                 KnowledgePointService knowledgePointService,
                                 QuestionBankService questionBankService,
                                 RecommendationRecordMapper recommendationRecordMapper) {
        this.errorRecordService = errorRecordService;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.recommendationRecordMapper = recommendationRecordMapper;
    }

    /**
     * 按错题记录（logId）或知识点（kpId，竖切兜底）生成三层推荐。
     *
     * @param logId    错题记录编号；契约必填，竖切阶段可为空
     * @param kpId     知识点编号；logId 为空时使用
     * @param courseId 课程 ID
     * @return 1—3 条推荐；无题可推时返回空数组
     */
    public List<Recommendation> recommend(String logId, String kpId, Long courseId) {
        String resolvedKp;
        String anchorLogId = null;
        if (logId != null && !logId.isBlank()) {
            StudentErrorRecord record = errorRecordService.getConfirmed(logId);
            resolvedKp = record.getKpId();
            anchorLogId = logId;
        } else if (kpId != null && !kpId.isBlank()) {
            resolvedKp = kpId;
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "logId 与 kpId 至少提供一个");
        }
        knowledgePointService.getById(resolvedKp);

        List<QuestionView> same = new ArrayList<>(questionBankService.listByKp(courseId, resolvedKp));
        List<QuestionView> prereq = collect(knowledgePointService.prerequisites(resolvedKp), courseId);
        List<QuestionView> successor = collect(knowledgePointService.successors(resolvedKp), courseId);
        if (same.isEmpty() && prereq.isEmpty() && successor.isEmpty()) {
            return List.of();
        }

        Set<String> used = new HashSet<>();
        List<Recommendation> result = new ArrayList<>(3);

        add(result, choose(same, 1, used), "CONSOLIDATE", "同点巩固", used);

        QuestionView variant = choose(prereq, 2, used);
        if (variant == null) {
            variant = choose(same, 2, used);
        }
        add(result, variant, "VARIANT", prereq.isEmpty() ? "同点变式" : "先修补强", used);

        QuestionView advance = choose(successor, 3, used);
        if (advance == null) {
            advance = choose(same, 3, used);
        }
        add(result, advance, "ADVANCE", successor.isEmpty() ? "同点进阶" : "后继进阶", used);

        if (anchorLogId != null) {
            result = persist(anchorLogId, result);
        }
        return result;
    }

    private List<QuestionView> collect(List<String> kpIds, Long courseId) {
        List<QuestionView> pool = new ArrayList<>();
        for (String kpId : kpIds) {
            pool.addAll(questionBankService.listByKp(courseId, kpId));
        }
        return pool;
    }

    /** 选难度最接近目标且未被使用的题；没有可选项返回 null */
    private QuestionView choose(List<QuestionView> pool, int targetDifficulty, Set<String> used) {
        return pool.stream()
                .filter(q -> q.qId() != null && !used.contains(q.qId()))
                .min(Comparator.comparingInt(q -> Math.abs(
                        (q.difficulty() == null ? 2 : q.difficulty()) - targetDifficulty)))
                .orElse(null);
    }

    private void add(List<Recommendation> result, QuestionView question, String layer,
                     String reason, Set<String> used) {
        if (question == null) {
            return;
        }
        used.add(question.qId());
        result.add(new Recommendation(null, question.qId(), layer, reason,
                question.difficulty(), false, question.questionText()));
    }

    /** 有 logId 时落表：先清旧推荐，再插入本次 3 条，返回带 id 的结果 */
    private List<Recommendation> persist(String logId, List<Recommendation> result) {
        long userId = StpUtil.getLoginIdAsLong();
        recommendationRecordMapper.delete(new LambdaQueryWrapper<RecommendationRecord>()
                .eq(RecommendationRecord::getUserId, userId)
                .eq(RecommendationRecord::getLogId, logId));

        List<Recommendation> persisted = new ArrayList<>(result.size());
        for (Recommendation item : result) {
            RecommendationRecord row = new RecommendationRecord();
            row.setUserId(userId);
            row.setLogId(logId);
            row.setQId(item.qId());
            row.setLayer(item.layer());
            row.setReason(item.reason());
            row.setRankNo(1);
            row.setIsAdopted(0);
            recommendationRecordMapper.insert(row);
            persisted.add(new Recommendation(row.getRecommendationId(), item.qId(), item.layer(),
                    item.reason(), item.difficulty(), false, item.questionText()));
        }
        return persisted;
    }
}

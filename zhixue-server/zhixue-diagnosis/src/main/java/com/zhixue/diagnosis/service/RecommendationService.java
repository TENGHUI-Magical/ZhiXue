package com.zhixue.diagnosis.service;

import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 三层推荐（契约 V2：GET /api/recommendations）。
 * <p>
 * CONSOLIDATE 同点巩固 → VARIANT 变式迁移 → ADVANCE 进阶挑战，各取 1 题。
 * 当前直接按难度选题、未落 recommendation 表，recommendationId 返回 null；
 * 落表与冷却逻辑在推荐算法接入时补齐。
 */
@Service
public class RecommendationService {

    private final StudentErrorRecordService errorRecordService;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;

    public RecommendationService(StudentErrorRecordService errorRecordService,
                                 KnowledgePointService knowledgePointService,
                                 QuestionBankService questionBankService) {
        this.errorRecordService = errorRecordService;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
    }

    /**
     * 按错题记录（logId）或知识点（kpId，竖切兜底）生成三层推荐。
     *
     * @param logId    错题记录编号；契约必填，竖切阶段可为空
     * @param kpId     知识点编号；logId 为空时使用
     * @param courseId 课程 ID
     * @return 1—3 条推荐
     */
    public List<Recommendation> recommend(String logId, String kpId, Long courseId) {
        String resolvedKp;
        if (logId != null && !logId.isBlank()) {
            StudentErrorRecord record = errorRecordService.getConfirmed(logId);
            resolvedKp = record.getKpId();
        } else if (kpId != null && !kpId.isBlank()) {
            // 竖切兜底：错题记录模块未接入前，允许直接按知识点推荐；记录模块落地后删除本分支
            resolvedKp = kpId;
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "logId 与 kpId 至少提供一个");
        }
        knowledgePointService.getById(resolvedKp);

        List<QuestionView> pool = new ArrayList<>(
                questionBankService.listByKp(courseId, resolvedKp));
        if (pool.isEmpty()) {
            throw new BizException(ErrorCode.VECTOR_NO_MATCH, "该知识点暂无题目: " + resolvedKp);
        }

        List<Recommendation> result = new ArrayList<>(3);
        pick(result, pool, 1, "CONSOLIDATE", "同点巩固");
        pick(result, pool, 2, "VARIANT", "变式迁移");
        pick(result, pool, 3, "ADVANCE", "进阶挑战");
        return result;
    }

    /** 优先取目标难度，不足时从剩余题里补，保证每个分层都尽量有题 */
    private void pick(List<Recommendation> result, List<QuestionView> pool, int difficulty,
                      String layer, String reason) {
        if (pool.isEmpty()) {
            return;
        }
        QuestionView chosen = pool.stream()
                .filter(q -> q.difficulty() != null && q.difficulty() == difficulty)
                .findFirst()
                .orElse(pool.get(0));
        pool.remove(chosen);
        result.add(new Recommendation(null, chosen.qId(), layer, reason,
                chosen.difficulty(), false, chosen.questionText()));
    }
}

package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.PracticeCreateRequest;
import com.zhixue.diagnosis.dto.PracticeResultView;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.RecommendationRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.RecommendationRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 练习作答服务：自主/推荐作答、幂等、推荐采纳回写；
 * 同时为计划打卡提供 source=PLAN 的内部写入方法。
 */
@Service
public class PracticeService {

    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);

    private static final Set<String> SOURCES = Set.of("RECOMMEND", "SELF");
    private static final Set<String> ERROR_TYPES = ErrorTypeClassifier.ALLOWED;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PracticeRecordMapper practiceRecordMapper;
    private final RecommendationRecordMapper recommendationRecordMapper;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final TransactionTemplate transactionTemplate;

    public PracticeService(PracticeRecordMapper practiceRecordMapper,
                           RecommendationRecordMapper recommendationRecordMapper,
                           KnowledgePointService knowledgePointService,
                           QuestionBankService questionBankService,
                           TransactionTemplate transactionTemplate) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.recommendationRecordMapper = recommendationRecordMapper;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 提交作答（推荐题/自主练习）。
     * 幂等：同一 userId + clientToken 返回首次结果，duplicate=true；不返回 40900。
     */
    public PracticeResultView submit(PracticeCreateRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        PracticeRecord existing = findByClientToken(userId, request.clientToken());
        if (existing != null) {
            return new PracticeResultView(existing.getPracticeId(), true, null,
                    toBoolean(existing.getIsCorrect()));
        }

        validateLength(request);
        KnowledgePoint kp = knowledgePointService.getById(request.kpId());
        QuestionBank question = questionBankService.getById(request.qId());
        if (!Objects.equals(question.getCourseId(), kp.getCourseId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "题目与知识点不属于同一课程");
        }

        // 客观题由服务端按 answerText 判分，客户端 isCorrect 不参与；主观题保留自评
        AnswerGrader.Grading grading = AnswerGrader.grade(question, request.myAnswer(),
                request.isCorrect());
        if (AnswerGrader.SELF.equals(grading.mode()) && request.isCorrect() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "主观题必须提交自评结果");
        }
        boolean isCorrect = grading.correct();

        String source = request.source();
        if (source == null) {
            source = request.recommendationId() == null ? "SELF" : "RECOMMEND";
        }
        if (!SOURCES.contains(source)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "source 仅允许 RECOMMEND/SELF");
        }
        if (request.recommendationId() != null && !"RECOMMEND".equals(source)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "带 recommendationId 时 source 必须为 RECOMMEND");
        }

        String errorType = request.errorType();
        if (errorType != null && !ERROR_TYPES.contains(errorType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "errorType 非法: " + errorType);
        }
        // 05 §2.2：答错必须落五类错因或 UNCLASSIFIED，不允许再写 null；客观题不信任客户端错因
        errorType = resolveErrorType(userId, question, request.myAnswer(), isCorrect,
                errorType, request.kpId());

        RecommendationRecord recommendation = null;
        if (request.recommendationId() != null) {
            recommendation = recommendationRecordMapper.selectById(request.recommendationId());
            if (recommendation == null || !Objects.equals(recommendation.getUserId(), userId)) {
                throw new BizException(ErrorCode.NOT_FOUND, "推荐记录不存在");
            }
            if (!Objects.equals(recommendation.getQId(), request.qId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "推荐记录与 qId 不匹配");
            }
        }

        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setClientToken(request.clientToken());
        record.setQId(request.qId());
        record.setKpId(request.kpId());
        record.setSource(source);
        record.setIsCorrect(isCorrect ? 1 : 0);
        record.setMyAnswer(request.myAnswer());
        record.setErrorType(errorType);
        record.setCostSeconds(request.costSeconds());
        record.setRecommendationId(request.recommendationId());
        final RecommendationRecord adopted = recommendation;
        try {
            // 双写同事务（作答落库 + 推荐采纳回写），任一失败整体回滚。
            // 用编程式事务而非 @Transactional：DuplicateKeyException 的幂等回查必须发生在
            // 事务结束之后——方法级注解会在入口建立 REPEATABLE READ 快照，并发方在快照后
            // 提交的同 token 记录回查不可见，幂等会被破坏（StudentErrorRecordService 同理不包事务）。
            transactionTemplate.executeWithoutResult(tx -> {
                practiceRecordMapper.insert(record);
                if (adopted != null) {
                    recommendationRecordMapper.update(null,
                            new LambdaUpdateWrapper<RecommendationRecord>()
                                    .eq(RecommendationRecord::getRecommendationId,
                                            adopted.getRecommendationId())
                                    .eq(RecommendationRecord::getUserId, userId)
                                    .set(RecommendationRecord::getIsAdopted, 1));
                }
            });
        } catch (DuplicateKeyException e) {
            // 此处事务已回滚结束，回查开新连接，能看到并发方已提交的首次记录
            PracticeRecord winner = findByClientToken(userId, request.clientToken());
            if (winner != null) {
                return new PracticeResultView(winner.getPracticeId(), true, null,
                        toBoolean(winner.getIsCorrect()));
            }
            throw e;
        }

        PracticeRecord saved = practiceRecordMapper.selectById(record.getPracticeId());
        return new PracticeResultView(saved == null ? record.getPracticeId() : saved.getPracticeId(),
                false, grading.mode(), isCorrect);
    }

    /** 按 userId + clientToken 查首次作答。 */
    public PracticeRecord findByClientToken(long userId, String clientToken) {
        if (clientToken == null || clientToken.isBlank()) {
            return null;
        }
        return practiceRecordMapper.selectOne(new LambdaQueryWrapper<PracticeRecord>()
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getClientToken, clientToken)
                .last("LIMIT 1"));
    }

    /** 近 N 天已练题目；供计划生成做冷却，避免刚做过的题立刻再排。 */
    public Set<String> recentPracticedQids(long userId, int days) {
        LocalDateTime since = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusDays(days);
        return practiceRecordMapper.selectList(new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .ge(PracticeRecord::getCreatedAt, since))
                .stream()
                .map(PracticeRecord::getQId)
                .filter(qId -> qId != null && !qId.isBlank())
                .collect(Collectors.toSet());
    }

    /**
     * 推断并归一错因（05 §2.2）：答对返回 null；答错返回五类之一或 UNCLASSIFIED。
     * 客观题按题型/干扰项/难度/首次作答/距上次练习规则推断；主观题采用学生自评。
     * 无法细分时（题库缺标准答案、主观题未选错因）落 UNCLASSIFIED 并记日志，可从日志看出原因。
     * 供提交练习与计划打卡共用，避免两处口径。
     */
    public String resolveErrorType(long userId, QuestionBank question, String myAnswer,
                                   boolean isCorrect, String selfReported, String kpId) {
        if (isCorrect) {
            return null;
        }
        // 客观题缺标准答案时判分已按错误记录（AnswerGrader），错因同样无法细分：这是题库数据问题，
        // 记 warn 便于内容线定位；主观题未选错因是正常用户行为，不刷告警。
        if (question.getAnswerText() == null || question.getAnswerText().isBlank()) {
            log.warn("题目 {} 缺少标准答案，错因无法细分，按 UNCLASSIFIED 落库", question.getQId());
        }
        int priorAttempts = practiceRecordMapper.countByUserAndQid(userId, question.getQId());
        LocalDateTime lastPracticedAt = kpId == null || kpId.isBlank()
                ? null : practiceRecordMapper.selectLastPracticedAt(userId, kpId);
        Integer days = lastPracticedAt == null ? null
                : (int) Math.max(0, Duration.between(lastPracticedAt, LocalDateTime.now(ZONE)).toDays());
        return ErrorTypeClassifier.classify(question, myAnswer, false, selfReported,
                priorAttempts, days);
    }

    /** 计划打卡幂等查询：返回首次 PLAN 作答；不存在返回 null。 */
    public PlanCompletion findCompletionByToken(long userId, String clientToken) {
        PracticeRecord record = findByClientToken(userId, clientToken);
        if (record == null) {
            return null;
        }
        return new PlanCompletion(record.getPracticeId(), record.getQId(),
                record.getSource(), true, toBoolean(record.getIsCorrect()));
    }

    /**
     * 计划打卡写 practice_record（source=PLAN）。
     * 2026-10-31 C1：按真实作答写入 is_correct（不再固定 1）。
     * 仅“已作答”打卡才调用本方法；“仅记录进度”不写 practice_record。
     */
    public PlanCompletion recordPlanCompletion(long userId, String qId, String kpId,
                                               String clientToken, String myAnswer,
                                               boolean isCorrect, Integer costSeconds,
                                               String errorType) {
        if (myAnswer != null && myAnswer.length() > 20000) {
            throw new BizException(ErrorCode.PARAM_ERROR, "myAnswer 最长 20000 字");
        }
        if (errorType != null && !ERROR_TYPES.contains(errorType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "errorType 非法");
        }
        // 兜底不变量：答错必须落显式错因，历史调用方漏传时按 UNCLASSIFIED
        if (!isCorrect && (errorType == null || errorType.isBlank())) {
            errorType = ErrorTypeClassifier.UNCLASSIFIED;
        }
        PracticeRecord existing = findByClientToken(userId, clientToken);
        if (existing != null) {
            if (!Objects.equals(existing.getQId(), qId) || !"PLAN".equals(existing.getSource())) {
                throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "clientToken 已用于其他作答");
            }
            return new PlanCompletion(existing.getPracticeId(), existing.getQId(),
                    existing.getSource(), true, toBoolean(existing.getIsCorrect()));
        }

        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setClientToken(clientToken);
        record.setQId(qId);
        record.setKpId(kpId);
        record.setSource("PLAN");
        record.setIsCorrect(isCorrect ? 1 : 0);
        record.setMyAnswer(myAnswer);
        record.setCostSeconds(costSeconds);
        record.setErrorType(errorType);
        try {
            practiceRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            PracticeRecord winner = findByClientToken(userId, clientToken);
            if (winner != null && Objects.equals(winner.getQId(), qId)
                    && "PLAN".equals(winner.getSource())) {
                return new PlanCompletion(winner.getPracticeId(), winner.getQId(),
                        winner.getSource(), true, toBoolean(winner.getIsCorrect()));
            }
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "clientToken 冲突");
        }
        PracticeRecord saved = practiceRecordMapper.selectById(record.getPracticeId());
        return new PlanCompletion(saved == null ? record.getPracticeId() : saved.getPracticeId(),
                qId, "PLAN", false, isCorrect);
    }

    private void validateLength(PracticeCreateRequest request) {
        if (request.qId().length() > 20 || request.kpId().length() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR, "qId/kpId 最长 20 字");
        }
    }

    private static Boolean toBoolean(Integer value) {
        return value == null ? null : value == 1;
    }

    /**
     * 打卡写库结果。
     *
     * @param practiceId 作答记录 ID
     * @param qId        题目编号
     * @param source     来源（PLAN）
     * @param duplicate  是否幂等命中
     * @param isCorrect  首次记录的服务端最终对错（主观题=自评值）
     */
    public record PlanCompletion(Long practiceId, String qId, String source,
                                 boolean duplicate, Boolean isCorrect) {
    }
}

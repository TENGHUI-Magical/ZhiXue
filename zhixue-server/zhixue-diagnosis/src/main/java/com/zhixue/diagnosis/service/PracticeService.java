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
import com.zhixue.diagnosis.dto.PracticeStartRequest;
import com.zhixue.diagnosis.dto.PracticeStartView;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.RecommendationRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.RecommendationRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    /** 服务端计时上限（毫秒）：超过 30 分钟视为挂机，不计入进步证据（截断值仍是假数据） */
    static final long MAX_COST_MILLIS = 30 * 60 * 1000L;
    /** begin 打点 Redis 前缀（对齐 zhixue: 命名空间，IT cleanData 可统一清理）；TTL 24h 兜底防泄漏，提交成功后主动删除 */
    private static final String START_KEY_PREFIX = "zhixue:practice:start:";
    private static final Duration START_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PracticeRecordMapper practiceRecordMapper;
    private final RecommendationRecordMapper recommendationRecordMapper;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    public PracticeService(PracticeRecordMapper practiceRecordMapper,
                           RecommendationRecordMapper recommendationRecordMapper,
                           KnowledgePointService knowledgePointService,
                           QuestionBankService questionBankService,
                           TransactionTemplate transactionTemplate,
                           StringRedisTemplate redisTemplate) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.recommendationRecordMapper = recommendationRecordMapper;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.transactionTemplate = transactionTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 开始作答打点（进步证据的服务端计时入口，防伪造方案）：
     * 时间戳全部由服务端时钟产生并存 Redis，客户端只持有令牌——
     * 伪造令牌无记录（计 null）、重放打点不重置（setIfAbsent 首次为准）、
     * 拖后提交只会让时长变大（无收益）。剩余信任假设：Redis 与应用同机同时钟（单机部署成立）。
     * 幂等：同 clientToken 重复打点返回首次开始时刻。
     */
    public PracticeStartView begin(PracticeStartRequest request) {
        // 题目必须存在且启用：不存在就地 40400，避免发一个提交时必然失败的打点令牌（探针 H03）
        // 错误码复用 QuestionBankService#getById 的既有 "题目不存在" 口径，不新造码
        questionBankService.getById(request.qId());
        long userId = StpUtil.getLoginIdAsLong();
        String key = START_KEY_PREFIX + userId + ":" + request.clientToken();
        String now = String.valueOf(System.currentTimeMillis());
        String startedAt = now;
        try {
            Boolean first = redisTemplate.opsForValue()
                    .setIfAbsent(key, now, START_TTL);
            String stored = Boolean.TRUE.equals(first) ? now : redisTemplate.opsForValue().get(key);
            if (stored != null && !stored.isBlank()) {
                Long.parseLong(stored); // 损坏值按本次 now，避免下面格式化时抛 NumberFormatException
                startedAt = stored;
            }
        } catch (Exception e) {
            // 与提交侧计时读取同口径：Redis 故障不阻断“开始作答”，降级为服务端当前时刻
            log.warn("作答打点 Redis 不可用，本次降级为服务端当前时刻 userId={} token={} reason={}",
                    userId, request.clientToken(), e.toString());
        }
        return new PracticeStartView(request.clientToken(),
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Long.parseLong(startedAt)),
                        ZONE).format(DATE_TIME));
    }

    /**
     * 服务端计时的作答耗时（秒）。纯函数可单测：
     * 无打点 → null（客户端自报的 costSeconds 不再落库，防伪造）；
     * 跨天提交按 epoch 差计算（23:59→00:01 = 120 秒，恒为正）；
     * 超过 30 分钟 → null（挂机无效，不截断——截断值仍是假数据）；
     * 时钟回拨防御 → 0。
     */
    static Integer computeCostSeconds(Long startEpochMillis, long nowEpochMillis) {
        if (startEpochMillis == null) {
            return null;
        }
        long diff = nowEpochMillis - startEpochMillis;
        if (diff < 0) {
            return 0;
        }
        if (diff > MAX_COST_MILLIS) {
            return null;
        }
        return (int) (diff / 1000);
    }

    /** 取 begin 打点时刻并计算耗时；打点令牌无效或 Redis 不可用时返回 null（不阻断提交）。 */
    private Integer resolveServerCostSeconds(long userId, String startToken) {
        if (startToken == null || startToken.isBlank()) {
            return null;
        }
        try {
            String stored = redisTemplate.opsForValue()
                    .get(START_KEY_PREFIX + userId + ":" + startToken);
            return stored == null ? null
                    : computeCostSeconds(Long.parseLong(stored), System.currentTimeMillis());
        } catch (Exception e) {
            // Redis 故障不阻断作答提交（降级为无计时），原因可从日志追
            log.warn("作答计时读取失败，本次 costSeconds 记 null: {}", e.getMessage());
            return null;
        }
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
                    toBoolean(existing.getIsCorrect()), existing.getCostSeconds());
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

        // 服务端计时（进步证据）：带 startToken 才有值，客户端自报 costSeconds 不落库（防伪造）
        Integer serverCost = resolveServerCostSeconds(userId, request.startToken());

        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setClientToken(request.clientToken());
        record.setQId(request.qId());
        record.setKpId(request.kpId());
        record.setSource(source);
        record.setIsCorrect(isCorrect ? 1 : 0);
        record.setMyAnswer(request.myAnswer());
        record.setErrorType(errorType);
        record.setCostSeconds(serverCost);
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
                        toBoolean(winner.getIsCorrect()), winner.getCostSeconds());
            }
            throw e;
        }
        // 提交成功后清掉打点（防同令牌二次计时污染；失败不影响主流程，TTL 24h 兜底）
        if (request.startToken() != null && !request.startToken().isBlank()) {
            try {
                redisTemplate.delete(START_KEY_PREFIX + userId + ":" + request.startToken());
            } catch (Exception e) {
                log.warn("作答打点清理失败（TTL 兜底）: {}", e.getMessage());
            }
        }

        PracticeRecord saved = practiceRecordMapper.selectById(record.getPracticeId());
        return new PracticeResultView(saved == null ? record.getPracticeId() : saved.getPracticeId(),
                false, grading.mode(), isCorrect, serverCost);
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
        // 快路径：主观题/未作答/题库答案缺失等分支不依赖历史信号，跳过 DB 查询
        var quick = ErrorTypeClassifier.quickVerdict(question, myAnswer, selfReported);
        if (quick.isPresent()) {
            return quick.get();
        }
        int priorAttempts = practiceRecordMapper.countByUserAndQid(userId, question.getQId());
        // 距上次练习天数只影响多选「部分选中」的 CONCEPT/STEP 区分；
        // 其余题型查了也用不上，省一次热路径往返
        Integer days = null;
        String type = question.getQuestionType() == null ? "" : question.getQuestionType().trim();
        if ("多选".equals(type) && kpId != null && !kpId.isBlank()) {
            LocalDateTime lastPracticedAt = practiceRecordMapper.selectLastPracticedAt(userId, kpId);
            days = lastPracticedAt == null ? null
                    : (int) Math.max(0, Duration.between(lastPracticedAt, LocalDateTime.now(ZONE))
                            .toDays());
        }
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
     * 按真实作答写入 is_correct（不固定 1）。
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

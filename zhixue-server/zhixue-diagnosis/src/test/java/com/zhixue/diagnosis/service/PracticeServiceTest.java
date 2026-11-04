package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private RecommendationRecordMapper recommendationRecordMapper;
    @Mock
    private KnowledgePointService knowledgePointService;
    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Mock
    private org.springframework.data.redis.core.ValueOperations<String, String> valueOps;

    private PracticeService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entity : List.of(PracticeRecord.class, RecommendationRecord.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new PracticeService(practiceRecordMapper, recommendationRecordMapper,
                knowledgePointService, questionBankService, transactionTemplate, redisTemplate);
        // 事务模板直通：立即执行回调（真实事务语义由 IT 在 Testcontainers 上验证）
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<?> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void duplicateClientTokenReturnsFirstResult() {
        PracticeRecord existing = new PracticeRecord();
        existing.setPracticeId(7L);
        existing.setQId("Q_0201");
        existing.setIsCorrect(1);
        existing.setCostSeconds(87);
        when(practiceRecordMapper.selectOne(any())).thenReturn(existing);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(request("SELF", null, "token-1"));
            assertEquals(7L, result.practiceId());
            assertTrue(result.duplicate());
            assertNull(result.gradingMode());
            assertTrue(result.isCorrect());
            // 幂等回放返回首次的服务端计时——第二次提交不会算成"神速"
            assertEquals(87, result.costSeconds());
            verify(practiceRecordMapper, never()).insert(any(PracticeRecord.class));
        }
    }

    // ---------- 服务端计时（进步证据，防伪造方案）----------
    // 边界期望全部手算写死：跨天按 epoch 差恒正；>30 分钟无效；无打点 null；客户端自报值被忽略。

    /** 纯函数边界：跨天 120 秒 / 阈值上下 / 无打点 / 时钟回拨防御。 */
    @Test
    void computeCostSecondsHandlesBoundaries() {
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
        // 跨天：23:59:00 开始 → 00:01:00 提交 = 120 秒（epoch 差与日历无关，恒为正）
        long crossMidnightStart = java.time.LocalDateTime.of(2026, 11, 2, 23, 59, 0)
                .atZone(zone).toInstant().toEpochMilli();
        long crossMidnightEnd = java.time.LocalDateTime.of(2026, 11, 3, 0, 1, 0)
                .atZone(zone).toInstant().toEpochMilli();
        assertEquals(Integer.valueOf(120),
                PracticeService.computeCostSeconds(crossMidnightStart, crossMidnightEnd));
        // 阈值边界：1800 秒有效、1800 秒 +1ms 无效
        assertEquals(Integer.valueOf(1800),
                PracticeService.computeCostSeconds(0L, 1_800_000L));
        assertNull(PracticeService.computeCostSeconds(0L, 1_800_001L));
        // 无打点 → null（不采用客户端自报值）
        assertNull(PracticeService.computeCostSeconds(null, System.currentTimeMillis()));
        // 时钟回拨防御 → 0，不出负数
        assertEquals(Integer.valueOf(0),
                PracticeService.computeCostSeconds(1_000_000L, 999_000L));
    }

    /** begin 打点幂等：重复打点返回首次开始时刻，不重置计时。 */
    @Test
    void beginIsIdempotentByFirstTimestamp() {
        String firstAt = String.valueOf(System.currentTimeMillis() - 60_000);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenReturn(false);
        when(valueOps.get(anyString())).thenReturn(firstAt);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            var view = service.begin(new com.zhixue.diagnosis.dto.PracticeStartRequest(
                    "Q_0201", "begin-token"));
            assertEquals("begin-token", view.clientToken());
            assertNotNull(view.startedAt());
            assertTrue(view.startedAt().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        }
    }

    /** Redis 不可用时 begin 不能把“开始作答”打成 50000：与提交侧计时读取同口径，降级为服务端当前时刻。 */
    @Test
    void beginDegradesToServerClockWhenRedisUnavailable() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            var view = service.begin(new com.zhixue.diagnosis.dto.PracticeStartRequest(
                    "Q_0201", "begin-token"));
            assertEquals("begin-token", view.clientToken());
            assertNotNull(view.startedAt());
        }
    }

    /** 带 startToken：服务端计时落库并返回；客户端自报 costSeconds=30 被忽略。 */
    @Test
    void submitWithStartTokenUsesServerClock() {
        prepare(question("单选", "B", null));
        // begin 于 120 秒前（动态构造，不依赖真实时钟）
        when(valueOps.get(anyString()))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 120_000));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(
                    requestWithStartToken("token-cs", "begin-token"));
            assertFalse(result.duplicate());
            assertNotNull(result.costSeconds());
            assertTrue(result.costSeconds() >= 119 && result.costSeconds() <= 120,
                    "服务端计时应为 120 秒左右，实际=" + result.costSeconds());
        }
    }

    /** 无 startToken：客户端自报 costSeconds 不落库（防伪造），落 null。 */
    @Test
    void submitWithoutStartTokenStoresNullCost() {
        prepare(question("单选", "B", null));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(requestWith(true, "B", "token-nocs"));
            assertFalse(result.duplicate());
            assertNull(result.costSeconds());
        }
    }

    /** 超过 30 分钟（挂机）→ 无效不计统计：落 null 而非截断（截断值仍是假数据）。 */
    @Test
    void submitBeyondThirtyMinutesIsInvalid() {
        prepare(question("单选", "B", null));
        when(valueOps.get(anyString()))
                .thenReturn(String.valueOf(System.currentTimeMillis() - 31 * 60 * 1000L));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(
                    requestWithStartToken("token-idle", "begin-token"));
            assertNull(result.costSeconds());
        }
    }

    /** startToken 无对应打点（伪造令牌）→ null，不阻断提交。 */
    @Test
    void submitWithUnknownStartTokenFallsBackToNull() {
        prepare(question("单选", "B", null));
        when(valueOps.get(anyString())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(
                    requestWithStartToken("token-fake", "forged-token"));
            assertNull(result.costSeconds());
        }
    }

    /**
     * 事务包装后的并发幂等路径（单测层）：用真实 TransactionTemplate + mock 事务管理器，
     * insert 在事务块内抛唯一键冲突 → 回滚后异常浮出 → catch 回查到并发方首次记录 → duplicate=true。
     * 真实并发行为由 IT concurrentDuplicateSubmitsRecordExactlyOnce 在 Testcontainers 上锁定。
     */
    @Test
    void insertConflictInsideTransactionStillReturnsWinner() {
        PracticeService realTxService = new PracticeService(practiceRecordMapper,
                recommendationRecordMapper, knowledgePointService, questionBankService,
                new TransactionTemplate(org.mockito.Mockito.mock(
                        org.springframework.transaction.PlatformTransactionManager.class)),
                redisTemplate);
        PracticeRecord winner = new PracticeRecord();
        winner.setPracticeId(21L);
        winner.setQId("Q_0201");
        winner.setIsCorrect(0);
        prepare(question("单选", "B", null));
        // 必须在 prepare 之后 stub：prepare 内部会重置 selectOne 为单值 null
        when(practiceRecordMapper.selectOne(any())).thenReturn(null).thenReturn(winner);
        when(practiceRecordMapper.insert(any(PracticeRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_user_client"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = realTxService.submit(requestWith(false, "A", "token-race"));
            assertEquals(21L, result.practiceId());
            assertTrue(result.duplicate());
            assertFalse(result.isCorrect());
        }
    }

    /** 事务块失败（非幂等冲突）时推荐采纳不残留：insert 先于 adoption 更新，整体回滚。 */
    @Test
    void adoptionNotTouchedWhenInsertFails() {
        PracticeService realTxService = new PracticeService(practiceRecordMapper,
                recommendationRecordMapper, knowledgePointService, questionBankService,
                new TransactionTemplate(org.mockito.Mockito.mock(
                        org.springframework.transaction.PlatformTransactionManager.class)),
                redisTemplate);
        when(practiceRecordMapper.selectOne(any())).thenReturn(null);
        when(practiceRecordMapper.insert(any(PracticeRecord.class)))
                .thenThrow(new IllegalStateException("db down"));
        prepare(question("单选", "B", null));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThrows(IllegalStateException.class,
                    () -> realTxService.submit(requestWith(false, "A", "tx-fail")));
        }
        verify(recommendationRecordMapper, never()).update(any(), any());
    }

    @Test
    void objectiveSingleCorrectIgnoresClientFalse() {
        Submission submission = submit(question("单选", "B"), requestWith(false, "B", "token-auto-1"));
        assertEquals("AUTO", submission.result().gradingMode());
        assertTrue(submission.result().isCorrect());
        assertEquals(1, submission.record().getIsCorrect());
    }

    @Test
    void objectiveSingleWrongEvenIfClientSaysCorrect() {
        Submission submission = submit(question("单选", "B"), requestWith(true, "A", "token-auto-2"));
        assertEquals("AUTO", submission.result().gradingMode());
        assertFalse(submission.result().isCorrect());
        assertEquals(0, submission.record().getIsCorrect());
    }

    @Test
    void objectiveMultiplePartialIsWrong() {
        Submission submission = submit(question("多选", "ABC"),
                requestWith(null, "A,C", "token-auto-3"));
        assertFalse(submission.result().isCorrect());
    }

    @Test
    void objectiveMultipleOrderInsensitiveIsCorrect() {
        Submission submission = submit(question("多选", "ABC"),
                requestWith(null, "C, A, B", "token-auto-4"));
        assertTrue(submission.result().isCorrect());
    }

    @Test
    void objectiveEmptyAnswerIsWrong() {
        Submission submission = submit(question("单选", "B"), requestWith(null, "", "token-auto-5"));
        assertFalse(submission.result().isCorrect());
        assertEquals(0, submission.record().getIsCorrect());
    }

    @Test
    void subjectiveKeepsSelfAssessment() {
        Submission submission = submit(question("简答", "参考答案"),
                requestWith(true, "我的答案", "token-self-1"));
        assertEquals("SELF", submission.result().gradingMode());
        assertTrue(submission.result().isCorrect());
    }

    @Test
    void objectiveWrongPersistsRuleInferredErrorType() {
        // 首次作答、难度 3、误选干扰项 → BOUNDARY_ERROR（05 §2.7）
        Submission submission = submit(question("单选", "B", 3),
                requestWith(false, "A", "token-err-1"));
        assertEquals("BOUNDARY_ERROR", submission.record().getErrorType());
    }

    @Test
    void objectiveIgnoresClientReportedErrorType() {
        Submission submission = submit(question("单选", "B", 2),
                requestWith(false, "A", "token-err-2", "CALCULATION_ERROR"));
        assertEquals("CONCEPT_CONFUSION", submission.record().getErrorType());
    }

    @Test
    void multiChoicePartialPersistsStepOmission() {
        Submission submission = submit(question("多选", "ABC", 2),
                requestWith(false, "AC", "token-err-3"));
        assertEquals("STEP_OMISSION", submission.record().getErrorType());
    }

    @Test
    void subjectiveWrongDefaultsToUnclassified() {
        Submission submission = submit(question("简答", "参考答案", 2),
                requestWith(false, "我的答案", "token-err-4"));
        assertEquals("UNCLASSIFIED", submission.record().getErrorType());
    }

    @Test
    void subjectiveWrongKeepsSelectedErrorType() {
        Submission submission = submit(question("简答", "参考答案", 2),
                requestWith(false, "我的答案", "token-err-5", "CONCEPT_CONFUSION"));
        assertEquals("CONCEPT_CONFUSION", submission.record().getErrorType());
    }

    @Test
    void subjectiveWithoutSelfAssessmentIsRejected() {
        prepare(question("简答", "参考答案"));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(requestWith(null, "我的答案", "token-self-2")));
            assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        }
    }

    @Test
    void newPracticeIsInserted() {
        when(practiceRecordMapper.selectOne(any())).thenReturn(null);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setCourseId(1L);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setCourseId(1L);
        when(questionBankService.getById("Q_0201")).thenReturn(question);
        doAnswer(invocation -> {
            PracticeRecord row = invocation.getArgument(0);
            row.setPracticeId(9L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        when(practiceRecordMapper.selectById(9L)).thenAnswer(invocation -> {
            PracticeRecord row = new PracticeRecord();
            row.setPracticeId(9L);
            return row;
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(request("SELF", null, "token-2"));
            assertEquals(9L, result.practiceId());
            assertEquals(false, result.duplicate());
        }
    }

    @Test
    void recommendSourceWritesAdoption() {
        when(practiceRecordMapper.selectOne(any())).thenReturn(null);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setCourseId(1L);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setCourseId(1L);
        when(questionBankService.getById("Q_0201")).thenReturn(question);
        RecommendationRecord recommendation = new RecommendationRecord();
        recommendation.setRecommendationId(11L);
        recommendation.setUserId(2L);
        recommendation.setQId("Q_0201");
        when(recommendationRecordMapper.selectById(11L)).thenReturn(recommendation);
        doAnswer(invocation -> {
            PracticeRecord row = invocation.getArgument(0);
            row.setPracticeId(12L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        when(practiceRecordMapper.selectById(12L)).thenAnswer(invocation -> {
            PracticeRecord row = new PracticeRecord();
            row.setPracticeId(12L);
            return row;
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(request(null, 11L, "token-3"));
            assertEquals(12L, result.practiceId());
            verify(recommendationRecordMapper).update(isNull(), any());
        }
    }

    @Test
    void planSourceIsRejected() {
        when(practiceRecordMapper.selectOne(any())).thenReturn(null);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setCourseId(1L);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setCourseId(1L);
        when(questionBankService.getById("Q_0201")).thenReturn(question);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = assertThrows(BizException.class,
                    () -> service.submit(request("PLAN", null, "token-4")));
            assertEquals(ErrorCode.PARAM_ERROR, ex.getErrorCode());
        }
    }

    private PracticeCreateRequest request(String source, Long recommendationId, String token) {
        return new PracticeCreateRequest("Q_0201", "KP0201", true, null, null,
                30, null, source, recommendationId, token);
    }

    private PracticeCreateRequest requestWith(Boolean isCorrect, String myAnswer, String token) {
        return requestWith(isCorrect, myAnswer, token, null);
    }

    /** 带 begin 打点令牌的请求：客户端自报 costSeconds=30（应被服务端忽略）。 */
    private PracticeCreateRequest requestWithStartToken(String token, String startToken) {
        return new PracticeCreateRequest("Q_0201", "KP0201", true, null, null,
                30, startToken, "SELF", null, token);
    }

    private PracticeCreateRequest requestWith(Boolean isCorrect, String myAnswer, String token,
                                              String errorType) {
        return new PracticeCreateRequest("Q_0201", "KP0201", isCorrect, myAnswer, errorType,
                30, null, "SELF", null, token);
    }

    private QuestionBank question(String type, String answer) {
        return question(type, answer, null);
    }

    private QuestionBank question(String type, String answer, Integer difficulty) {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setCourseId(1L);
        question.setQuestionType(type);
        question.setAnswerText(answer);
        question.setDifficulty(difficulty);
        return question;
    }

    private void prepare(QuestionBank question) {
        when(practiceRecordMapper.selectOne(any())).thenReturn(null);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setCourseId(1L);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp);
        when(questionBankService.getById("Q_0201")).thenReturn(question);
    }

    private Submission submit(QuestionBank question, PracticeCreateRequest request) {
        prepare(question);
        doAnswer(invocation -> {
            PracticeRecord row = invocation.getArgument(0);
            row.setPracticeId(20L);
            return 1;
        }).when(practiceRecordMapper).insert(any(PracticeRecord.class));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(request);
            ArgumentCaptor<PracticeRecord> captor = ArgumentCaptor.forClass(PracticeRecord.class);
            verify(practiceRecordMapper).insert(captor.capture());
            return new Submission(result, captor.getValue());
        }
    }

    private record Submission(PracticeResultView result, PracticeRecord record) {
    }
}

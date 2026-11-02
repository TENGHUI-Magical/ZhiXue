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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
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
                knowledgePointService, questionBankService);
    }

    @Test
    void duplicateClientTokenReturnsFirstResult() {
        PracticeRecord existing = new PracticeRecord();
        existing.setPracticeId(7L);
        existing.setQId("Q_0201");
        existing.setIsCorrect(1);
        when(practiceRecordMapper.selectOne(any())).thenReturn(existing);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            PracticeResultView result = service.submit(request("SELF", null, "token-1"));
            assertEquals(7L, result.practiceId());
            assertTrue(result.duplicate());
            assertNull(result.gradingMode());
            assertTrue(result.isCorrect());
            verify(practiceRecordMapper, never()).insert(any(PracticeRecord.class));
        }
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
                30, source, recommendationId, token);
    }

    private PracticeCreateRequest requestWith(Boolean isCorrect, String myAnswer, String token) {
        return requestWith(isCorrect, myAnswer, token, null);
    }

    private PracticeCreateRequest requestWith(Boolean isCorrect, String myAnswer, String token,
                                              String errorType) {
        return new PracticeCreateRequest("Q_0201", "KP0201", isCorrect, myAnswer, errorType,
                30, "SELF", null, token);
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

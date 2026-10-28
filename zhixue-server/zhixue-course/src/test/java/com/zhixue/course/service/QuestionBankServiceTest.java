package com.zhixue.course.service;

import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionAnswerView;
import com.zhixue.course.dto.QuestionDetailView;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.mapper.QuestionBankMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionBankMapper questionBankMapper;
    @Mock
    private KnowledgePointService knowledgePointService;

    private QuestionBankService service;

    @BeforeEach
    void setUp() {
        service = new QuestionBankService(questionBankMapper, new ObjectMapper(), knowledgePointService);
    }

    @Test
    void detailDoesNotExposeAnswerFields() {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setCourseId(1L);
        question.setPrimaryKpId("KP0201");
        question.setQuestionType("单选");
        question.setQuestionText("题干");
        question.setOptionsJson("[{\"key\":\"A\",\"text\":\"选项A\"}]");
        question.setAnswerText("A");
        question.setAnalysisText("解析");
        question.setDifficulty(1);
        when(questionBankMapper.selectById("Q_0201")).thenReturn(question);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        when(knowledgePointService.findById("KP0201")).thenReturn(kp);

        QuestionDetailView detail = service.getDetail("Q_0201");

        assertEquals("Q_0201", detail.qId());
        assertEquals("关系模型", detail.kpName());
        assertEquals(1, detail.options().size());
        assertEquals("A", detail.options().get(0).key());
        // 编译期即保证 QuestionDetailView 没有 answerText/analysisText
    }

    @Test
    void answerIsOnlyReturnedFromAnswerMethod() {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setAnswerText("A");
        question.setAnalysisText("解析");
        when(questionBankMapper.selectById("Q_0201")).thenReturn(question);

        QuestionAnswerView answer = service.getAnswer("Q_0201");

        assertEquals("A", answer.answerText());
        assertEquals("解析", answer.analysisText());
    }

    @Test
    void missingQuestionReturns404() {
        when(questionBankMapper.selectById("Q_9999")).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> service.getDetail("Q_9999"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void dirtyOptionsJsonDegradesToEmpty() {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0202");
        question.setQuestionType("单选");
        question.setQuestionText("题干");
        // 元素不是对象：旧实现会在 toView 强转时抛 ClassCastException
        question.setOptionsJson("[\"A\",\"B\"]");
        when(questionBankMapper.selectById("Q_0202")).thenReturn(question);

        QuestionDetailView detail = service.getDetail("Q_0202");

        assertEquals(0, detail.options().size());
    }
}

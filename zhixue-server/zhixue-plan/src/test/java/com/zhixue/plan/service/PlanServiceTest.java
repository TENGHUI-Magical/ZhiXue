package com.zhixue.plan.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.service.PracticeService;
import com.zhixue.diagnosis.service.RepairPriorityService;
import com.zhixue.plan.dto.CheckinRequest;
import com.zhixue.plan.dto.PlanView;
import com.zhixue.plan.entity.StudyPlan;
import com.zhixue.plan.entity.StudyPlanItem;
import com.zhixue.plan.mapper.StudyPlanItemMapper;
import com.zhixue.plan.mapper.StudyPlanMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private StudyPlanMapper planMapper;
    @Mock
    private StudyPlanItemMapper itemMapper;
    @Mock
    private CourseService courseService;
    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private PracticeService practiceService;
    @Mock
    private RepairPriorityService repairPriorityService;

    private PlanService service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> entity : List.of(StudyPlan.class, StudyPlanItem.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), entity);
        }
    }

    @BeforeEach
    void setUp() {
        service = new PlanService(planMapper, itemMapper, courseService,
                questionBankService, practiceService, repairPriorityService, 7);
    }

    @Test
    void duplicateCheckinReturns40900() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(2L);
        plan.setPlanCount(3);
        plan.setStatus("PENDING");
        when(planMapper.selectById(1L)).thenReturn(plan);

        StudyPlanItem item = new StudyPlanItem();
        item.setItemId(5L);
        item.setPlanId(1L);
        item.setQId("Q_0201");
        item.setKpId("KP0201");
        item.setIsDone(1);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(practiceService.findCompletionByToken(2L, "token-1")).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                    () -> service.checkin(1L, new CheckinRequest("Q_0201", "token-1",
                            true, "A", true, 10, null, null)));
            assertEquals(ErrorCode.DUPLICATE_SUBMIT, ex.getErrorCode());
        }
    }

    @Test
    void otherUsersPlanReturns40300() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(99L);
        when(planMapper.selectById(1L)).thenReturn(plan);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            BizException ex = org.junit.jupiter.api.Assertions.assertThrows(BizException.class,
                    () -> service.checkin(1L, new CheckinRequest("Q_0201", "token-1",
                            true, "A", true, 10, null, null)));
            assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        }
    }

    @Test
    void recordOnlyCheckinDoesNotWritePractice() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(2L);
        plan.setPlanCount(3);
        plan.setDoneCount(0);
        plan.setStatus("PENDING");
        when(planMapper.selectById(1L)).thenReturn(plan);

        StudyPlanItem item = new StudyPlanItem();
        item.setItemId(5L);
        item.setPlanId(1L);
        item.setQId("Q_0201");
        item.setKpId("KP0201");
        item.setIsDone(0);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(itemMapper.update(any(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            com.zhixue.plan.dto.CheckinResult result = service.checkin(1L,
                    new CheckinRequest("Q_0201", "token-only", false, null, null, null, null, null));
            org.junit.jupiter.api.Assertions.assertFalse(result.verified());
            org.junit.jupiter.api.Assertions.assertNull(result.practiceId());
            org.junit.jupiter.api.Assertions.assertNull(result.isCorrect());
        }
        org.mockito.Mockito.verify(practiceService, org.mockito.Mockito.never())
                .recordPlanCompletion(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void objectiveCheckinUsesServerGrading() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(2L);
        plan.setPlanCount(3);
        plan.setDoneCount(0);
        plan.setStatus("PENDING");
        when(planMapper.selectById(1L)).thenReturn(plan);

        StudyPlanItem item = new StudyPlanItem();
        item.setItemId(5L);
        item.setPlanId(1L);
        item.setQId("Q_0201");
        item.setKpId("KP0201");
        item.setIsDone(0);
        when(itemMapper.selectOne(any())).thenReturn(item);

        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionType("单选");
        question.setAnswerText("B");
        when(questionBankService.getById("Q_0201")).thenReturn(question);
        when(practiceService.findCompletionByToken(2L, "token-auto")).thenReturn(null);
        when(practiceService.recordPlanCompletion(anyLong(), any(), any(), any(), any(),
                anyBoolean(), any(), any()))
                .thenReturn(new PracticeService.PlanCompletion(
                        55L, "Q_0201", "PLAN", false, false));
        when(itemMapper.update(any(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            // 客户端误传 isCorrect=true，服务端按 myAnswer=A 与 answerText=B 判为错
            com.zhixue.plan.dto.CheckinResult result = service.checkin(1L,
                    new CheckinRequest("Q_0201", "token-auto", true, "A", true, 10, null, null));
            assertFalse(result.isCorrect());
            assertEquals("AUTO", result.gradingMode());
            // 计划打卡契约不含 startToken，客户端自报 10 秒不可信，服务端必须落 null
            org.mockito.Mockito.verify(practiceService)
                    .recordPlanCompletion(2L, "Q_0201", "KP0201", "token-auto", "A",
                            false, null, null);
        }
    }

    @Test
    void objectiveWrongCheckinPassesInferredErrorType() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(2L);
        plan.setPlanCount(3);
        plan.setDoneCount(0);
        plan.setStatus("PENDING");
        when(planMapper.selectById(1L)).thenReturn(plan);

        StudyPlanItem item = new StudyPlanItem();
        item.setItemId(5L);
        item.setPlanId(1L);
        item.setQId("Q_0201");
        item.setKpId("KP0201");
        item.setIsDone(0);
        when(itemMapper.selectOne(any())).thenReturn(item);

        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionType("单选");
        question.setAnswerText("B");
        when(questionBankService.getById("Q_0201")).thenReturn(question);
        when(practiceService.findCompletionByToken(2L, "token-auto-err")).thenReturn(null);
        // 客户端误报 CALCULATION_ERROR，服务端按规则推断为 CONCEPT_CONFUSION
        when(practiceService.resolveErrorType(anyLong(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn("CONCEPT_CONFUSION");
        when(practiceService.recordPlanCompletion(anyLong(), any(), any(), any(), any(),
                anyBoolean(), any(), any()))
                .thenReturn(new PracticeService.PlanCompletion(
                        56L, "Q_0201", "PLAN", false, false));
        when(itemMapper.update(any(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            service.checkin(1L, new CheckinRequest("Q_0201", "token-auto-err", true,
                    "A", false, 10, "CALCULATION_ERROR", null));
        }

        // 客户端自报 10 秒同样不落库，错因推断不受影响
        org.mockito.Mockito.verify(practiceService).recordPlanCompletion(
                2L, "Q_0201", "KP0201", "token-auto-err", "A", false, null, "CONCEPT_CONFUSION");
    }

    @Test
    void planCheckinIgnoresClientReportedCostSeconds() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(2L);
        plan.setPlanCount(3);
        plan.setDoneCount(0);
        plan.setStatus("PENDING");
        when(planMapper.selectById(1L)).thenReturn(plan);

        StudyPlanItem item = new StudyPlanItem();
        item.setItemId(5L);
        item.setPlanId(1L);
        item.setQId("Q_0201");
        item.setKpId("KP0201");
        item.setIsDone(0);
        when(itemMapper.selectOne(any())).thenReturn(item);

        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setQuestionType("单选");
        question.setAnswerText("A");
        when(questionBankService.getById("Q_0201")).thenReturn(question);
        when(practiceService.findCompletionByToken(2L, "token-cost")).thenReturn(null);
        when(practiceService.recordPlanCompletion(anyLong(), any(), any(), any(), any(),
                anyBoolean(), any(), any()))
                .thenReturn(new PracticeService.PlanCompletion(
                        57L, "Q_0201", "PLAN", false, true));
        when(itemMapper.update(any(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            // 客户端谎报 123 秒：服务端仍按 null 落库，打卡成功与判分不受影响
            com.zhixue.plan.dto.CheckinResult result = service.checkin(1L,
                    new CheckinRequest("Q_0201", "token-cost", true, "A", true, 123, null, null));
            assertEquals(true, result.verified());
            assertEquals(true, result.isCorrect());
            assertEquals(1, result.doneCount());
            assertEquals(57L, result.practiceId());
        }

        org.mockito.Mockito.verify(practiceService).recordPlanCompletion(
                2L, "Q_0201", "KP0201", "token-cost", "A", true, null, null);
    }

    @Test
    void pastPendingPlanIsDerivedOverdue() {
        StudyPlan plan = new StudyPlan();
        plan.setPlanId(1L);
        plan.setUserId(2L);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        plan.setPlanDate(yesterday);
        plan.setPlanCount(3);
        plan.setDoneCount(1);
        plan.setStatus("PENDING");
        when(planMapper.selectList(any())).thenReturn(List.of(plan));
        when(itemMapper.selectList(any())).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            List<PlanView> views = service.list(yesterday, yesterday);
            assertEquals(1, views.size());
            assertEquals("OVERDUE", views.get(0).status());
        }
    }
}

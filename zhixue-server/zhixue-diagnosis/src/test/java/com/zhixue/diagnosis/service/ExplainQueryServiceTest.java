package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ExplainLogView;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 讲解记录查询单测（契约 V2 §5.4）：字段映射、不存在时 40400、教师课程范围 40300。
 */
@ExtendWith(MockitoExtension.class)
class ExplainQueryServiceTest {

    @Mock
    private AiGenerationLogMapper logMapper;

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private CourseService courseService;

    private ExplainQueryService service;

    /** MyBatis-Plus 的 lambda 列名缓存依赖表元数据；单测里手动初始化，便于断言 wrapper 的 SQL 片段。 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), AiGenerationLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new ExplainQueryService(logMapper, knowledgePointService, new ObjectMapper(),
                courseService);
    }

    @Test
    void returnsStoredContentWithResolvedSources() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_1");
        row.setTraceId("t-1");
        row.setModel("deepseek-chat");
        row.setIsDegraded(0);
        row.setContentText("已生成讲解全文");
        row.setSourceIds("[\"KP0201\"]");
        row.setIsBlocked(0);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        when(logMapper.selectOne(any())).thenReturn(row);

        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        kp.setTextbookPage("P34");
        when(knowledgePointService.findById("KP0201")).thenReturn(kp);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.get("EX_1");
        }

        assertThat(view.explainId()).isEqualTo("EX_1");
        assertThat(view.content()).isEqualTo("已生成讲解全文");
        assertThat(view.degraded()).isFalse();
        assertThat(view.blocked()).isFalse();
        assertThat(view.createdAt()).isEqualTo("2026-10-26 12:00:00");
        assertThat(view.sources()).extracting(ExplainLogView.SourceView::title)
                .containsExactly("关系模型");
    }

    @Test
    void missingKpToleratedInSources() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_1");
        row.setContentText("正文");
        row.setSourceIds("[\"KP0201\"]");
        row.setIsDegraded(0);
        row.setIsBlocked(0);
        when(logMapper.selectOne(any())).thenReturn(row);
        when(knowledgePointService.findById("KP0201")).thenReturn(null);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.get("EX_1");
        }

        assertThat(view.sources()).hasSize(1);
        assertThat(view.sources().getFirst().title()).isNull();
        assertThat(view.sources().getFirst().textbookPage()).isNull();
    }

    @Test
    void missingRecordThrowsNotFound() {
        when(logMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.get("EX_missing"))
                    .isInstanceOf(BizException.class);
        }
    }

    /** 评阅回流（05 §4.6）：讲解查询带出该讲解自身的复核状态；未评阅三字段为 null。 */
    @Test
    void carriesOwnReviewFields() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_2");
        row.setContentText("正文");
        row.setSourceIds("[]");
        row.setIsDegraded(0);
        row.setIsBlocked(0);
        row.setCreatedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        row.setReviewResult("RETURN");
        row.setTeacherComment("请补充连接条件的书写");
        row.setReviewedAt(LocalDateTime.of(2026, 10, 27, 9, 0));
        when(logMapper.selectOne(any())).thenReturn(row);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.get("EX_2");
        }

        assertThat(view.reviewResult()).isEqualTo("RETURN");
        assertThat(view.reviewComment()).isEqualTo("请补充连接条件的书写");
        assertThat(view.reviewedAt()).isEqualTo("2026-10-27 09:00:00");
    }

    /**
     * 本课程教师可读本课程讲解（读侧课程范围放行的正例）。
     * 旧版此处断言 {@code StpUtil.getLoginIdAsLong()} 从未被调用，等于把"不校验教师课程范围"
     * 锁成期望；现改为按"登录教师 → 可管理课程"口径校验。
     */
    @Test
    void teacherInSameCourseCanReadReviewCommentForBackfill() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_teacher");
        row.setLogId("EL_teacher");
        row.setContentText("正文");
        row.setSourceIds("[]");
        row.setReviewResult("RETURN");
        row.setTeacherComment("请补充连接条件书写");
        when(logMapper.selectOne(any())).thenReturn(row);
        when(logMapper.selectLinkedCourseIds("EL_teacher")).thenReturn(List.of(1L));
        when(courseService.listActiveCourseIdsByTeacher(1L)).thenReturn(List.of(1L));

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(true);
            view = service.get("EX_teacher");
        }

        assertThat(view.reviewComment()).isEqualTo("请补充连接条件书写");
    }

    /** 读侧课程范围：外课程教师读讲解 → 40300（删掉范围校验时本用例必红）。 */
    @Test
    void teacherOutsideCourseScopeGetsForbidden() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_other_course");
        row.setLogId("EL_other_course");
        row.setContentText("其他课程的学生作答全文");
        row.setSourceIds("[]");
        when(logMapper.selectOne(any())).thenReturn(row);
        when(logMapper.selectLinkedCourseIds("EL_other_course")).thenReturn(List.of(1L));
        when(courseService.listActiveCourseIdsByTeacher(500L)).thenReturn(List.of(2L));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(500L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(true);
            assertThatThrownBy(() -> service.get("EX_other_course"))
                    .isInstanceOfSatisfying(BizException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                        assertThat(ex.getMessage()).doesNotContain("其他课程的学生作答全文");
                    });
        }
    }

    /** 读侧课程范围：教师无任何可管理课程时，可定位课程的记录一律 40300（删校验时同样必红）。 */
    @Test
    void teacherWithoutManagedCourseGetsForbidden() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_course_linked");
        row.setLogId("EL_course_linked");
        row.setSourceIds("[]");
        when(logMapper.selectOne(any())).thenReturn(row);
        when(logMapper.selectLinkedCourseIds("EL_course_linked")).thenReturn(List.of(1L));
        when(courseService.listActiveCourseIdsByTeacher(600L)).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(600L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(true);
            assertThatThrownBy(() -> service.get("EX_course_linked"))
                    .isInstanceOfSatisfying(BizException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }

    /**
     * P1-6：归属不明（关联不到课程）不归属任何教师课程，非创建者教师一律 40300。
     * 旧版此处断言“归属不明放行”，那是单课程阶段无害、扩多课程即越权读学生正文的漏洞，已收紧。
     */
    @Test
    void teacherCannotReadRecordWithUnknownCourseAttribution() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_unknown");
        row.setLogId("EL_unknown");
        row.setUserId(7L);
        row.setContentText("其他学生的正文");
        row.setSourceIds("[]");
        when(logMapper.selectOne(any())).thenReturn(row);
        when(logMapper.selectLinkedCourseIds("EL_unknown")).thenReturn(List.of());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(500L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(true);
            assertThatThrownBy(() -> service.get("EX_unknown"))
                    .isInstanceOfSatisfying(BizException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                        assertThat(ex.getMessage()).doesNotContain("其他学生的正文");
                    });
        }
    }

    /** P1-6 反向：创建者本人读自己归属不明的讲解 → 200，不许误伤。 */
    @Test
    void creatorCanReadOwnUnattributedRecord() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_own_unknown");
        row.setLogId("EL_own_unknown");
        row.setUserId(500L);
        row.setContentText("本人正文");
        row.setSourceIds("[]");
        when(logMapper.selectOne(any())).thenReturn(row);
        when(logMapper.selectLinkedCourseIds("EL_own_unknown")).thenReturn(List.of());

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(500L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(true);
            view = service.get("EX_own_unknown");
        }

        assertThat(view.content()).isEqualTo("本人正文");
    }

    /** 学生本人读自己的讲解 → 200；并断言查询条件确实带 user_id = 登录人（删掉过滤即红）。 */
    @Test
    void studentCanReadOwnExplain() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_student_own");
        row.setUserId(9002L);
        row.setContentText("自己的正文");
        row.setSourceIds("[]");
        when(logMapper.selectOne(any())).thenReturn(row);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9002L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(false);
            view = service.get("EX_student_own");
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<AiGenerationLog>> wrapper =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(logMapper).selectOne(wrapper.capture());
        assertThat(wrapper.getValue().getSqlSegment())
                .contains("explain_id")
                .contains("user_id");
        assertThat(wrapper.getValue().getParamNameValuePairs().values()).contains(9002L);
        assertThat(view.content()).isEqualTo("自己的正文");
    }

    /** P1-6：记录不存在仍是 40400，不能被“无权限”合并成 40300。 */
    @Test
    void missingRecordKeeps40400NotForbidden() {
        when(logMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(500L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(true);
            assertThatThrownBy(() -> service.get("EX_missing"))
                    .isInstanceOfSatisfying(BizException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }
    }

    /** 非教师（学生）语义不变：仍只按本人 user_id 查，查不到一律 40400。 */
    @Test
    void studentKeepsOwnRecordOnlySemantics() {
        when(logMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9002L);
            stp.when(() -> StpUtil.hasRole("TEACHER")).thenReturn(false);
            assertThatThrownBy(() -> service.get("EX_someone_else"))
                    .isInstanceOfSatisfying(BizException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
            stp.verify(() -> StpUtil.hasRole("TEACHER"));
        }
    }

    @Test
    void unreviewedRowCarriesNullReviewFields() {
        AiGenerationLog row = new AiGenerationLog();
        row.setExplainId("EX_3");
        row.setContentText("正文");
        row.setSourceIds("[]");
        row.setIsDegraded(0);
        row.setIsBlocked(0);
        when(logMapper.selectOne(any())).thenReturn(row);

        ExplainLogView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.get("EX_3");
        }

        assertThat(view.reviewResult()).isNull();
        assertThat(view.reviewComment()).isNull();
        assertThat(view.reviewedAt()).isNull();
    }
}

package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.dto.ErrorRecordPageView;
import com.zhixue.diagnosis.dto.ErrorRecordView;
import com.zhixue.diagnosis.entity.AiGenerationLog;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.AiGenerationLogMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 错题记录单测：clientToken 幂等、唯一键竞态回查、确认 CAS、视图补齐 kpName。
 */
@ExtendWith(MockitoExtension.class)
class StudentErrorRecordServiceTest {

    @Mock
    private StudentErrorRecordMapper mapper;

    @Mock
    private AiGenerationLogMapper aiLogMapper;

    @Mock
    private KnowledgePointService knowledgePointService;

    @Mock
    private QuestionBankService questionBankService;

    private StudentErrorRecordService service;

    /** 纯单测无 SqlSessionFactory，需手动初始化实体 TableInfo 供 LambdaUpdateWrapper 解析列名 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                StudentErrorRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new StudentErrorRecordService(mapper, aiLogMapper, knowledgePointService,
                questionBankService);
    }

    @Test
    void createPersistsQIdForPracticeEntry() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(StudentErrorRecord.class))).thenReturn(1);
        when(questionBankService.getById("Q_0201")).thenReturn(question(1L));

        ArgumentCaptor<StudentErrorRecord> captor = ArgumentCaptor.forClass(StudentErrorRecord.class);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            service.create(new ErrorRecordCreate(1L, "ct-q", "Q_0201", null, null, null,
                    null, null, null, null));
        }

        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getQId()).isEqualTo("Q_0201");
    }

    @Test
    void createRejectsQidFromAnotherCourse() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(questionBankService.getById("Q_0201")).thenReturn(question(2L));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.create(new ErrorRecordCreate(1L, "ct-q3", "Q_0201",
                    null, null, null, null, null, null, null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("不属于当前课程");
        }

        verify(mapper, never()).insert(any(StudentErrorRecord.class));
    }

    @Test
    void createRejectsNullQidBeforeInsert() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(questionBankService.getById("Q_9999")).thenReturn(null);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.create(new ErrorRecordCreate(1L, "ct-q4", "Q_9999",
                    null, null, null, null, null, null, null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("题目不存在");
        }

        verify(mapper, never()).insert(any(StudentErrorRecord.class));
    }

    @Test
    void createRejectsInactiveQidBeforeInsert() {
        when(mapper.selectOne(any())).thenReturn(null);
        QuestionBank inactive = question(1L);
        inactive.setIsActive(0);
        when(questionBankService.getById("Q_0201")).thenReturn(inactive);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.create(new ErrorRecordCreate(1L, "ct-q5", "Q_0201",
                    null, null, null, null, null, null, null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("题目已停用");
        }

        verify(mapper, never()).insert(any(StudentErrorRecord.class));
    }

    @Test
    void createRejectsUnknownQidBeforeInsert() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(questionBankService.getById("Q_9999"))
                .thenThrow(new BizException(com.zhixue.common.api.ErrorCode.NOT_FOUND, "题目不存在"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.create(new ErrorRecordCreate(1L, "ct-q2", "Q_9999",
                    null, null, null, null, null, null, null)))
                    .isInstanceOf(BizException.class);
        }

        verify(mapper, never()).insert(any(StudentErrorRecord.class));
    }

    @Test
    void createReturnsFirstRecordForSameClientToken() {
        StudentErrorRecord existing = record("EL_1", "DRAFT");
        when(mapper.selectOne(any())).thenReturn(existing);

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.create(createRequest());
        }

        assertThat(view.logId()).isEqualTo("EL_1");
        assertThat(view.status()).isEqualTo("DRAFT");
        verify(mapper, never()).insert(any(StudentErrorRecord.class));
    }

    @Test
    void createRecoversFromDuplicateKeyRace() {
        StudentErrorRecord winner = record("EL_2", "DRAFT");
        when(mapper.selectOne(any())).thenReturn(null, winner);
        when(mapper.insert(any(StudentErrorRecord.class))).thenThrow(new DuplicateKeyException("uk_user_client"));

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.create(createRequest());
        }

        assertThat(view.logId()).isEqualTo("EL_2");
    }

    @Test
    void confirmUsesCasAndReturnsConfirmedView() {
        StudentErrorRecord draft = record("EL_1", "DRAFT");
        draft.setKpId("KP0201");
        StudentErrorRecord confirmed = record("EL_1", "CONFIRMED");
        confirmed.setKpId("KP0201");
        confirmed.setConfirmedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        when(mapper.selectById("EL_1")).thenReturn(draft, confirmed);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp());
        when(knowledgePointService.findById("KP0201")).thenReturn(kp());

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.confirm("EL_1", confirmRequest());
        }

        assertThat(view.status()).isEqualTo("CONFIRMED");
        assertThat(view.kpName()).isEqualTo("关系模型");
        assertThat(view.confirmedAt()).isEqualTo("2026-10-26 12:00:00");
        verify(mapper).update(isNull(), any());
    }

    /**
     * 确认时间按上海时区写入：把 JVM 默认时区拨到 UTC 后，确认更新里的 confirmed_at
     * 仍应与上海当前时间同窗。把 {@code now(ZONE)} 回退成裸 {@code now()} 即红（差 8 小时）。
     */
    @Test
    @SuppressWarnings("unchecked")
    void confirmedAtUsesShanghaiZoneRegardlessOfJvmDefault() {
        StudentErrorRecord draft = record("EL_1", "DRAFT");
        draft.setKpId("KP0201");
        StudentErrorRecord confirmed = record("EL_1", "CONFIRMED");
        confirmed.setKpId("KP0201");
        when(mapper.selectById("EL_1")).thenReturn(draft, confirmed);
        when(mapper.update(isNull(), any())).thenReturn(1);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp());
        when(knowledgePointService.findById("KP0201")).thenReturn(kp());

        LocalDateTime shanghaiNow;
        LocalDateTime written;
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            shanghaiNow = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
                service.confirm("EL_1", confirmRequest());
            }

            ArgumentCaptor<Wrapper<StudentErrorRecord>> captor =
                    ArgumentCaptor.forClass(Wrapper.class);
            verify(mapper).update(isNull(), captor.capture());
            AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) captor.getValue();
            wrapper.getSqlSegment();
            written = wrapper.getParamNameValuePairs().values().stream()
                    .filter(LocalDateTime.class::isInstance)
                    .map(LocalDateTime.class::cast)
                    .findFirst().orElseThrow();
        } finally {
            TimeZone.setDefault(original);
        }

        assertThat(Duration.between(shanghaiNow, written).abs().getSeconds()).isLessThan(5);
    }

    @Test
    void concurrentConfirmReturnsConflict() {
        StudentErrorRecord draft = record("EL_1", "DRAFT");
        draft.setKpId("KP0201");
        StudentErrorRecord confirmed = record("EL_1", "CONFIRMED");
        confirmed.setKpId("KP0201");
        when(mapper.selectById("EL_1")).thenReturn(draft, confirmed);
        when(mapper.update(isNull(), any())).thenReturn(0);
        when(knowledgePointService.getById("KP0201")).thenReturn(kp());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.confirm("EL_1", confirmRequest()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("已确认");
        }
    }

    @Test
    void createRejectsUnknownKpBeforeInsert() {
        ErrorRecordCreate request = new ErrorRecordCreate(1L, "ct-1", null, "KP9999",
                null, null, null, null, null, null);
        when(knowledgePointService.getById("KP9999"))
                .thenThrow(new BizException(com.zhixue.common.api.ErrorCode.NOT_FOUND, "知识点不存在"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(BizException.class);
        }

        verify(mapper, never()).insert(any(StudentErrorRecord.class));
    }

    @Test
    void createRetriesWithNewLogIdAfterPrimaryKeyCollision() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any(StudentErrorRecord.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry for PRIMARY"))
                .thenReturn(1);

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.create(createRequest());
        }

        assertThat(view).isNotNull();
        verify(mapper, times(2)).insert(any(StudentErrorRecord.class));
    }

    @Test
    void detailToleratesMissingKp() {
        StudentErrorRecord record = record("EL_1", "DRAFT");
        record.setKpId("KP0201");
        when(mapper.selectById("EL_1")).thenReturn(record);
        when(knowledgePointService.findById("KP0201")).thenReturn(null);

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.detail("EL_1");
        }

        assertThat(view.kpName()).isNull();
    }

    @Test
    void detailMapsKpName() {
        StudentErrorRecord record = record("EL_1", "DRAFT");
        record.setKpId("KP0201");
        record.setCreatedAt(LocalDateTime.of(2026, 10, 26, 11, 0));
        when(mapper.selectById("EL_1")).thenReturn(record);
        when(knowledgePointService.findById("KP0201")).thenReturn(kp());

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.detail("EL_1");
        }

        assertThat(view.kpName()).isEqualTo("关系模型");
        assertThat(view.createdAt()).isEqualTo("2026-10-26 11:00:00");
    }

    @Test
    void pageReturnsFilteredRecordsWithBatchKpNames() {
        StudentErrorRecord confirmed = record("EL_2", "CONFIRMED");
        confirmed.setKpId("KP0201");
        confirmed.setCreatedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        StudentErrorRecord draft = record("EL_1", "DRAFT");
        draft.setKpId("KP0202");
        draft.setCreatedAt(LocalDateTime.of(2026, 10, 26, 11, 0));
        when(mapper.selectCount(any())).thenReturn(2L);
        when(mapper.selectPageByWrapper(any(), anyInt(), anyLong())).thenReturn(List.of(confirmed, draft));
        when(knowledgePointService.findByIds(any())).thenReturn(
                Map.of("KP0201", kp("KP0201", "关系模型"),
                        "KP0202", kp("KP0202", "事务")));

        ErrorRecordPageView page;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            page = service.page(1, 10, null, null);
        }

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).extracting(ErrorRecordView::logId)
                .containsExactly("EL_2", "EL_1");
        assertThat(page.records()).extracting(ErrorRecordView::kpName)
                .containsExactly("关系模型", "事务");
        verify(knowledgePointService, times(1)).findByIds(any());
        verify(knowledgePointService, never()).findById(anyString());
        // 分页参数必须绑定到 Mapper 方法，而不是拼进 .last("LIMIT ...")
        verify(mapper).selectPageByWrapper(any(), eq(10), eq(0L));
    }

    @Test
    void pageRejectsInvalidStatus() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            assertThatThrownBy(() -> service.page(1, 10, null, "BAD"))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("status");
        }
        verify(mapper, never()).selectCount(any());
    }

    /**
     * 评阅回流（05 §4.6）：列表批量带出复核状态。
     * EL_2 有两条已评阅讲解（旧 PASS 10-01、新 RETURN 10-26 带评语）→ 聚合取最新 RETURN；
     * EL_1 从未评阅 → 三字段 null。重新生成讲解产生的新未评阅行不参与聚合，
     * 旧结论持续可见（退回意见不清退）。
     */
    @Test
    void pageSurfacesLatestReviewPerRecord() {
        StudentErrorRecord reviewed = record("EL_2", "CONFIRMED");
        reviewed.setKpId("KP0201");
        reviewed.setCreatedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        StudentErrorRecord unreviewed = record("EL_1", "CONFIRMED");
        unreviewed.setKpId("KP0202");
        unreviewed.setCreatedAt(LocalDateTime.of(2026, 10, 26, 11, 0));
        when(mapper.selectCount(any())).thenReturn(2L);
        when(mapper.selectPageByWrapper(any(), anyInt(), anyLong()))
                .thenReturn(List.of(reviewed, unreviewed));
        when(knowledgePointService.findByIds(any())).thenReturn(
                Map.of("KP0201", kp("KP0201", "关系模型"),
                        "KP0202", kp("KP0202", "事务")));
        when(aiLogMapper.selectReviewedByUserAndLogIds(eq(2L), anyList())).thenReturn(List.of(
                reviewRow(101L, "EL_2", "PASS", null, LocalDateTime.of(2026, 10, 1, 9, 0)),
                reviewRow(102L, "EL_2", "RETURN", "请补充连接条件的书写",
                        LocalDateTime.of(2026, 10, 26, 9, 0))));

        ErrorRecordPageView page;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            page = service.page(1, 10, null, null);
        }

        ErrorRecordView first = page.records().get(0);
        assertThat(first.reviewResult()).isEqualTo("RETURN");
        assertThat(first.reviewComment()).isEqualTo("请补充连接条件的书写");
        assertThat(first.reviewedAt()).isEqualTo("2026-10-26 09:00:00");
        ErrorRecordView second = page.records().get(1);
        assertThat(second.reviewResult()).isNull();
        assertThat(second.reviewComment()).isNull();
        assertThat(second.reviewedAt()).isNull();
        // 回流一次批量查询，不逐条 N+1
        verify(aiLogMapper, times(1)).selectReviewedByUserAndLogIds(eq(2L), anyList());
    }

    /** 详情同样带出复核状态；单条出口走同一聚合口径。 */
    @Test
    void detailCarriesLatestReview() {
        StudentErrorRecord record = record("EL_1", "CONFIRMED");
        record.setKpId("KP0201");
        record.setCreatedAt(LocalDateTime.of(2026, 10, 26, 11, 0));
        when(mapper.selectById("EL_1")).thenReturn(record);
        when(knowledgePointService.findById("KP0201")).thenReturn(kp());
        when(aiLogMapper.selectReviewedByUserAndLogIds(eq(2L), anyList())).thenReturn(List.of(
                reviewRow(7L, "EL_1", "PASS", "讲解到位", LocalDateTime.of(2026, 10, 27, 8, 30))));

        ErrorRecordView view;
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(2L);
            view = service.detail("EL_1");
        }

        assertThat(view.reviewResult()).isEqualTo("PASS");
        assertThat(view.reviewComment()).isEqualTo("讲解到位");
        assertThat(view.reviewedAt()).isEqualTo("2026-10-27 08:30:00");
    }

    private AiGenerationLog reviewRow(long logPk, String logId, String result, String comment,
                                      LocalDateTime reviewedAt) {
        AiGenerationLog row = new AiGenerationLog();
        row.setLogPk(logPk);
        row.setLogId(logId);
        row.setUserId(2L);
        row.setScene("RAG");
        row.setReviewResult(result);
        row.setTeacherComment(comment);
        row.setReviewedAt(reviewedAt);
        return row;
    }

    private ErrorRecordCreate createRequest() {
        return new ErrorRecordCreate(1L, "ct-1", null, null, null, null, null,
                null, null, null);
    }

    private ConfirmRequest confirmRequest() {
        ConfirmRequest request = new ConfirmRequest();
        request.setKpId("KP0201");
        request.setErrorType("CONCEPT_CONFUSION");
        return request;
    }

    private StudentErrorRecord record(String logId, String status) {
        StudentErrorRecord record = new StudentErrorRecord();
        record.setLogId(logId);
        record.setUserId(2L);
        record.setClientToken("ct-1");
        record.setStatus(status);
        return record;
    }

    private KnowledgePoint kp() {
        return kp("KP0201", "关系模型");
    }

    private QuestionBank question(long courseId) {
        QuestionBank question = new QuestionBank();
        question.setQId("Q_0201");
        question.setCourseId(courseId);
        question.setIsActive(1);
        return question;
    }

    private KnowledgePoint kp(String kpId, String name) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId(kpId);
        kp.setName(name);
        return kp;
    }

    /**
     * 留痕ID 的日期段按上海时区生成，不随 JVM 默认时区漂移：把默认时区拨到 UTC 后，
     * ID 里的 yyyyMMddHHmmss 仍与上海当前时间同窗（把 {@code now(ZONE)} 改回裸 {@code now()} 即红）。
     */
    @Test
    void logIdStampUsesShanghaiZoneRegardlessOfJvmDefault() throws Exception {
        java.lang.reflect.Method nextLogId =
                StudentErrorRecordService.class.getDeclaredMethod("nextLogId");
        nextLogId.setAccessible(true);

        java.time.LocalDateTime shanghaiNow;
        String logId;
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            shanghaiNow = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
            logId = (String) nextLogId.invoke(service);
        } finally {
            java.util.TimeZone.setDefault(original);
        }

        // EL_{yyMMddHHmmss}_{4位hex}
        String stamp = logId.substring(3, 15);
        java.time.LocalDateTime written = java.time.LocalDateTime.parse(stamp,
                java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        assertThat(java.time.Duration.between(shanghaiNow, written).abs().getSeconds())
                .isLessThan(5);
    }
}

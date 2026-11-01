package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
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
import com.zhixue.diagnosis.entity.StudentErrorRecord;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        service = new StudentErrorRecordService(mapper, knowledgePointService, questionBankService);
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
}

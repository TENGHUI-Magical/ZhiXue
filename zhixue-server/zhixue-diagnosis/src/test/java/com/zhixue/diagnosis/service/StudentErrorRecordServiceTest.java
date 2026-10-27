package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.ConfirmRequest;
import com.zhixue.diagnosis.dto.ErrorRecordCreate;
import com.zhixue.diagnosis.dto.ErrorRecordView;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private StudentErrorRecordService service;

    /** 纯单测无 SqlSessionFactory，需手动初始化实体 TableInfo 供 LambdaUpdateWrapper 解析列名 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                StudentErrorRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new StudentErrorRecordService(mapper, knowledgePointService);
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
        KnowledgePoint kp = new KnowledgePoint();
        kp.setKpId("KP0201");
        kp.setName("关系模型");
        return kp;
    }
}

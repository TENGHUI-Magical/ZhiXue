package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasteryAggregationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    @Mock
    private PracticeRecordMapper practiceRecordMapper;
    @Mock
    private StudentErrorRecordMapper errorRecordMapper;

    private MasteryAggregationService service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                StudentErrorRecord.class);
    }

    @BeforeEach
    void setUp() {
        service = new MasteryAggregationService(practiceRecordMapper, errorRecordMapper, 30, 180);
    }

    /**
     * 口径统一（docs/05 §2.1）：学生端与教师/班级端必须走同一聚合入口。
     * 同一学生、同一份练习数据，两条入口输出的掌握度必须逐位相等。
     * 旧实现里学生端含上传错题、教师端只算 practice_record，同一学生两端数字不同。
     */
    @Test
    void studentAndCourseEntryProduceSameMastery() {
        UserKpAgg practice = kp("KP01", 4, 1, 3.0, 1.0, NOW.minusDays(10));
        when(practiceRecordMapper.aggregateByUserKp(any(), any(), anyDouble(), any(), any()))
                .thenReturn(List.of(practice));
        when(practiceRecordMapper.selectDistinctQidsByUser(2L)).thenReturn(List.of("Q1"));
        when(practiceRecordMapper.selectDistinctQidsByUsers(any())).thenReturn(List.of());
        // CONFIRMED 上传错题（qId 未被练习覆盖）：旧实现只有学生端计入，教师/班级端漏掉 → 两端不一致
        when(errorRecordMapper.selectList(any())).thenReturn(List.of(
                upload("Q2", "CALCULATION_ERROR", NOW.minusDays(5))));

        MasteryAggregationService.StudentMastery student =
                service.studentMastery(2L, NOW, NOW.minusDays(180));
        MasteryAggregationService.CourseMastery course = service.courseMastery(
                List.of("Q1"), List.of("KP01"), List.of(2L), NOW, NOW.minusDays(180));

        assertEquals(5, student.kps().get(0).events(), "练习 4 次 + 上传错题 1 次");
        assertEquals(student.kps().get(0).mastery(), course.byUser().get(2L).get(0).mastery());
        assertEquals(student.kps().get(0).events(), course.byUser().get(2L).get(0).events());
        assertEquals(student.uploadedEvents(), 1);
    }

    /**
     * 衰减语义（docs/05 §2.3）：弱学生停练后掌握度会向先验 0.5 回落（看起来“变好”），
     * 但预计保留率不会升高。两个数并列输出，避免把“停练”误读成“进步”。
     */
    @Test
    void retentionDoesNotRiseWhenWeakStudentStops() {
        // 3 次错误、0 次正确：初始掌握度 Beta(2, 2+3) = 0.286，保留率 0
        UserKpAgg nowAgg = kp("KP01", 3, 3, 0.0, 3.0, null);
        // 60 天后（2 个半衰期）错误权重衰减到 0.75：掌握度回升到 Beta(2,2.75)≈0.421
        UserKpAgg laterAgg = kp("KP01", 3, 3, 0.0, 0.75, null);
        when(practiceRecordMapper.aggregateByUserKp(any(), any(), anyDouble(), any(), any()))
                .thenReturn(List.of(nowAgg), List.of(laterAgg));
        when(practiceRecordMapper.selectDistinctQidsByUser(2L)).thenReturn(List.of());
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());

        MasteryAggregationService.KpMastery atNow =
                service.studentMastery(2L, NOW, NOW.minusDays(180)).kps().get(0);
        MasteryAggregationService.KpMastery later =
                service.studentMastery(2L, NOW.plusDays(60), NOW.minusDays(120)).kps().get(0);

        assertTrue(later.mastery() > atNow.mastery(), "停练后掌握度向先验 0.5 回落");
        assertEquals(0.0, atNow.retention());
        assertEquals(0.0, later.retention(), "从未答对，保留率始终为 0，不应随停练变高");
    }

    /** 保留率有正确证据时按半衰衰减：90 天前答对 → 0.5^3 = 0.125。 */
    @Test
    void retentionDecaysFromLastCorrectEvidence() {
        UserKpAgg agg = kp("KP01", 1, 0, 1.0, 0.0, NOW.minusDays(90));
        when(practiceRecordMapper.aggregateByUserKp(any(), any(), anyDouble(), any(), any()))
                .thenReturn(List.of(agg));
        when(practiceRecordMapper.selectDistinctQidsByUser(2L)).thenReturn(List.of());
        when(errorRecordMapper.selectList(any())).thenReturn(List.of());

        MasteryAggregationService.KpMastery kp =
                service.studentMastery(2L, NOW, NOW.minusDays(180)).kps().get(0);
        assertEquals(0.125, kp.retention(), 1e-9);
    }

    /**
     * 同日重复（docs/05 §2.1）：同一题目同一自然日的重复确认只计 1 次，
     * 不能靠多次上传把错误事件数量刷上去（上传路径同理必须去重）。
     */
    @Test
    void duplicateUploadsSameQuestionSameDayCountOnce() {
        when(practiceRecordMapper.aggregateByUserKp(any(), any(), anyDouble(), any(), any()))
                .thenReturn(List.of());
        when(practiceRecordMapper.selectDistinctQidsByUser(2L)).thenReturn(List.of());
        when(errorRecordMapper.selectList(any())).thenReturn(List.of(
                upload("Q9", "CONCEPT_CONFUSION", NOW.minusDays(1).withHour(9)),
                upload("Q9", "CALCULATION_ERROR", NOW.minusDays(1).withHour(15))));

        MasteryAggregationService.StudentMastery mastery =
                service.studentMastery(2L, NOW, NOW.minusDays(180));

        assertEquals(1, mastery.kps().size());
        assertEquals(1, mastery.kps().get(0).events(), "同日同题只计最优 1 次");
        assertEquals(1, mastery.uploadedEvents());
        assertEquals(1, mastery.uploadedErrorDist().values().stream().mapToInt(Integer::intValue).sum());
    }

    private UserKpAgg kp(String kpId, int events, int errors, double successWeight,
                         double severitySum, LocalDateTime lastCorrectAt) {
        UserKpAgg agg = new UserKpAgg();
        agg.setUserId(2L);
        agg.setKpId(kpId);
        agg.setEvents(events);
        agg.setErrorCount(errors);
        agg.setSuccessWeight(successWeight);
        agg.setSeveritySum(severitySum);
        agg.setLastCorrectAt(lastCorrectAt);
        return agg;
    }

    private StudentErrorRecord upload(String qId, String errorType, LocalDateTime at) {
        StudentErrorRecord record = new StudentErrorRecord();
        record.setLogId(qId + "#" + at);
        record.setUserId(2L);
        record.setQId(qId);
        record.setKpId("KP01");
        record.setStatus("CONFIRMED");
        record.setErrorType(errorType);
        record.setConfirmedAt(at);
        record.setCreatedAt(at);
        return record;
    }
}

package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.CourseView;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.ClassPortraitView;
import com.zhixue.diagnosis.dto.PortraitView;
import com.zhixue.diagnosis.entity.LearningPortraitSnapshot;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.LearningPortraitSnapshotMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 学情画像服务。
 * <p>
 * 有快照读最新快照；无快照时对学生个人画像做一次只读的即时汇总（不落库），
 * 保证前端首访有数据。班级画像为课程维度实时聚合 MVP，口径见实现注释。
 * <p>
 * 掌握度按 docs/05 §2 计算：错因严重度 × 30 天半衰，训练事件 = 练习作答
 * + 无对应练习的 CONFIRMED 错题（按 qId 去重，避免同一次错误计两遍）。
 * 旧实现是 1 - 错误数/作答数，不区分错因、不随时间衰减，已按设计口径替换。
 */
@Service
public class PortraitService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int WEAK_LIMIT = 5;
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private final LearningPortraitSnapshotMapper snapshotMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final StudentErrorRecordMapper errorRecordMapper;
    private final KnowledgePointService knowledgePointService;
    private final CourseService courseService;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;
    private final MasteryCalculator masteryCalculator;
    private final int minSamples;

    public PortraitService(LearningPortraitSnapshotMapper snapshotMapper,
                           PracticeRecordMapper practiceRecordMapper,
                           StudentErrorRecordMapper errorRecordMapper,
                           KnowledgePointService knowledgePointService,
                           CourseService courseService,
                           QuestionBankService questionBankService,
                           ObjectMapper objectMapper,
                           @Value("${mastery.half-life-days:30}") double halfLifeDays,
                           @Value("${mastery.min-samples:3}") int minSamples) {
        this.snapshotMapper = snapshotMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.errorRecordMapper = errorRecordMapper;
        this.knowledgePointService = knowledgePointService;
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.objectMapper = objectMapper;
        this.masteryCalculator = new MasteryCalculator(halfLifeDays);
        this.minSamples = Math.max(1, minSamples);
    }

    /** 当前学生最新画像；无快照且无作答时返回 null（前端空态）。 */
    public PortraitView me() {
        long userId = StpUtil.getLoginIdAsLong();
        LearningPortraitSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<LearningPortraitSnapshot>()
                        .eq(LearningPortraitSnapshot::getUserId, userId)
                        .orderByDesc(LearningPortraitSnapshot::getStatDate)
                        .last("LIMIT 1"));
        if (snapshot != null) {
            return fromSnapshot(snapshot);
        }
        return transientPortrait(userId);
    }

    /**
     * 班级画像（教师）：课程维度实时聚合。
     * 学生集合 = 在该课程题目上有作答记录的学生；活跃率 = 近 7 天有作答 / 学生数。
     * 薄弱点掌握度按“学生先算、再按人平均”（宏平均），避免高频作答的学生
     * 把班级均值拉偏；这是无 course_student 表时的 MVP 口径。
     */
    public ClassPortraitView classPortrait(Long courseId) {
        StpUtil.checkRole("TEACHER");
        Course course = courseService.getActiveById(courseId);
        long teacherId = StpUtil.getLoginIdAsLong();
        if (course.getTeacherId() != null && !Objects.equals(course.getTeacherId(), teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看其他教师的课程");
        }
        List<String> qIds = questionBankService.listQidsByCourse(courseId);
        if (qIds.isEmpty()) {
            return emptyClassPortrait(courseId);
        }
        List<PracticeRecord> records = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .select(PracticeRecord::getUserId, PracticeRecord::getKpId,
                                PracticeRecord::getIsCorrect, PracticeRecord::getErrorType,
                                PracticeRecord::getCreatedAt)
                        .in(PracticeRecord::getQId, qIds));
        if (records.isEmpty()) {
            return emptyClassPortrait(courseId);
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        Set<Long> students = new HashSet<>();
        Set<Long> activeStudents = new HashSet<>();
        LocalDateTime since = now.minusDays(7);
        Map<String, Map<Long, KpStat>> kpByStudent = new HashMap<>();
        Map<String, Integer> errorDist = new HashMap<>();
        for (PracticeRecord record : records) {
            if (record.getUserId() == null) {
                continue;
            }
            students.add(record.getUserId());
            if (record.getCreatedAt() != null && !record.getCreatedAt().isBefore(since)) {
                activeStudents.add(record.getUserId());
            }
            if (isBlank(record.getKpId())) {
                continue;
            }
            boolean wrong = record.getIsCorrect() != null && record.getIsCorrect() == 0;
            KpStat stat = kpByStudent
                    .computeIfAbsent(record.getKpId(), key -> new HashMap<>())
                    .computeIfAbsent(record.getUserId(), key -> new KpStat());
            stat.events++;
            if (wrong) {
                stat.errors++;
                stat.severitySum += masteryCalculator.errorWeight(
                        record.getErrorType(), record.getCreatedAt(), now);
                mergeErrorType(errorDist, record.getErrorType());
            }
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpByStudent.keySet());
        List<KpAgg> aggregates = new ArrayList<>();
        for (Map.Entry<String, Map<Long, KpStat>> entry : kpByStudent.entrySet()) {
            int events = 0;
            int errors = 0;
            double studentsMastery = 0.0;
            int studentCount = 0;
            for (KpStat stat : entry.getValue().values()) {
                events += stat.events;
                errors += stat.errors;
                if (stat.events > 0) {
                    studentsMastery += masteryCalculator.mastery(stat.events, stat.severitySum);
                    studentCount++;
                }
            }
            double macroMastery = studentCount == 0 ? 0.0 : studentsMastery / studentCount;
            aggregates.add(new KpAgg(entry.getKey(), events, errors, macroMastery));
        }

        List<ClassPortraitView.WeakestKpTop> top = aggregates.stream()
                .filter(item -> item.errors() > 0 && item.events() >= minSamples)
                .sorted(Comparator.comparingDouble(KpAgg::mastery)
                        .thenComparing(Comparator.comparingInt(KpAgg::errors).reversed())
                        .thenComparing(KpAgg::kpId))
                .limit(WEAK_LIMIT)
                .map(item -> new ClassPortraitView.WeakestKpTop(
                        item.kpId(), kpName(kpMap, item.kpId()),
                        round3(item.mastery()), item.errors()))
                .toList();

        int studentCount = students.size();
        double activeRate = studentCount == 0 ? 0.0
                : round3(activeStudents.size() / (double) studentCount);
        return new ClassPortraitView(courseId, studentCount, top, errorDist, activeRate);
    }

    /**
     * 即时画像（无快照时）。
     * 训练事件互斥：练习记录一条算一条；CONFIRMED 错题若已有同 qId 的练习记录则跳过，
     * 否则补一条错误事件；DRAFT 错题不参与（查询已按 status 过滤）。
     */
    private PortraitView transientPortrait(long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<PracticeRecord> records = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .orderByAsc(PracticeRecord::getCreatedAt));
        List<StudentErrorRecord> uploadedErrors = errorRecordMapper.selectList(
                new LambdaQueryWrapper<StudentErrorRecord>()
                        .eq(StudentErrorRecord::getUserId, userId)
                        .eq(StudentErrorRecord::getStatus, STATUS_CONFIRMED)
                        .select(StudentErrorRecord::getQId, StudentErrorRecord::getKpId,
                                StudentErrorRecord::getErrorType,
                                StudentErrorRecord::getConfirmedAt,
                                StudentErrorRecord::getCreatedAt));
        if (records.isEmpty() && uploadedErrors.isEmpty()) {
            return null;
        }

        Map<String, KpStat> kpStats = new HashMap<>();
        Map<String, Integer> errorDist = new HashMap<>();
        Set<String> practicedQids = new HashSet<>();
        int totalError = 0;
        int totalEvents = 0;
        for (PracticeRecord record : records) {
            totalEvents++;
            if (!isBlank(record.getQId())) {
                practicedQids.add(record.getQId());
            }
            boolean wrong = record.getIsCorrect() != null && record.getIsCorrect() == 0;
            if (wrong) {
                totalError++;
                mergeErrorType(errorDist, record.getErrorType());
            }
            if (isBlank(record.getKpId())) {
                continue;
            }
            KpStat stat = kpStats.computeIfAbsent(record.getKpId(), key -> new KpStat());
            stat.events++;
            if (wrong) {
                stat.errors++;
                stat.severitySum += masteryCalculator.errorWeight(
                        record.getErrorType(), record.getCreatedAt(), now);
            }
        }
        for (StudentErrorRecord error : uploadedErrors) {
            if (isBlank(error.getKpId())) {
                continue;
            }
            if (!isBlank(error.getQId()) && practicedQids.contains(error.getQId())) {
                continue; // 同一次错误已由练习记录计入，避免重复
            }
            LocalDateTime at = error.getConfirmedAt() != null
                    ? error.getConfirmedAt() : error.getCreatedAt();
            KpStat stat = kpStats.computeIfAbsent(error.getKpId(), key -> new KpStat());
            stat.events++;
            stat.errors++;
            stat.severitySum += masteryCalculator.errorWeight(error.getErrorType(), at, now);
            totalEvents++;
            totalError++;
            mergeErrorType(errorDist, error.getErrorType());
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpStats.keySet());
        Map<String, double[]> dimStats = new LinkedHashMap<>();
        for (Map.Entry<String, KpStat> entry : kpStats.entrySet()) {
            KnowledgePoint kp = kpMap.get(entry.getKey());
            if (kp == null || isBlank(kp.getDimensionKey())) {
                continue;
            }
            KpStat stat = entry.getValue();
            double mastery = masteryCalculator.mastery(stat.events, stat.severitySum);
            double[] dim = dimStats.computeIfAbsent(kp.getDimensionKey(), key -> new double[2]);
            dim[0] += stat.events * mastery; // 按样本量加权（docs/05 §2.4）
            dim[1] += stat.events;
        }

        List<String> dimensionKeys = configuredDimensionKeys();
        if (dimensionKeys.isEmpty()) {
            dimensionKeys = new ArrayList<>(dimStats.keySet());
        }
        Map<String, Double> scores = new LinkedHashMap<>();
        for (String key : dimensionKeys) {
            double[] dim = dimStats.get(key);
            scores.put(key, dim == null || dim[1] == 0 ? null : round3(dim[0] / dim[1]));
        }

        List<PortraitView.WeakestKp> weakest = kpStats.entrySet().stream()
                .filter(entry -> entry.getValue().events >= minSamples && entry.getValue().errors > 0)
                .map(entry -> {
                    KpStat stat = entry.getValue();
                    return new PortraitView.WeakestKp(
                            entry.getKey(),
                            kpName(kpMap, entry.getKey()),
                            round3(masteryCalculator.mastery(stat.events, stat.severitySum)),
                            stat.events);
                })
                .sorted(Comparator.comparingDouble(PortraitView.WeakestKp::mastery)
                        .thenComparing(PortraitView.WeakestKp::kpId))
                .limit(WEAK_LIMIT)
                .toList();

        return new PortraitView(LocalDate.now(ZONE), scores, weakest, errorDist,
                totalEvents, totalError);
    }

    private PortraitView fromSnapshot(LearningPortraitSnapshot snapshot) {
        Map<String, Double> scores = parseNumberMap(snapshot.getDimensionScores());
        Map<String, Integer> errors = parseIntMap(snapshot.getErrorDist());
        List<Map<String, Object>> rawWeakest = parseMapList(snapshot.getWeakestKps());
        List<String> kpIds = rawWeakest.stream()
                .map(item -> asString(item.get("kpId")))
                .filter(id -> id != null && !id.isBlank())
                .toList();
        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpIds);
        List<PortraitView.WeakestKp> weakest = new ArrayList<>();
        for (Map<String, Object> item : rawWeakest) {
            String kpId = asString(item.get("kpId"));
            if (kpId == null || kpId.isBlank()) {
                continue;
            }
            String name = asString(item.get("name"));
            if (name == null || name.isBlank()) {
                name = kpName(kpMap, kpId);
            }
            weakest.add(new PortraitView.WeakestKp(kpId, name,
                    asDouble(item.get("mastery")), asInt(item.get("sampleCount"))));
        }
        return new PortraitView(snapshot.getStatDate(), scores, weakest, errors,
                snapshot.getTotalPractice(), snapshot.getTotalError());
    }

    private List<String> configuredDimensionKeys() {
        List<String> keys = new ArrayList<>();
        for (CourseView course : courseService.listActive()) {
            if (course.dimensionConfig() == null) {
                continue;
            }
            for (CourseView.DimensionDef def : course.dimensionConfig()) {
                if (def.key() != null && !keys.contains(def.key())) {
                    keys.add(def.key());
                }
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> parseNumberMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, Double> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object value = entry.getValue();
                result.put(entry.getKey(), value instanceof Number number
                        ? number.doubleValue() : null);
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseIntMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object value = entry.getValue();
                result.put(entry.getKey(), value instanceof Number number
                        ? number.intValue() : null);
            }
            return result;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMapList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : raw) {
                // 维度配置可能混入字符串，逐项过滤，避免后续强转 50000
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String kpName(Map<String, KnowledgePoint> kpMap, String kpId) {
        KnowledgePoint kp = kpMap.get(kpId);
        return kp == null ? null : kp.getName();
    }

    private void mergeErrorType(Map<String, Integer> errorDist, String errorType) {
        if (!isBlank(errorType)) {
            errorDist.merge(errorType, 1, Integer::sum);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private ClassPortraitView emptyClassPortrait(Long courseId) {
        return new ClassPortraitView(courseId, 0, List.of(), Map.of(), 0.0);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Integer asInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** 单个学生在一个知识点上的训练事件与错误权重。 */
    private static final class KpStat {
        private int events;
        private int errors;
        private double severitySum;
    }

    /** 班级维度聚合结果。 */
    private record KpAgg(String kpId, int events, int errors, double mastery) {
    }
}

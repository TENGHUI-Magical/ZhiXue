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
import com.zhixue.diagnosis.mapper.LearningPortraitSnapshotMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
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
 */
@Service
public class PortraitService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MIN_SAMPLES = 3;
    private static final int WEAK_LIMIT = 5;

    private final LearningPortraitSnapshotMapper snapshotMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final KnowledgePointService knowledgePointService;
    private final CourseService courseService;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    public PortraitService(LearningPortraitSnapshotMapper snapshotMapper,
                           PracticeRecordMapper practiceRecordMapper,
                           KnowledgePointService knowledgePointService,
                           CourseService courseService,
                           QuestionBankService questionBankService,
                           ObjectMapper objectMapper) {
        this.snapshotMapper = snapshotMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.knowledgePointService = knowledgePointService;
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.objectMapper = objectMapper;
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
     * 这是无 course_student 表时的 MVP 口径，后续补选课表与快照 course_id 后可替换。
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

        Set<Long> students = new HashSet<>();
        Set<Long> activeStudents = new HashSet<>();
        LocalDateTime since = LocalDateTime.now(ZONE).minusDays(7);
        Map<String, int[]> kpStats = new HashMap<>();
        Map<String, Integer> errorDist = new HashMap<>();
        for (PracticeRecord record : records) {
            if (record.getUserId() == null) {
                continue;
            }
            students.add(record.getUserId());
            if (record.getCreatedAt() != null && !record.getCreatedAt().isBefore(since)) {
                activeStudents.add(record.getUserId());
            }
            if (record.getKpId() != null && !record.getKpId().isBlank()) {
                int[] stat = kpStats.computeIfAbsent(record.getKpId(), key -> new int[2]);
                stat[0]++;
                if (record.getIsCorrect() != null && record.getIsCorrect() == 0) {
                    stat[1]++;
                    if (record.getErrorType() != null && !record.getErrorType().isBlank()) {
                        errorDist.merge(record.getErrorType(), 1, Integer::sum);
                    }
                }
            }
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpStats.keySet());
        List<ClassPortraitView.WeakestKpTop> top = kpStats.entrySet().stream()
                .filter(entry -> entry.getValue()[1] > 0)
                .map(entry -> new ClassPortraitView.WeakestKpTop(
                        entry.getKey(),
                        kpName(kpMap, entry.getKey()),
                        round3(mastery(entry.getValue()[0], entry.getValue()[1])),
                        entry.getValue()[1]))
                .sorted(Comparator.comparingInt(ClassPortraitView.WeakestKpTop::errorCount)
                        .reversed()
                        .thenComparing(ClassPortraitView.WeakestKpTop::kpId))
                .limit(WEAK_LIMIT)
                .toList();

        int studentCount = students.size();
        double activeRate = studentCount == 0 ? 0.0
                : round3(activeStudents.size() / (double) studentCount);
        return new ClassPortraitView(courseId, studentCount, top, errorDist, activeRate);
    }

    private PortraitView transientPortrait(long userId) {
        List<PracticeRecord> records = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .orderByAsc(PracticeRecord::getCreatedAt));
        if (records.isEmpty()) {
            return null;
        }
        Map<String, int[]> kpStats = new HashMap<>();
        Map<String, Integer> errorDist = new HashMap<>();
        int totalError = 0;
        for (PracticeRecord record : records) {
            boolean wrong = record.getIsCorrect() != null && record.getIsCorrect() == 0;
            if (wrong) {
                totalError++;
                if (record.getErrorType() != null && !record.getErrorType().isBlank()) {
                    errorDist.merge(record.getErrorType(), 1, Integer::sum);
                }
            }
            if (record.getKpId() != null && !record.getKpId().isBlank()) {
                int[] stat = kpStats.computeIfAbsent(record.getKpId(), key -> new int[2]);
                stat[0]++;
                if (wrong) {
                    stat[1]++;
                }
            }
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpStats.keySet());
        Map<String, double[]> dimStats = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : kpStats.entrySet()) {
            KnowledgePoint kp = kpMap.get(entry.getKey());
            if (kp == null || kp.getDimensionKey() == null || kp.getDimensionKey().isBlank()) {
                continue;
            }
            int attempts = entry.getValue()[0];
            int errors = entry.getValue()[1];
            double[] dim = dimStats.computeIfAbsent(kp.getDimensionKey(), key -> new double[2]);
            dim[0] += attempts * mastery(attempts, errors);
            dim[1] += attempts;
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
                .filter(entry -> entry.getValue()[0] >= MIN_SAMPLES && entry.getValue()[1] > 0)
                .map(entry -> new PortraitView.WeakestKp(
                        entry.getKey(),
                        kpName(kpMap, entry.getKey()),
                        round3(mastery(entry.getValue()[0], entry.getValue()[1])),
                        entry.getValue()[0]))
                .sorted(Comparator.comparingDouble(PortraitView.WeakestKp::mastery)
                        .thenComparing(PortraitView.WeakestKp::kpId))
                .limit(WEAK_LIMIT)
                .toList();

        return new PortraitView(LocalDate.now(ZONE), scores, weakest, errorDist,
                records.size(), totalError);
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

    private double mastery(int attempts, int errors) {
        if (attempts <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, 1.0 - errors / (double) attempts));
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
}

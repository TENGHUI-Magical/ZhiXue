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
import com.zhixue.diagnosis.dto.ErrorTypeAgg;
import com.zhixue.diagnosis.dto.PortraitView;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserPracticeAgg;
import com.zhixue.diagnosis.entity.LearningPortraitSnapshot;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.LearningPortraitSnapshotMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(PortraitService.class);
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
    private final double halfLifeDays;
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
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
        this.masteryCalculator = new MasteryCalculator(halfLifeDays);
        this.minSamples = Math.max(1, minSamples);
    }

    /**
     * 当前学生最新画像；优先读最新快照（预留，当前无写入方），无快照时实时聚合；
     * 无作答时返回 null。
     */
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
     * 薄弱点掌握度按"学生先算、再按人平均"（宏平均），避免高频作答的学生
     * 把班级均值拉偏；这是无 course_student 表时的 MVP 口径。
     * <p>
     * 聚合全部下推到 SQL（复用 aggregateByUser / aggregateByUserKp / aggregateErrorTypeByCourse），
     * 进 JVM 的行数从「课程练习明细（万级）」降到「学生数 × 知识点数（百级）」，
     * 与 plan 模块 C12 共用同一套半衰严重度口径，避免两处算法漂移。
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

        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime since = now.minusDays(7);

        int studentCount = 0;
        int activeCount = 0;
        Map<Long, LocalDateTime> lastActiveByUser = new HashMap<>();
        for (UserPracticeAgg agg : practiceRecordMapper.aggregateByUser(qIds, null)) {
            if (agg.getUserId() == null) {
                continue;
            }
            studentCount++;
            lastActiveByUser.put(agg.getUserId(), agg.getLastActiveAt());
            if (agg.getLastActiveAt() != null && !agg.getLastActiveAt().isBefore(since)) {
                activeCount++;
            }
        }
        if (studentCount == 0) {
            return emptyClassPortrait(courseId);
        }

        Map<String, List<UserKpAgg>> kpByStudents = new HashMap<>();
        for (UserKpAgg agg : practiceRecordMapper.aggregateByUserKp(qIds, null, halfLifeDays, now)) {
            if (agg.getUserId() == null || isBlank(agg.getKpId())) {
                continue;
            }
            kpByStudents.computeIfAbsent(agg.getKpId(), key -> new ArrayList<>()).add(agg);
        }

        Map<String, Integer> errorDist = new HashMap<>();
        for (ErrorTypeAgg agg : practiceRecordMapper.aggregateErrorTypeByCourse(qIds)) {
            if (!isBlank(agg.getErrorType()) && agg.getCnt() != null) {
                errorDist.merge(agg.getErrorType(), agg.getCnt(), Integer::sum);
            }
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpByStudents.keySet());
        List<KpAgg> aggregates = new ArrayList<>();
        for (Map.Entry<String, List<UserKpAgg>> entry : kpByStudents.entrySet()) {
            int events = 0;
            int errors = 0;
            double masterySum = 0.0;
            int studentCnt = 0;
            for (UserKpAgg agg : entry.getValue()) {
                int ev = agg.getEvents() == null ? 0 : agg.getEvents();
                int err = agg.getErrorCount() == null ? 0 : agg.getErrorCount();
                errors += err;
                events += ev;
                if (ev > 0) {
                    int correct = Math.max(0, ev - err);
                    masterySum += masteryCalculator.mastery(correct,
                            agg.getSeveritySum() == null ? 0.0 : agg.getSeveritySum(),
                            lastActiveByUser.get(agg.getUserId()), now);
                    studentCnt++;
                }
            }
            double macroMastery = studentCnt == 0 ? 0.0 : masterySum / studentCnt;
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

        double activeRate = round3(activeCount / (double) studentCount);
        return new ClassPortraitView(courseId, studentCount, top, errorDist, activeRate);
    }

    /**
     * 即时画像（无快照时的正常路径，实时聚合练习与 CONFIRMED 错题）。
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
            stat.lastAt = later(stat.lastAt, record.getCreatedAt());
            if (wrong) {
                stat.errors++;
                stat.severitySum += masteryCalculator.errorWeight(
                        record.getErrorType(), record.getCreatedAt(), now);
            } else {
                stat.correct++;
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
            stat.lastAt = later(stat.lastAt, at);
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
            double mastery = masteryCalculator.mastery(stat.correct, stat.severitySum, stat.lastAt, now);
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
                            round3(masteryCalculator.mastery(stat.correct, stat.severitySum,
                                    stat.lastAt, now)),
                            stat.events);
                })
                .sorted(Comparator.comparingDouble(PortraitView.WeakestKp::mastery)
                        .thenComparing(PortraitView.WeakestKp::kpId))
                .limit(WEAK_LIMIT)
                .toList();

        return new PortraitView(LocalDate.now(ZONE), scores, weakest, errorDist,
                totalEvents, totalError);
    }

    /**
     * 从快照还原画像（**预留未启用**）。
     * 当前 learning_portrait_snapshot 没有写入方（无 @Scheduled / 无插入路径），
     * 正常请求走 transientPortrait；启用前不得据此宣称"每日快照"。
     */
    private PortraitView fromSnapshot(LearningPortraitSnapshot snapshot) {
        Map<String, Double> scores = parseNumberMap(snapshot.getDimensionScores(), "dimensionScores");
        Map<String, Integer> errors = parseIntMap(snapshot.getErrorDist(), "errorDist");
        List<Map<String, Object>> rawWeakest = parseMapList(snapshot.getWeakestKps(), "weakestKps");
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
    private Map<String, Double> parseNumberMap(String json, String label) {
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
        } catch (Exception e) {
            log.warn("画像快照字段 {} 解析失败，按空集合降级", label, e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseIntMap(String json, String label) {
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
        } catch (Exception e) {
            log.warn("画像快照字段 {} 解析失败，按空集合降级", label, e);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseMapList(String json, String label) {
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
        } catch (Exception e) {
            log.warn("画像快照字段 {} 解析失败，按空列表降级", label, e);
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
        private int correct;
        private double severitySum;
        private LocalDateTime lastAt;
    }

    private static LocalDateTime later(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    /** 班级维度聚合结果。 */
    private record KpAgg(String kpId, int events, int errors, double mastery) {
    }
}

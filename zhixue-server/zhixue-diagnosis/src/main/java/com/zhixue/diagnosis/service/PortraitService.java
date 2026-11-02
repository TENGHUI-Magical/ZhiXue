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
 * 有快照读最新快照（预留，当前无写入方）；无快照时对学生个人画像做一次只读的即时汇总（不落库），
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
    /** 聚合时间窗（天）：跨学年历史不实时算，等快照写入方接入（me() 已优先读快照） */
    private final int aggregateWindowDays;

    public PortraitService(LearningPortraitSnapshotMapper snapshotMapper,
                           PracticeRecordMapper practiceRecordMapper,
                           StudentErrorRecordMapper errorRecordMapper,
                           KnowledgePointService knowledgePointService,
                           CourseService courseService,
                           QuestionBankService questionBankService,
                           ObjectMapper objectMapper,
                           @Value("${mastery.half-life-days:30}") double halfLifeDays,
                           @Value("${mastery.min-samples:3}") int minSamples,
                           @Value("${mastery.aggregate-window-days:180}") int aggregateWindowDays) {
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
        // 窗口至少盖住 6 个半衰期（默认 180 天 = 30 天半衰 × 6），窗口外证据衰减后权重 <1.6%
        this.aggregateWindowDays = Math.max(1, aggregateWindowDays);
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
        // 聚合窗口：只实时算窗口内记录，跨学年历史走快照（me() 快照优先已就位）
        LocalDateTime aggregateSince = now.minusDays(aggregateWindowDays);

        int studentCount = 0;
        int activeCount = 0;
        Map<Long, LocalDateTime> lastActiveByUser = new HashMap<>();
        for (UserPracticeAgg agg : practiceRecordMapper.aggregateByUser(qIds, null, aggregateSince)) {
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
        for (UserKpAgg agg : practiceRecordMapper.aggregateByUserKp(qIds, null, halfLifeDays, now,
                aggregateSince)) {
            if (agg.getUserId() == null || isBlank(agg.getKpId())) {
                continue;
            }
            kpByStudents.computeIfAbsent(agg.getKpId(), key -> new ArrayList<>()).add(agg);
        }

        Map<String, Integer> errorDist = new HashMap<>();
        for (ErrorTypeAgg agg : practiceRecordMapper.aggregateErrorType(qIds, null, aggregateSince)) {
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
                    // 与教师端同一 SQL 口径：successWeight 按每条正确事件的时间衰减求和
                    masterySum += masteryCalculator.mastery(
                            agg.getSuccessWeight() == null ? 0.0 : agg.getSuccessWeight(),
                            agg.getSeveritySum() == null ? 0.0 : agg.getSeveritySum());
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
     * <p>
     * 聚合全部下推 SQL（窗口内 per-kp 权重 / 错因分布 / 总量），不把练习明细拉进 JVM——
     * 单学生一学期约 1800 行明细，全量加载会在学期末放大为全表扫描+回表。
     * 训练事件互斥：练习记录一条算一条；CONFIRMED 错题若已有同 qId 的练习记录则跳过
     * （练过的题走 DISTINCT 投影查询，同样不拉明细），否则补一条错误事件；DRAFT 不参与。
     * 时间窗：窗口外历史走快照（me() 快照优先已就位），实时路径只算窗口内。
     */
    private PortraitView transientPortrait(long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime aggregateSince = now.minusDays(aggregateWindowDays);

        Map<String, Integer> errorDist = new HashMap<>();
        for (ErrorTypeAgg agg : practiceRecordMapper.aggregateErrorType(null, userId,
                aggregateSince)) {
            if (!isBlank(agg.getErrorType()) && agg.getCnt() != null) {
                errorDist.put(agg.getErrorType(), agg.getCnt());
            }
        }
        int totalEvents = 0;
        int totalError = 0;
        for (UserPracticeAgg agg : practiceRecordMapper.aggregateByUser(null, List.of(userId),
                aggregateSince)) {
            if (agg.getPracticeCount() != null) {
                totalEvents = agg.getPracticeCount();
            }
            if (agg.getWrongCount() != null) {
                totalError = agg.getWrongCount();
            }
        }

        // per-kp 聚合（练习 + 无练习覆盖的 CONFIRMED 错题补事件），与到期复习共用同一口径
        KpAggregatePass pass = aggregateUserKps(userId, now, aggregateSince);
        Map<String, UserKpAgg> kpStats = pass.kpStats();
        for (Map.Entry<String, Integer> entry : pass.uploadedErrorDist().entrySet()) {
            errorDist.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        totalEvents += pass.uploadedEvents();
        totalError += pass.uploadedErrors();
        if (kpStats.isEmpty()) {
            return null;
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpStats.keySet());
        Map<String, double[]> dimStats = new LinkedHashMap<>();
        for (Map.Entry<String, UserKpAgg> entry : kpStats.entrySet()) {
            KnowledgePoint kp = kpMap.get(entry.getKey());
            if (kp == null || isBlank(kp.getDimensionKey())) {
                continue;
            }
            UserKpAgg stat = entry.getValue();
            double mastery = masteryCalculator.mastery(
                    stat.getSuccessWeight() == null ? 0.0 : stat.getSuccessWeight(),
                    stat.getSeveritySum() == null ? 0.0 : stat.getSeveritySum());
            double[] dim = dimStats.computeIfAbsent(kp.getDimensionKey(), key -> new double[2]);
            dim[0] += stat.getEvents() * mastery; // 按样本量加权（docs/05 §2.4）
            dim[1] += stat.getEvents();
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
                .filter(entry -> entry.getValue().getEvents() != null
                        && entry.getValue().getEvents() >= minSamples
                        && entry.getValue().getErrorCount() != null
                        && entry.getValue().getErrorCount() > 0)
                .map(entry -> {
                    UserKpAgg stat = entry.getValue();
                    return new PortraitView.WeakestKp(
                            entry.getKey(),
                            kpName(kpMap, entry.getKey()),
                            round3(masteryCalculator.mastery(
                                    stat.getSuccessWeight() == null ? 0.0 : stat.getSuccessWeight(),
                                    stat.getSeveritySum() == null ? 0.0 : stat.getSeveritySum())),
                            stat.getEvents());
                })
                .sorted(Comparator.comparingDouble(PortraitView.WeakestKp::mastery)
                        .thenComparing(PortraitView.WeakestKp::kpId))
                .limit(WEAK_LIMIT)
                .toList();

        return new PortraitView(LocalDate.now(ZONE), scores, weakest, errorDist,
                totalEvents, totalError);
    }

    /**
     * 单学生 per-kp 聚合（含无练习覆盖的 CONFIRMED 错题补事件），供画像与到期复习共用：
     * 学生在同一页面看到的画像掌握度与到期复习排序里的掌握度必须同源，不能两处口径。
     * 掌握度由调用方用同一 halfLifeDays 构造的 {@link MasteryCalculator#mastery} 计算。
     */
    public Map<String, UserKpAgg> userKpAggregates(long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        return aggregateUserKps(userId, now, now.minusDays(aggregateWindowDays)).kpStats();
    }

    /** 一次聚合 pass：per-kp 权重 + 错题补事件对总量/错因分布的增量，避免提取后重复查询。 */
    private KpAggregatePass aggregateUserKps(long userId, LocalDateTime now,
                                            LocalDateTime aggregateSince) {
        Map<String, UserKpAgg> kpStats = new HashMap<>();
        for (UserKpAgg agg : practiceRecordMapper.aggregateByUserKp(null, List.of(userId),
                halfLifeDays, now, aggregateSince)) {
            if (!isBlank(agg.getKpId())) {
                kpStats.put(agg.getKpId(), agg);
            }
        }

        // CONFIRMED 错题补事件：qId 已有练习记录的跳过（DISTINCT 投影，不拉明细）
        List<StudentErrorRecord> uploadedErrorRecords = errorRecordMapper.selectList(
                new LambdaQueryWrapper<StudentErrorRecord>()
                        .eq(StudentErrorRecord::getUserId, userId)
                        .eq(StudentErrorRecord::getStatus, STATUS_CONFIRMED)
                        .ge(StudentErrorRecord::getCreatedAt, aggregateSince)
                        .select(StudentErrorRecord::getQId, StudentErrorRecord::getKpId,
                                StudentErrorRecord::getErrorType,
                                StudentErrorRecord::getConfirmedAt,
                                StudentErrorRecord::getCreatedAt));
        Set<String> practicedQids = new HashSet<>(
                practiceRecordMapper.selectDistinctQidsByUser(userId));
        int uploadedEvents = 0;
        int uploadedErrors = 0;
        Map<String, Integer> uploadedErrorDist = new HashMap<>();
        for (StudentErrorRecord error : uploadedErrorRecords) {
            if (isBlank(error.getKpId())) {
                continue;
            }
            if (!isBlank(error.getQId()) && practicedQids.contains(error.getQId())) {
                continue; // 同一次错误已由练习记录计入，避免重复
            }
            LocalDateTime at = error.getConfirmedAt() != null
                    ? error.getConfirmedAt() : error.getCreatedAt();
            UserKpAgg stat = kpStats.computeIfAbsent(error.getKpId(), key -> {
                UserKpAgg created = new UserKpAgg();
                created.setUserId(userId);
                created.setKpId(key);
                created.setEvents(0);
                created.setSuccessWeight(0.0);
                created.setSeveritySum(0.0);
                created.setErrorCount(0);
                return created;
            });
            stat.setEvents((stat.getEvents() == null ? 0 : stat.getEvents()) + 1);
            stat.setErrorCount((stat.getErrorCount() == null ? 0 : stat.getErrorCount()) + 1);
            stat.setSeveritySum((stat.getSeveritySum() == null ? 0.0 : stat.getSeveritySum())
                    + masteryCalculator.errorWeight(error.getErrorType(), at, now));
            uploadedEvents++;
            uploadedErrors++;
            if (!isBlank(error.getErrorType())) {
                uploadedErrorDist.merge(error.getErrorType(), 1, Integer::sum);
            }
        }
        return new KpAggregatePass(kpStats, uploadedEvents, uploadedErrors, uploadedErrorDist);
    }

    /** per-kp 聚合 pass 结果：kpStats 供画像/到期复习，uploaded* 为错题补事件的增量。 */
    private record KpAggregatePass(Map<String, UserKpAgg> kpStats, int uploadedEvents,
                                   int uploadedErrors, Map<String, Integer> uploadedErrorDist) {
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

    /** 班级维度聚合结果。 */
    private record KpAgg(String kpId, int events, int errors, double mastery) {
    }
}

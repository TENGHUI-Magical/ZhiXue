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
import com.zhixue.diagnosis.mapper.LearningPortraitSnapshotMapper;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
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

    private final LearningPortraitSnapshotMapper snapshotMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final MasteryAggregationService masteryAggregationService;
    private final KnowledgePointService knowledgePointService;
    private final CourseService courseService;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;
    private final int minSamples;
    /** 聚合时间窗（天）：跨学年历史不实时算，等快照写入方接入（me() 已优先读快照） */
    private final int aggregateWindowDays;

    public PortraitService(LearningPortraitSnapshotMapper snapshotMapper,
                           PracticeRecordMapper practiceRecordMapper,
                           MasteryAggregationService masteryAggregationService,
                           KnowledgePointService knowledgePointService,
                           CourseService courseService,
                           QuestionBankService questionBankService,
                           ObjectMapper objectMapper,
                           @Value("${mastery.min-samples:3}") int minSamples,
                           @Value("${mastery.aggregate-window-days:180}") int aggregateWindowDays) {
        this.snapshotMapper = snapshotMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.masteryAggregationService = masteryAggregationService;
        this.knowledgePointService = knowledgePointService;
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.objectMapper = objectMapper;
        this.minSamples = Math.max(1, minSamples);
        // 窗口至少盖住 6 个半衰期（默认 180 天 = 30 天半衰 × 6），窗口外证据衰减后权重 <1.6%
        this.aggregateWindowDays = Math.max(1, aggregateWindowDays);
    }

    /**
     * 当前学生最新画像；优先读最新快照（P0 修复：新鲜度门限 3 天），
     * 快照过期或不存在时实时聚合；无作答时返回 null。
     */
    public PortraitView me() {
        long userId = StpUtil.getLoginIdAsLong();
        LearningPortraitSnapshot snapshot = snapshotMapper.selectOne(
                new LambdaQueryWrapper<LearningPortraitSnapshot>()
                        .eq(LearningPortraitSnapshot::getUserId, userId)
                        .orderByDesc(LearningPortraitSnapshot::getStatDate)
                        .last("LIMIT 1"));
        // P0 修复：新鲜度门限——快照超过 3 天视为过期，回落实时聚合
        if (snapshot != null && snapshot.getStatDate() != null
                && !snapshot.getStatDate().isBefore(LocalDate.now(ZONE).minusDays(3))) {
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
        // P1-1 fail-closed：teacherId 为空一律拒绝，消除「无归属教师即放行」的危险写法。
        // 残余风险：班级维度仍不是安全边界（无 course_student/teacher_class 绑定表），
        // 本次只收紧课程归属校验，不引入 schema 变更（按 AGENTS.md 需人确认）。
        if (course.getTeacherId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "课程无归属教师，禁止访问");
        }
        if (!Objects.equals(course.getTeacherId(), teacherId)) {
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
        Set<Long> userIds = new HashSet<>();
        for (UserPracticeAgg agg : practiceRecordMapper.aggregateByUser(qIds, null, aggregateSince)) {
            if (agg.getUserId() == null) {
                continue;
            }
            studentCount++;
            userIds.add(agg.getUserId());
            if (agg.getLastActiveAt() != null && !agg.getLastActiveAt().isBefore(since)) {
                activeCount++;
            }
        }
        if (studentCount == 0) {
            return emptyClassPortrait(courseId);
        }

        // 与教师端/学生端同源：practice_record + CONFIRMED 上传错题、同日同题去重，统一由聚合服务计算
        List<KnowledgePoint> courseKps = courseService.listActiveKnowledgePoints(courseId);
        List<String> kpIds = courseKps.stream().map(KnowledgePoint::getKpId)
                .filter(Objects::nonNull).toList();
        MasteryAggregationService.CourseMastery courseMastery = masteryAggregationService
                .courseMastery(qIds, kpIds, userIds, now, aggregateSince);
        Map<String, List<MasteryAggregationService.KpMastery>> kpByStudents = new HashMap<>();
        for (List<MasteryAggregationService.KpMastery> kpsOfUser : courseMastery.byUser().values()) {
            for (MasteryAggregationService.KpMastery kp : kpsOfUser) {
                kpByStudents.computeIfAbsent(kp.kpId(), key -> new ArrayList<>()).add(kp);
            }
        }

        Map<String, Integer> errorDist = new HashMap<>();
        for (ErrorTypeAgg agg : practiceRecordMapper.aggregateErrorType(qIds, null, aggregateSince)) {
            if (!isBlank(agg.getErrorType()) && agg.getCnt() != null) {
                errorDist.merge(agg.getErrorType(), agg.getCnt(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : courseMastery.uploadedErrorDist().entrySet()) {
            errorDist.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpByStudents.keySet());
        List<KpAgg> aggregates = new ArrayList<>();
        for (Map.Entry<String, List<MasteryAggregationService.KpMastery>> entry : kpByStudents.entrySet()) {
            int errors = 0;
            double masterySum = 0.0;
            int studentCnt = 0;
            for (MasteryAggregationService.KpMastery kp : entry.getValue()) {
                // 班级薄弱点分母按单学生样本量 N_k >= minSamples，而不是全班总事件数
                if (kp.events() < minSamples) {
                    continue;
                }
                errors += kp.errorCount();
                masterySum += kp.mastery();
                studentCnt++;
            }
            if (studentCnt == 0) {
                continue;
            }
            aggregates.add(new KpAgg(entry.getKey(), errors, masterySum / studentCnt));
        }

        List<ClassPortraitView.WeakestKpTop> top = aggregates.stream()
                .filter(item -> item.errors() > 0)
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
        LocalDateTime now = masteryAggregationService.now();
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

        // 唯一聚合入口：practice_record + CONFIRMED 上传错题、同日同题去重
        MasteryAggregationService.StudentMastery student =
                masteryAggregationService.studentMastery(userId, now, aggregateSince);
        Map<String, MasteryAggregationService.KpMastery> kpStats = new LinkedHashMap<>();
        for (MasteryAggregationService.KpMastery kp : student.kps()) {
            kpStats.put(kp.kpId(), kp);
        }
        for (Map.Entry<String, Integer> entry : student.uploadedErrorDist().entrySet()) {
            errorDist.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        totalEvents += student.uploadedEvents();
        totalError += student.uploadedErrors();
        if (kpStats.isEmpty()) {
            return null;
        }

        Map<String, KnowledgePoint> kpMap = knowledgePointService.findByIds(kpStats.keySet());
        Map<String, double[]> dimStats = new LinkedHashMap<>();
        for (MasteryAggregationService.KpMastery stat : kpStats.values()) {
            KnowledgePoint kp = kpMap.get(stat.kpId());
            if (kp == null || isBlank(kp.getDimensionKey())) {
                continue;
            }
            double[] dim = dimStats.computeIfAbsent(kp.getDimensionKey(), key -> new double[2]);
            dim[0] += stat.events() * stat.mastery(); // 按样本量加权（docs/05 §2.4）
            dim[1] += stat.events();
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

        List<PortraitView.WeakestKp> weakest = kpStats.values().stream()
                .filter(stat -> stat.events() >= minSamples && stat.errorCount() > 0)
                .map(stat -> new PortraitView.WeakestKp(
                        stat.kpId(), kpName(kpMap, stat.kpId()), round3(stat.mastery()),
                        stat.events()))
                .sorted(Comparator.comparingDouble(PortraitView.WeakestKp::mastery)
                        .thenComparing(PortraitView.WeakestKp::kpId))
                .limit(WEAK_LIMIT)
                .toList();

        return new PortraitView(LocalDate.now(ZONE), scores, weakest, errorDist,
                totalEvents, totalError);
    }

    /**
     * 单学生 per-kp 聚合，供画像与到期复习共用：学生在同一页面看到的画像掌握度与
     * 到期复习排序里的掌握度必须同源，不能两处口径。实际计算全部收敛在
     * {@link MasteryAggregationService}。
     */
    public Map<String, UserKpAgg> userKpAggregates(long userId) {
        Map<String, UserKpAgg> result = new LinkedHashMap<>();
        for (MasteryAggregationService.KpMastery kp
                : masteryAggregationService.studentMastery(userId).kps()) {
            UserKpAgg agg = new UserKpAgg();
            agg.setUserId(userId);
            agg.setKpId(kp.kpId());
            agg.setEvents(kp.events());
            agg.setErrorCount(kp.errorCount());
            agg.setSuccessWeight(kp.successWeight());
            agg.setSeveritySum(kp.severitySum());
            result.put(kp.kpId(), agg);
        }
        return result;
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

    /** 班级维度聚合结果（仅统计单学生样本量达标的学生）。 */
    private record KpAgg(String kpId, int errors, double mastery) {
    }

    // ===== P0 修复：每日快照写入方 =====

    /**
     * 每日凌晨 2:00 为所有有作答记录的学生生成画像快照。
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE（uk_user_date 唯一键）保证幂等。
     * 无分布式锁（单体架构），依赖唯一键兜底并发写入。
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * ?")
    public void writeDailySnapshots() {
        log.info("开始生成每日画像快照...");
        LocalDate today = LocalDate.now(ZONE);
        // 查询所有有作答记录的学生（近 180 天活跃）
        List<Long> userIds = practiceRecordMapper.aggregateByUser(null, null,
                        LocalDateTime.now(ZONE).minusDays(aggregateWindowDays)).stream()
                .map(UserPracticeAgg::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        int written = 0;
        for (Long userId : userIds) {
            try {
                PortraitView portrait = transientPortrait(userId);
                if (portrait == null) {
                    continue;
                }
                saveSnapshot(userId, today, portrait);
                written++;
            } catch (Exception e) {
                log.warn("生成快照失败 userId={}: {}", userId, e.getMessage());
            }
        }
        log.info("每日画像快照完成：{}/{} 条", written, userIds.size());
    }

    /** 保存单个学生的画像快照（幂等：uk_user_date 冲突时更新）。 */
    private void saveSnapshot(long userId, LocalDate statDate, PortraitView portrait) {
        LearningPortraitSnapshot snapshot = new LearningPortraitSnapshot();
        snapshot.setUserId(userId);
        snapshot.setStatDate(statDate);
        snapshot.setDimensionScores(toJson(portrait.dimensionScores()));
        snapshot.setWeakestKps(toJson(portrait.weakestKps().stream()
                .map(kp -> Map.of("kpId", kp.kpId(), "name", kp.name(),
                        "mastery", kp.mastery(), "sampleCount", kp.sampleCount()))
                .toList()));
        snapshot.setErrorDist(toJson(portrait.errorDist()));
        snapshot.setTotalPractice(portrait.totalPractice());
        snapshot.setTotalError(portrait.totalError());
        try {
            snapshotMapper.insert(snapshot);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 幂等：同 user_id + stat_date 已存在则更新
            snapshotMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<LearningPortraitSnapshot>()
                    .eq(LearningPortraitSnapshot::getUserId, userId)
                    .eq(LearningPortraitSnapshot::getStatDate, statDate)
                    .set(LearningPortraitSnapshot::getDimensionScores, snapshot.getDimensionScores())
                    .set(LearningPortraitSnapshot::getWeakestKps, snapshot.getWeakestKps())
                    .set(LearningPortraitSnapshot::getErrorDist, snapshot.getErrorDist())
                    .set(LearningPortraitSnapshot::getTotalPractice, snapshot.getTotalPractice())
                    .set(LearningPortraitSnapshot::getTotalError, snapshot.getTotalError()));
        }
    }

    private String toJson(Object value) {
        try {
            return new tools.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}

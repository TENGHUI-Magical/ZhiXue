package com.zhixue.diagnosis.service;

import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.KnowledgePrereq;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.diagnosis.dto.KpPracticeSignal;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 补漏排序（PRP）：先修 1—2 跳 × 遗忘紧迫度 × 错因可修复性（docs/05 §3.7）。
 * <p>
 * 纯 Java 计算，不新增表/接口：计划生成与三层推荐共用本服务的排序与理由，
 * 避免 PlanService 按错题计数、RecommendationService 按难度各用一套口径。</p>
 * <ul>
 *   <li>先修权重：1 跳先修 = 1.0，2 跳 = 0.7，薄弱点本身 = 0.6，
 *       薄弱点直接后继 = 0.5，课程内其他点 = 0.2（保证周计划仍有覆盖）。</li>
 *   <li>遗忘紧迫度：1 - 0.5^(距上次<b>正确</b>作答天数 / half-life-days)，从未答对记 1.0。
 *       刚答错不代表没遗忘：锚点取最后一次正确作答，避免“昨天答错”被误判为不紧迫。</li>
 *   <li>错因可修复性：概念混淆 1.0、方法缺失 0.9、步骤遗漏 0.8、
 *       边界 0.7、计算 0.5、未归类/无错因 0.5。</li>
 *   <li>score = 先修权重 × 紧迫度 × 可修复性。</li>
 * </ul>
 */
@Service
public class RepairPriorityService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 理由上限与 recommendation.reason VARCHAR(50) / openapi maxLength 对齐 */
    private static final int MAX_REASON_LENGTH = 45;
    /** 先修遍历最大跳数：1—2 跳 */
    private static final int MAX_PREREQ_DEPTH = 2;

    private static final Map<String, Double> REPAIRABILITY = Map.of(
            "CONCEPT_CONFUSION", 1.0,
            "METHOD_MISSING", 0.9,
            "STEP_OMISSION", 0.8,
            "BOUNDARY_ERROR", 0.7,
            "CALCULATION_ERROR", 0.5,
            "UNCLASSIFIED", 0.5);
    private static final double DEFAULT_REPAIRABILITY = 0.5;

    /** 错因 → 展示名（错因“名”，教师端统计同用，保留）；句子化措辞见 ERROR_STUDENT_PHRASE。 */
    private static final Map<String, String> ERROR_LABEL = Map.of(
            "CONCEPT_CONFUSION", "概念混淆",
            "METHOD_MISSING", "方法缺失",
            "STEP_OMISSION", "步骤遗漏",
            "CALCULATION_ERROR", "计算错误",
            "BOUNDARY_ERROR", "边界错误",
            "UNCLASSIFIED", "错因未归类");

    /**
     * 错因 → 学生话术（学生可见文案专用，explain/firstReason/DueReview 共用，一处维护）。
     * 主语是“你”，动词化描述学生能对照自身行为的话；未归类如实说“再做一遍才知道”。
     */
    static final Map<String, String> ERROR_STUDENT_PHRASE = Map.of(
            "CONCEPT_CONFUSION", "把两个概念记混了",
            "METHOD_MISSING", "方法还没掌握",
            "STEP_OMISSION", "做题漏了步骤",
            "CALCULATION_ERROR", "计算上容易出错",
            "BOUNDARY_ERROR", "特殊情况容易漏",
            "UNCLASSIFIED", "错在哪再做一遍才知道");

    /** 学生视角的错因话术；未知/空错因同 UNCLASSIFIED，不猜。 */
    static String errorStudentPhrase(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return ERROR_STUDENT_PHRASE.get("UNCLASSIFIED");
        }
        return ERROR_STUDENT_PHRASE.getOrDefault(
                errorType.trim().toUpperCase(Locale.ROOT), ERROR_STUDENT_PHRASE.get("UNCLASSIFIED"));
    }

    private final PracticeRecordMapper practiceRecordMapper;
    private final CourseService courseService;
    private final KnowledgePointService knowledgePointService;
    private final double halfLifeDays;
    /** 冷却天数（recommend.cooldown-days，与推荐/计划/到期复习共用）：firstReason 的“刚练过”判定 */
    private final int cooldownDays;

    public RepairPriorityService(PracticeRecordMapper practiceRecordMapper,
                                 CourseService courseService,
                                 KnowledgePointService knowledgePointService,
                                 @Value("${mastery.half-life-days:30}") double halfLifeDays,
                                 @Value("${recommend.cooldown-days:7}") int cooldownDays) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.courseService = courseService;
        this.knowledgePointService = knowledgePointService;
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
        this.cooldownDays = Math.max(1, cooldownDays);
    }

    /**
     * 单个知识点的补漏评分与理由素材。
     * daysSinceLastCorrect 为距上次正确作答天数，null=从未答对；
     * daysSinceLastPractice 为距上次任意作答天数（firstReason 的“冷却期内刚练过”判定），null=无记录。
     */
    public record Priority(String kpId, double score, int prereqDistance, boolean successor,
                           double urgency, double repairability,
                           String lastErrorType, Integer daysSinceLastCorrect,
                           Integer daysSinceLastPractice) {
    }

    /** 课程排序结果：knowledgePoints 已按补漏优先级降序，priorityByKp 供推荐列表复用。 */
    public record RankedCourse(List<KnowledgePoint> knowledgePoints,
                               Map<String, Priority> priorityByKp) {
    }

    public RankedCourse rank(long userId, long courseId) {
        return rank(userId, courseId, LocalDateTime.now(ZONE));
    }

    /**
     * 按补漏优先级排序课程内启用知识点。
     * 固定 3 条 SQL：知识点、练习信号、先修边；不随知识点数量增长。
     *
     * @param now 当前时间，显式传入便于单测固定遗忘天数
     */
    public RankedCourse rank(long userId, long courseId, LocalDateTime now) {
        List<KnowledgePoint> kps = courseService.listActiveKnowledgePoints(courseId);
        if (kps.isEmpty()) {
            return new RankedCourse(List.of(), Map.of());
        }
        List<String> kpIds = kps.stream().map(KnowledgePoint::getKpId).toList();
        Set<String> kpSet = new HashSet<>(kpIds);

        Map<String, KpPracticeSignal> signalByKp = new HashMap<>();
        for (KpPracticeSignal signal : practiceRecordMapper.selectKpSignals(userId, courseId)) {
            if (signal != null && signal.getKpId() != null) {
                signalByKp.put(signal.getKpId(), signal);
            }
        }
        List<KnowledgePrereq> edges = knowledgePointService.listPrereqEdges(kpIds);

        Set<String> anchors = new HashSet<>();
        for (KpPracticeSignal signal : signalByKp.values()) {
            if (signal.getWrongCount() != null && signal.getWrongCount() > 0
                    && kpSet.contains(signal.getKpId())) {
                anchors.add(signal.getKpId());
            }
        }
        Map<String, Integer> prereqDistance = distancesToAnchors(anchors, edges, kpSet);
        Set<String> successors = successorsOfAnchors(anchors, edges, kpSet);

        Map<String, Priority> priorities = new HashMap<>();
        for (KnowledgePoint kp : kps) {
            KpPracticeSignal signal = signalByKp.get(kp.getKpId());
            Integer days = daysSinceCorrect(signal == null ? null : signal.getLastCorrectAt(), now);
            double urgency = days == null ? 1.0 : 1.0 - Math.pow(0.5, days / halfLifeDays);
            String errorType = signal == null ? null : signal.getLastErrorType();
            double repairability = repairability(errorType);
            int distance = prereqDistance.getOrDefault(kp.getKpId(), -1);
            boolean successor = successors.contains(kp.getKpId());
            double weight = prereqWeight(distance, successor);
            Integer daysPracticed = daysSinceCorrect(
                    signal == null ? null : signal.getLastPracticedAt(), now);
            priorities.put(kp.getKpId(), new Priority(kp.getKpId(), weight * urgency * repairability,
                    distance, successor, urgency, repairability, errorType, days, daysPracticed));
        }

        List<KnowledgePoint> ordered = new ArrayList<>(kps);
        ordered.sort(Comparator
                .comparingDouble((KnowledgePoint kp) -> priorities.get(kp.getKpId()).score())
                .reversed()
                .thenComparingInt(kp -> tieBreakDistance(priorities.get(kp.getKpId())))
                .thenComparingInt(kp -> kp.getSortNo() == null ? 0 : kp.getSortNo())
                .thenComparing(KnowledgePoint::getKpId));
        return new RankedCourse(List.copyOf(ordered), priorities);
    }

    /**
     * 每条推荐的简短理由（学生可见默认文案，≤45 字）。
     * <p><b>学生语言硬约束（2026-11-03 复核追加，改文案前必读）</b>：目标读者是怕考试的学生，
     * 只回答"为什么练它"；禁用内部术语——"先修/权重/优先级/因子/可修复/紧迫度/补漏/聚合/后验"
     * 只能出现在 docs/05 的技术说明层，不得进入本方法返回值；术语换成具体对象
     * （如直接先修 → "这道题要用到它"）；保留数字证据（天数/次数）。</p>
     * 例："这道题要用到它，已 38 天没答对，把两个概念记混了"。
     * 知识点不在课程范围内或没有评分时返回 fallback，保证理由非空。
     */
    public String explain(Priority priority, String fallback) {
        if (priority == null) {
            return fallback == null || fallback.isBlank() ? "现在练它最划算" : fallback;
        }
        StringBuilder builder = new StringBuilder();
        if (priority.prereqDistance() == 1) {
            builder.append("这道题要用到它");
        } else if (priority.prereqDistance() == 2) {
            builder.append("它是更基础的知识");
        } else if (priority.prereqDistance() == 0) {
            builder.append("你刚错的就是这块");
        } else if (priority.successor()) {
            builder.append("学完这块，下一课就顺");
        } else {
            builder.append("现在练它最划算");
        }
        if (priority.daysSinceLastCorrect() == null) {
            builder.append("，还没答对过");
        } else if (priority.daysSinceLastCorrect() == 0) {
            builder.append("，今天刚答对");
        } else {
            builder.append("，已 ").append(priority.daysSinceLastCorrect()).append(" 天没答对");
        }
        if (priority.lastErrorType() != null && !priority.lastErrorType().isBlank()) {
            builder.append("，").append(errorStudentPhrase(priority.lastErrorType()));
        }
        String reason = builder.toString();
        if (reason.length() <= MAX_REASON_LENGTH) {
            return reason;
        }
        return reason.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }

    /** 先修权重（docs/05 §3.7 决策表）。 */
    private double prereqWeight(int distance, boolean successor) {
        if (distance == 1) {
            return 1.0;
        }
        if (distance == 2) {
            return 0.7;
        }
        if (distance == 0) {
            return 0.6;
        }
        return successor ? 0.5 : 0.2;
    }

    /** 同分时先修点优先，其余按 sortNo/kpId，保证多次调用顺序稳定。 */
    private int tieBreakDistance(Priority priority) {
        return priority.prereqDistance() < 0 ? MAX_PREREQ_DEPTH + 1 : priority.prereqDistance();
    }

    private double repairability(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return DEFAULT_REPAIRABILITY;
        }
        return REPAIRABILITY.getOrDefault(errorType.trim().toUpperCase(Locale.ROOT),
                DEFAULT_REPAIRABILITY);
    }

    /** 错因 → 展示名；同包的到期复习理由复用，避免两处映射漂移。 */
    static String errorLabel(String errorType) {
        return ERROR_LABEL.getOrDefault(errorType.trim().toUpperCase(Locale.ROOT), "错因未归类");
    }

    private String label(String errorType) {
        return errorLabel(errorType);
    }

    /** firstReason 上限（top1Reason 契约 maxLength=45 对齐） */
    private static final int MAX_FIRST_REASON_LENGTH = 45;

    /**
     * “为什么排第一”一句话（05 §3.7，算法创新评审 2026-11-03 唯一采纳项）：由现有三因子
     * （先修权重 / 遗忘紧迫度 / 错因可修复性）确定性模板拼接，不新增模型、不调 LLM、不改分数，
     * 同一输入永远同一句话，可单测。与 {@link #explain} 的分工：explain 是每条推荐的逐条
     * 简短理由；firstReason 是重排后第一名的叙事句。
     *
     * <p><b>学生语言硬约束（2026-11-03 复核追加，改文案前必读）</b>：目标读者是怕考试的
     * 大二学生，只回答三件事——练哪个、为什么是它、不练会怎样。</p>
     * <ul>
     *   <li>长度硬上限 <b>45 字</b>，一段话说清，不分号堆砌；</li>
     *   <li><b>禁用词</b>：先修、权重、优先级、因子、可修复、紧迫度、补漏、聚合、后验——
     *       这些词只能出现在 docs/05 的技术说明层，不得进入返回值；</li>
     *   <li>术语换具体对象：直接先修 → “这道题要用到它”；概念混淆 → “你把两个概念记混了”；</li>
     *   <li>保留数字证据（天数、次数），学生能感知；</li>
     *   <li>仍是确定性模板，同一输入同一句话，禁止改为模型自由生成。</li>
     * </ul>
     * <p>四种退化边界各有固定文案：无先修关系、从未答对、错因待归类、冷却期内刚练过仍排第一。</p>
     */
    public String firstReason(String kpName, Priority priority, String fallback) {
        if (priority == null) {
            return fallback == null || fallback.isBlank() ? "先练它，见效最快" : fallback;
        }
        String name = kpName == null || kpName.isBlank() ? priority.kpId() : kpName;
        StringBuilder builder = new StringBuilder("先练").append(name).append("：");
        // 冷却边界：N 天前刚练过仍排第一 → “还是错”说明没学会，趁热再练（替代正常收尾）
        boolean inCooldown = priority.daysSinceLastPractice() != null
                && priority.daysSinceLastPractice() < cooldownDays;
        // 因子一：先修关系（无关系时按“最没把握”说，不编造关联）
        if (priority.prereqDistance() == 1) {
            builder.append("这道题要用到它");
        } else if (priority.prereqDistance() == 2) {
            builder.append("它是更基础的知识");
        } else if (priority.prereqDistance() == 0) {
            builder.append("你刚错的就是这块");
        } else if (priority.successor()) {
            builder.append("学完这块，下一课就顺");
        } else {
            builder.append("它是你现在最没把握的一块");
        }
        // 因子二：遗忘（从未答对如实说，不虚造天数；冷却时改说“刚练过还是错”）
        if (inCooldown) {
            builder.append("，").append(priority.daysSinceLastPractice())
                    .append(" 天前刚练过还是错，趁热再练一遍最有效");
        } else if (priority.daysSinceLastCorrect() == null) {
            builder.append("，还没答对过");
        } else if (priority.daysSinceLastCorrect() == 0) {
            builder.append("，今天刚答对");
        } else {
            builder.append("，已 ").append(priority.daysSinceLastCorrect()).append(" 天没答对");
        }
        // 因子三：错因话术（冷却句已收尾，不再拼错因避免超长；未归类说“再做一遍才知道”）
        String errorType = priority.lastErrorType();
        if (!inCooldown && errorType != null && !errorType.isBlank()) {
            builder.append("，").append(errorStudentPhrase(errorType));
        }
        if (!inCooldown) {
            builder.append("，先练它见效最快");
        }
        String reason = builder.toString();
        return reason.length() <= MAX_FIRST_REASON_LENGTH ? reason
                : reason.substring(0, MAX_FIRST_REASON_LENGTH - 1) + "…";
    }

    /** 距最后一次正确作答的整天数；null 表示从未答对（紧迫度记 1.0，最该补）。 */
    private Integer daysSinceCorrect(LocalDateTime lastCorrectAt, LocalDateTime now) {
        if (lastCorrectAt == null || now == null) {
            return null;
        }
        return (int) Math.max(0, Duration.between(lastCorrectAt, now).toDays());
    }

    /**
     * 多源 BFS：薄弱点距离 0，其先修 1 跳，先修的先修 2 跳；超过 2 跳不纳入。
     * 只使用课程内两端都在集合中的边，避免跨课程先修污染排序。
     */
    private Map<String, Integer> distancesToAnchors(Set<String> anchors,
                                                    List<KnowledgePrereq> edges,
                                                    Set<String> kpSet) {
        Map<String, Integer> distance = new HashMap<>();
        if (anchors.isEmpty()) {
            return distance;
        }
        Map<String, List<String>> prereqsOf = new HashMap<>();
        for (KnowledgePrereq edge : edges) {
            if (edge.getKpId() == null || edge.getPrereqKpId() == null
                    || !kpSet.contains(edge.getKpId()) || !kpSet.contains(edge.getPrereqKpId())) {
                continue;
            }
            prereqsOf.computeIfAbsent(edge.getKpId(), key -> new ArrayList<>())
                    .add(edge.getPrereqKpId());
        }
        Deque<String> queue = new ArrayDeque<>();
        for (String anchor : anchors) {
            distance.put(anchor, 0);
            queue.add(anchor);
        }
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int depth = distance.get(current);
            if (depth >= MAX_PREREQ_DEPTH) {
                continue;
            }
            for (String prereq : prereqsOf.getOrDefault(current, List.of())) {
                if (!distance.containsKey(prereq)) {
                    distance.put(prereq, depth + 1);
                    queue.add(prereq);
                }
            }
        }
        return distance;
    }

    /** 薄弱点的直接后继：edge.prereqKpId = 薄弱点，edge.kpId 为后继。 */
    private Set<String> successorsOfAnchors(Set<String> anchors,
                                            List<KnowledgePrereq> edges,
                                            Set<String> kpSet) {
        Set<String> result = new HashSet<>();
        for (KnowledgePrereq edge : edges) {
            if (edge.getPrereqKpId() == null || edge.getKpId() == null) {
                continue;
            }
            if (anchors.contains(edge.getPrereqKpId()) && kpSet.contains(edge.getKpId())) {
                result.add(edge.getKpId());
            }
        }
        return result;
    }
}

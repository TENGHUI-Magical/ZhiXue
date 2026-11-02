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
    private static final int MAX_REASON_LENGTH = 50;
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

    private static final Map<String, String> ERROR_LABEL = Map.of(
            "CONCEPT_CONFUSION", "概念混淆",
            "METHOD_MISSING", "方法缺失",
            "STEP_OMISSION", "步骤遗漏",
            "CALCULATION_ERROR", "计算错误",
            "BOUNDARY_ERROR", "边界错误",
            "UNCLASSIFIED", "错因未归类");

    private final PracticeRecordMapper practiceRecordMapper;
    private final CourseService courseService;
    private final KnowledgePointService knowledgePointService;
    private final double halfLifeDays;

    public RepairPriorityService(PracticeRecordMapper practiceRecordMapper,
                                 CourseService courseService,
                                 KnowledgePointService knowledgePointService,
                                 @Value("${mastery.half-life-days:30}") double halfLifeDays) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.courseService = courseService;
        this.knowledgePointService = knowledgePointService;
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
    }

    /** 单个知识点的补漏评分与理由素材；daysSinceLastCorrect 为距上次正确作答天数，null=从未答对。 */
    public record Priority(String kpId, double score, int prereqDistance, boolean successor,
                           double urgency, double repairability,
                           String lastErrorType, Integer daysSinceLastCorrect) {
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
            priorities.put(kp.getKpId(), new Priority(kp.getKpId(), weight * urgency * repairability,
                    distance, successor, urgency, repairability, errorType, days));
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
     * 可解释理由，≤50 字：关系 + 遗忘紧迫度 + 最近错因。
     * 例：前置知识点未掌握 + 距上次答对 38 天 + 概念混淆型错误。
     * 知识点不在课程范围内或没有评分时返回固定模板，保证理由非空。
     */
    public String explain(Priority priority, String fallback) {
        if (priority == null) {
            return fallback == null || fallback.isBlank() ? "常规巩固" : fallback;
        }
        List<String> parts = new ArrayList<>(3);
        if (priority.prereqDistance() == 1) {
            parts.add("前置知识点未掌握");
        } else if (priority.prereqDistance() == 2) {
            parts.add("二级前置未掌握");
        } else if (priority.prereqDistance() == 0) {
            parts.add("当前知识点未掌握");
        } else if (priority.successor()) {
            parts.add("衔接后继知识点");
        } else {
            parts.add("常规巩固");
        }
        if (priority.daysSinceLastCorrect() == null) {
            parts.add("尚未答对");
        } else if (priority.daysSinceLastCorrect() == 0) {
            parts.add("今日已答对");
        } else {
            parts.add("距上次答对 " + priority.daysSinceLastCorrect() + " 天");
        }
        if (priority.lastErrorType() != null && !priority.lastErrorType().isBlank()) {
            parts.add(label(priority.lastErrorType()) + "型错误");
        }
        String reason = String.join(" + ", parts);
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

    private String label(String errorType) {
        return ERROR_LABEL.getOrDefault(errorType.trim().toUpperCase(Locale.ROOT), "错因未归类");
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

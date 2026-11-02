package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.Recommendation;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.RecommendationRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.RecommendationRecordMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * 三层推荐（契约 V2：GET /api/recommendations；算法见 05 号文 §3）。
 * <p>
 * CONSOLIDATE 同知识点（难度 ≤ 近期均值）→ VARIANT 先修/后继 1 跳（难度 ±1）
 * → ADVANCE 后继知识点（难度 = min(3, 均值+1)）。
 * 去重：排除当前错题与 7 天内已练题，不足按 7 天 → 3 天 → 允许重复放宽。
 * 理由只由固定模板生成（05 §3.4）；持久化按唯一键幂等，保留 is_adopted（05 §3.3.4）。
 */
@Service
public class RecommendationService {

    /** 05 §3.4 理由模板（≤15 字，禁止自由发挥，保证可解释可测试） */
    private static final String REASON_CONSOLIDATE = "同知识点再练一道";
    private static final String REASON_VARIANT = "换个问法巩固一下";
    private static final String REASON_ADVANCE = "衔接下一知识点";
    private static final String REASON_INSUFFICIENT = "题量不足，先练这些";

    /** 05 §3.2：难度基准取最近 N 条练习，无记录取 2 */
    private static final int RECENT_PRACTICE_LIMIT = 5;
    private static final int DEFAULT_AVG_DIFFICULTY = 2;
    private static final int MAX_DIFFICULTY = 3;
    /** 05 §3.3.3：冷却不足时的第二档放宽（固定梯度，不随 cooldown-days 缩放） */
    private static final int RELAXED_COOLDOWN_DAYS = 3;

    private final StudentErrorRecordService errorRecordService;
    private final KnowledgePointService knowledgePointService;
    private final QuestionBankService questionBankService;
    private final RecommendationRecordMapper recommendationRecordMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final RepairPriorityService repairPriorityService;
    private final int cooldownDays;

    public RecommendationService(StudentErrorRecordService errorRecordService,
                                 KnowledgePointService knowledgePointService,
                                 QuestionBankService questionBankService,
                                 RecommendationRecordMapper recommendationRecordMapper,
                                 PracticeRecordMapper practiceRecordMapper,
                                 RepairPriorityService repairPriorityService,
                                 @Value("${recommend.cooldown-days:7}") int cooldownDays) {
        this.errorRecordService = errorRecordService;
        this.knowledgePointService = knowledgePointService;
        this.questionBankService = questionBankService;
        this.recommendationRecordMapper = recommendationRecordMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.repairPriorityService = repairPriorityService;
        this.cooldownDays = Math.max(1, cooldownDays);
    }

    /**
     * 按错题记录（logId）或知识点（kpId，竖切兜底）生成三层推荐；两者至少提供一个。
     *
     * @param logId    错题记录编号；有值时才落 recommendation 表
     * @param kpId     知识点编号；logId 为空时使用（不落库，recommendationId 为 null）
     * @param courseId 课程 ID；为空时按知识点所属课程解析，显式传入时须与知识点课程一致
     * @return 1—3 条推荐；无题可推时返回空数组
     */
    @Transactional
    public List<Recommendation> recommend(String logId, String kpId, Long courseId) {
        StudentErrorRecord record = null;
        String resolvedKp;
        if (logId != null && !logId.isBlank()) {
            record = errorRecordService.getConfirmed(logId);
            resolvedKp = record.getKpId();
        } else if (kpId != null && !kpId.isBlank()) {
            resolvedKp = kpId;
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "logId 与 kpId 至少提供一个");
        }
        KnowledgePoint kp = knowledgePointService.getById(resolvedKp);
        long resolvedCourseId = resolveCourseId(kp, courseId);
        long userId = StpUtil.getLoginIdAsLong();

        int avgDiff = recentAvgDifficulty(userId, resolvedKp);
        String currentQid = record == null ? null : record.getQId();
        // 冷却基准来自 recommend.cooldown-days（05 §0），与计划排题、到期复习共用同一参数
        Set<String> cooldownBase = practicedQids(userId, resolvedCourseId, cooldownDays);
        Set<String> cooldownRelaxed = practicedQids(userId, resolvedCourseId, RELAXED_COOLDOWN_DAYS);

        List<QuestionView> same = questionBankService.listByKp(resolvedCourseId, resolvedKp);
        // 05 §3.1/§3.6：先修/后继池走 question_kp 关联（含次要关联），题量不足时同池兜底
        List<QuestionView> prereq = collectLinked(knowledgePointService.prerequisites(resolvedKp), resolvedCourseId);
        List<QuestionView> successor = collectLinked(knowledgePointService.successors(resolvedKp), resolvedCourseId);

        Set<String> picked = new HashSet<>();
        List<Recommendation> result = new ArrayList<>(3);

        addIfPresent(result, pick(same, picked, currentQid, cooldownBase, cooldownRelaxed, avgDiff, avgDiff,
                d -> d <= avgDiff), "CONSOLIDATE", REASON_CONSOLIDATE, picked);

        List<QuestionView> variantPool = prereq.isEmpty() ? same : prereq;
        addIfPresent(result, pick(variantPool, picked, currentQid, cooldownBase, cooldownRelaxed, avgDiff,
                avgDiff, d -> Math.abs(d - avgDiff) <= 1), "VARIANT", REASON_VARIANT, picked);

        List<QuestionView> advancePool = successor.isEmpty() ? same : successor;
        // 05 §3.2：拓展层优先 min(3, 均值+1)，候选不足才放宽为 ≥ 均值
        int advanceTarget = Math.min(MAX_DIFFICULTY, avgDiff + 1);
        addIfPresent(result, pick(advancePool, picked, currentQid, cooldownBase, cooldownRelaxed, avgDiff,
                advanceTarget, d -> d >= avgDiff), "ADVANCE", REASON_ADVANCE, picked);

        // 05 §3.7：与计划生成共用补漏排序，按先修权重 × 遗忘紧迫度 × 错因可修复性重排三层，
        // 理由改为可解释的组合模板；题目所属知识点由题池映射（题目本身不冗余 kpId 到推荐 DTO）。
        applyRepairPriority(result, kpByQid(same, prereq, successor),
                repairPriorityService.rank(userId, resolvedCourseId));

        if (result.size() < 3) {
            // 05 §3.3.3：数量不足时整批改用统一模板，提示题库待补充
            for (int i = 0; i < result.size(); i++) {
                Recommendation item = result.get(i);
                result.set(i, new Recommendation(item.recommendationId(), item.qId(),
                        item.layer(), REASON_INSUFFICIENT, item.difficulty(), item.isAdopted(),
                        item.questionText()));
            }
        }

        if (record != null) {
            return persist(record.getLogId(), userId, result);
        }
        // kpId 兜底路径不落库：log_id 为 NULL 时唯一键 uk_user_log_q 不去重，会重复插行
        return result;
    }

    /**
     * 选题：冷却 7 天 → 3 天 → 允许重复 → 难度区间放宽为任意，依次放宽；
     * 每轮取难度最接近目标值的题（拓展层目标为 min(3, 均值+1)）。
     * 四轮是 05 §3.2/§3.3 放宽语义的落地。
     */
    private QuestionView pick(List<QuestionView> pool, Set<String> picked, String currentQid,
                              Set<String> cooldown7, Set<String> cooldown3, int avgDiff,
                              int targetDifficulty, IntPredicate difficultyRange) {
        List<java.util.function.Predicate<QuestionView>> rounds = List.of(
                q -> !cooldown7.contains(q.qId()),
                q -> !cooldown3.contains(q.qId()),
                q -> true,
                q -> true);
        for (int i = 0; i < rounds.size(); i++) {
            boolean relaxedRange = i == 3;
            java.util.function.Predicate<QuestionView> cooldown = rounds.get(i);
            final boolean useRange = !relaxedRange;
            QuestionView chosen = pool.stream()
                    .filter(q -> q.qId() != null && !picked.contains(q.qId()))
                    .filter(q -> !q.qId().equals(currentQid))
                    .filter(cooldown)
                    .filter(q -> !useRange || difficultyRange.test(difficulty(q)))
                    .min(Comparator.comparingInt(q -> Math.abs(difficulty(q) - targetDifficulty)))
                    .orElse(null);
            if (chosen != null) {
                return chosen;
            }
        }
        return null;
    }

    private void addIfPresent(List<Recommendation> result, QuestionView question, String layer,
                              String reason, Set<String> picked) {
        if (question == null) {
            return;
        }
        picked.add(question.qId());
        result.add(new Recommendation(null, question.qId(), layer, reason,
                question.difficulty(), false, question.questionText()));
    }

    /** 题号 → 主知识点：用于把题目映射到补漏评分（题池已加载，不再回查题库）。 */
    @SafeVarargs
    private final Map<String, String> kpByQid(List<QuestionView>... pools) {
        Map<String, String> mapping = new HashMap<>();
        for (List<QuestionView> pool : pools) {
            for (QuestionView question : pool) {
                if (question.qId() != null && question.primaryKpId() != null) {
                    mapping.putIfAbsent(question.qId(), question.primaryKpId());
                }
            }
        }
        return mapping;
    }

    /**
     * 05 §3.7：按补漏评分重排推荐列表，并用同一评分生成可解释理由。
     * 评分相同按层序、题号兜底，保证同一输入多次调用顺序稳定；题库为空时直接返回。
     */
    private void applyRepairPriority(List<Recommendation> result, Map<String, String> kpByQid,
                                     RepairPriorityService.RankedCourse ranked) {
        if (result.isEmpty()) {
            return;
        }
        Map<String, RepairPriorityService.Priority> priorities = ranked.priorityByKp();
        result.sort(Comparator
                .comparingDouble((Recommendation item) ->
                        score(priorities, kpByQid.get(item.qId())))
                .reversed()
                .thenComparingInt(item -> layerOrder(item.layer()))
                .thenComparing(Recommendation::qId));
        for (int i = 0; i < result.size(); i++) {
            Recommendation item = result.get(i);
            RepairPriorityService.Priority priority = priorities.get(kpByQid.get(item.qId()));
            result.set(i, new Recommendation(item.recommendationId(), item.qId(), item.layer(),
                    repairPriorityService.explain(priority, item.reason()), item.difficulty(),
                    item.isAdopted(), item.questionText()));
        }
    }

    private double score(Map<String, RepairPriorityService.Priority> priorities, String kpId) {
        RepairPriorityService.Priority priority = kpId == null ? null : priorities.get(kpId);
        return priority == null ? 0.0 : priority.score();
    }

    private int layerOrder(String layer) {
        if ("CONSOLIDATE".equals(layer)) {
            return 0;
        }
        if ("VARIANT".equals(layer)) {
            return 1;
        }
        return "ADVANCE".equals(layer) ? 2 : 3;
    }

    private int difficulty(QuestionView q) {
        return q.difficulty() == null ? 2 : q.difficulty();
    }

    /** 课程解析：未传时取知识点所属课程；显式传入时必须一致，避免多课程下拿错题库。 */
    private long resolveCourseId(KnowledgePoint kp, Long courseId) {
        Long kpCourseId = kp.getCourseId();
        if (kpCourseId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "知识点缺少课程归属: " + kp.getKpId());
        }
        if (courseId != null && !courseId.equals(kpCourseId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "courseId 与知识点所属课程不一致");
        }
        return kpCourseId;
    }

    /** 05 §3.1：先修/后继池：一次 IN 批量查看 question_kp 关联（含次要关联），避免逐 kp N+1。 */
    private List<QuestionView> collectLinked(List<String> kpIds, Long courseId) {
        return questionBankService.listByKpIdsIncludeSecondary(courseId, kpIds);
    }

    /** 05 §3.2：最近 5 条该知识点练习的题目难度均值（四舍五入）；无记录取 2 */
    private int recentAvgDifficulty(long userId, String kpId) {
        List<PracticeRecord> recent = practiceRecordMapper.selectList(new LambdaQueryWrapper<PracticeRecord>()
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getKpId, kpId)
                .orderByDesc(PracticeRecord::getPracticeId)
                .last("LIMIT " + RECENT_PRACTICE_LIMIT));
        if (recent.isEmpty()) {
            return DEFAULT_AVG_DIFFICULTY;
        }
        Map<String, Integer> difficultyByQid = questionBankService.difficultyByQids(
                recent.stream().map(PracticeRecord::getQId).toList());
        List<Integer> difficulties = recent.stream()
                .map(r -> difficultyByQid.get(r.getQId()))
                .filter(Objects::nonNull)
                .toList();
        if (difficulties.isEmpty()) {
            return DEFAULT_AVG_DIFFICULTY;
        }
        return (int) Math.round(difficulties.stream()
                .mapToInt(Integer::intValue)
                .average().orElse(DEFAULT_AVG_DIFFICULTY));
    }

    /** 05 §3.3：按课程过滤的近期已练题目（7 天 / 3 天冷却） */
    private Set<String> practicedQids(long userId, long courseId, int days) {
        return new HashSet<>(practiceRecordMapper.selectPracticedQids(
                userId, courseId, LocalDateTime.now().minusDays(days)));
    }

    /**
     * 幂等落库（05 §3.3.4）：按唯一键 uk_user_log_q，重复生成命中已有记录并保留其
     * is_adopted（学生是否练过不能被覆盖）；外层 @Transactional 保证整批一致。
     */
    private List<Recommendation> persist(String logId, long userId, List<Recommendation> result) {
        List<Recommendation> persisted = new ArrayList<>(result.size());
        int rankNo = 1;
        for (Recommendation item : result) {
            RecommendationRecord row = new RecommendationRecord();
            row.setUserId(userId);
            row.setLogId(logId);
            row.setQId(item.qId());
            row.setLayer(item.layer());
            row.setReason(item.reason());
            row.setRankNo(rankNo++);
            row.setIsAdopted(0);
            try {
                recommendationRecordMapper.insert(row);
            } catch (DuplicateKeyException e) {
                RecommendationRecord existing = recommendationRecordMapper.selectOne(
                        new LambdaQueryWrapper<RecommendationRecord>()
                                .eq(RecommendationRecord::getUserId, userId)
                                .eq(RecommendationRecord::getLogId, logId)
                                .eq(RecommendationRecord::getQId, item.qId())
                                .last("LIMIT 1"));
                if (existing != null) {
                    row = existing;
                } else {
                    throw e;
                }
            }
            boolean adopted = row.getIsAdopted() != null && row.getIsAdopted() == 1;
            persisted.add(new Recommendation(row.getRecommendationId(), item.qId(), item.layer(),
                    item.reason(), item.difficulty(), adopted, item.questionText()));
        }
        return persisted;
    }
}

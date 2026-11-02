package com.zhixue.diagnosis.service;

import cn.dev33.satoken.stp.StpUtil;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.DueReviewItem;
import com.zhixue.diagnosis.dto.KpPracticeSignal;
import com.zhixue.diagnosis.dto.QuestionPracticeSignal;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 到期复习队列（docs/05 §3.8）：按“上次作答时间 + 掌握度”算出今天该复习的知识点。
 * <p>
 * 间隔重复此前只有“形”（半衰衰减参与掌握度与 PRP），没有“到期”的实——学生今天
 * 该复习什么没有任何机制算出来。本服务补上判定与排序，全部复用现有参数，不另造时间常数：</p>
 * <ul>
 *   <li><b>有正确证据</b>：lastCorrectAt 非空。从未答对的知识点不是“复习”而是“补漏”，
 *       走 PRP（§3.7），不进到期队列。</li>
 *   <li><b>遗忘过半</b>：距上次正确作答 ≥ {@code mastery.half-life-days}（30 天），
 *       保留率 ≤ 50%——半衰期既是掌握度衰减参数，也是到期阈值，一处定义。</li>
 *   <li><b>冷却外</b>：距上次任意作答 ≥ {@code recommend.cooldown-days}（7 天），
 *       刚复习过/刚练过的点当天不重复出队。</li>
 * </ul>
 * 排序：{@code score = 遗忘紧迫度 × (1 - 掌握度)}；紧迫度与 PRP 同式
 * {@code 1 - 0.5^(days/half-life)}，掌握度与画像同源（{@link PortraitService#userKpAggregates}，
 * 窗口外无证据按先验 0.5）；同分按 sortNo → kpId 兜底。
 * 选题：优先重做该知识点最近答错的题，其次最近答对的题（同题重测保留率），
 * 都没有时用未练过的题兜底（难度升序）；无可用题目则跳过并记日志（内容缺口）。
 * 复习完成的回执就是练习记录本身：作答刷新 lastPracticedAt/lastCorrectAt，
 * 冷却与半衰自然接管下一次到期，无新增状态表。
 */
@Service
public class DueReviewService {

    private static final Logger log = LoggerFactory.getLogger(DueReviewService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 理由上限与 recommendation.reason VARCHAR(50) / openapi maxLength 对齐 */
    private static final int MAX_REASON_LENGTH = 50;
    /** 今日到期队列上限：与画像薄弱点 WEAK_LIMIT 一致的日消化量 */
    private static final int DUE_LIMIT = 5;
    /** 掌握度聚合窗口外（无证据）的先验值，与 Beta(2,2) 均值一致 */
    private static final double PRIOR_MASTERY = 0.5;

    private final PracticeRecordMapper practiceRecordMapper;
    private final PortraitService portraitService;
    private final CourseService courseService;
    private final QuestionBankService questionBankService;
    private final MasteryCalculator masteryCalculator;
    private final double halfLifeDays;
    private final int cooldownDays;

    public DueReviewService(PracticeRecordMapper practiceRecordMapper,
                            PortraitService portraitService,
                            CourseService courseService,
                            QuestionBankService questionBankService,
                            @Value("${mastery.half-life-days:30}") double halfLifeDays,
                            @Value("${recommend.cooldown-days:7}") int cooldownDays) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.portraitService = portraitService;
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
        this.cooldownDays = Math.max(0, cooldownDays);
        this.masteryCalculator = new MasteryCalculator(this.halfLifeDays);
    }

    /** 当前学生的到期复习队列（契约 GET /api/plans/due-reviews）。 */
    public List<DueReviewItem> dueReviews(Long courseId) {
        long userId = StpUtil.getLoginIdAsLong();
        Course course = courseService.getActiveById(courseId);
        return dueReviews(userId, course.getCourseId(), LocalDateTime.now(ZONE));
    }

    /**
     * 判定 + 排序 + 选题；now 显式传入便于单测固定遗忘天数。
     * 固定 4 条 SQL：知识点、kp 信号、题目信号、课程题库；不随知识点数量增长。
     */
    public List<DueReviewItem> dueReviews(long userId, long courseId, LocalDateTime now) {
        List<KnowledgePoint> kps = courseService.listActiveKnowledgePoints(courseId);
        if (kps.isEmpty()) {
            return List.of();
        }
        Map<String, KnowledgePoint> kpById = new HashMap<>();
        for (KnowledgePoint kp : kps) {
            kpById.put(kp.getKpId(), kp);
        }

        List<DueCandidate> candidates = new ArrayList<>();
        for (KpPracticeSignal signal : practiceRecordMapper.selectKpSignals(userId, courseId)) {
            if (signal == null || signal.getKpId() == null || !kpById.containsKey(signal.getKpId())) {
                continue;
            }
            DueCandidate candidate = toCandidate(signal, now);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 掌握度与画像同源：学生看到的画像数字与到期排序里的掌握度不能两处口径
        Map<String, UserKpAgg> aggs = portraitService.userKpAggregates(userId);
        Map<String, List<QuestionPracticeSignal>> practicedByKp = new HashMap<>();
        for (QuestionPracticeSignal signal : practiceRecordMapper.selectQuestionSignals(userId,
                courseId)) {
            if (signal != null && signal.getKpId() != null && signal.getQId() != null) {
                practicedByKp.computeIfAbsent(signal.getKpId(), key -> new ArrayList<>()).add(signal);
            }
        }
        Map<String, QuestionView> questionById = new HashMap<>();
        for (QuestionView question : questionBankService.listByCourse(courseId)) {
            if (question.qId() != null) {
                questionById.put(question.qId(), question);
            }
        }

        List<ScoredDue> scored = new ArrayList<>(candidates.size());
        for (DueCandidate candidate : candidates) {
            QuestionView question = pickQuestion(candidate.kpId(), practicedByKp, questionById);
            if (question == null) {
                log.warn("知识点 {} 在课程 {} 下无可用题目，到期复习项跳过", candidate.kpId(), courseId);
                continue;
            }
            UserKpAgg agg = aggs.get(candidate.kpId());
            double mastery = agg == null ? PRIOR_MASTERY : masteryCalculator.mastery(
                    agg.getSuccessWeight() == null ? 0.0 : agg.getSuccessWeight(),
                    agg.getSeveritySum() == null ? 0.0 : agg.getSeveritySum());
            // 排序分：遗忘紧迫度 × 未掌握程度；紧迫度与 PRP §3.7 同式
            double urgency = 1.0 - Math.pow(0.5, candidate.days() / halfLifeDays);
            KnowledgePoint kp = kpById.get(candidate.kpId());
            scored.add(new ScoredDue(new DueReviewItem("DUE_REVIEW", candidate.kpId(),
                    kp.getName(), question.qId(), question.questionText(), difficulty(question),
                    candidate.days(), round3(mastery), candidate.totalCount(), reason(candidate)),
                    urgency * (1.0 - mastery), kp.getSortNo() == null ? 0 : kp.getSortNo()));
        }
        // score 降序 → sortNo → kpId（稳定兜底，与 PRP 一致），只保留日消化量内的前 N 项
        return scored.stream()
                .sorted(Comparator.comparingDouble((ScoredDue item) -> item.score()).reversed()
                        .thenComparingInt(ScoredDue::sortNo)
                        .thenComparing(item -> item.item().kpId()))
                .limit(DUE_LIMIT)
                .map(ScoredDue::item)
                .toList();
    }

    /** 排序中间结构：score 不进契约，截断后丢弃。 */
    private record ScoredDue(DueReviewItem item, double score, int sortNo) {
    }

    /** 到期判定（05 §3.8 三条件）；不满足任一返回 null。 */
    private DueCandidate toCandidate(KpPracticeSignal signal, LocalDateTime now) {
        if (signal.getLastCorrectAt() == null) {
            return null; // 从未答对：补漏（PRP）而非复习
        }
        int daysCorrect = days(signal.getLastCorrectAt(), now);
        if (daysCorrect < halfLifeDays) {
            return null; // 未过半衰期：保留率仍 > 50%
        }
        // 冷却锚点取最后一次任意作答；lastPracticedAt 理论上随 lastCorrectAt 非空，防御兜底
        int daysPracticed = signal.getLastPracticedAt() == null
                ? daysCorrect : days(signal.getLastPracticedAt(), now);
        if (daysPracticed < cooldownDays) {
            return null; // 刚复习过：冷却内不出队
        }
        int wrongCount = signal.getWrongCount() == null ? 0 : signal.getWrongCount();
        int totalCount = signal.getTotalCount() == null ? 0 : signal.getTotalCount();
        return new DueCandidate(signal.getKpId(), daysCorrect, wrongCount, totalCount,
                signal.getLastErrorType());
    }

    private record DueCandidate(String kpId, int days, int wrongCount, int totalCount,
                                String lastErrorType) {
    }

    /**
     * 选题（05 §3.8）：练过的题优先——最近答错的题最值得重做，其次最近答对的题（同题重测）；
     * 练过的题都已不在题库时用未练过的题兜底（难度升序、qId 稳定兜底）。
     * 到期知识点自身的练习必然在冷却外（kp 级冷却已挡），无需再做题级冷却。
     */
    private QuestionView pickQuestion(String kpId,
                                      Map<String, List<QuestionPracticeSignal>> practicedByKp,
                                      Map<String, QuestionView> questionById) {
        List<QuestionPracticeSignal> practiced = practicedByKp.getOrDefault(kpId, List.of());
        // 最近答错的题优先（时间降序），其次最近答对的题；qId 升序兜底保证稳定
        QuestionPracticeSignal best = practiced.stream()
                .filter(signal -> questionById.containsKey(signal.getQId()))
                .min(Comparator
                        .comparingInt(DueReviewService::practiceRank)
                        .thenComparing(signal -> signal.getLastWrongAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(signal -> signal.getLastCorrectAt(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(QuestionPracticeSignal::getQId))
                .orElse(null);
        if (best != null) {
            return questionById.get(best.getQId());
        }
        // 兜底：该 kp 主知识点下的未练题，难度升序；题干缺失（内容缺口）时返回 null 由调用方跳过
        Set<String> practicedQids = new HashSet<>();
        for (QuestionPracticeSignal signal : practiced) {
            practicedQids.add(signal.getQId());
        }
        return questionById.values().stream()
                .filter(question -> kpId.equals(question.primaryKpId()))
                .filter(question -> !practicedQids.contains(question.qId()))
                .min(Comparator.comparingInt(DueReviewService::difficulty)
                        .thenComparing(QuestionView::qId))
                .orElse(null);
    }

    /** 0=答错过（最优先重做），1=只答对过（同题重测），2=其他。 */
    private static int practiceRank(QuestionPracticeSignal signal) {
        if (signal.getLastWrongAt() != null) {
            return 0;
        }
        return signal.getLastCorrectAt() != null ? 1 : 2;
    }

    /**
     * 可解释理由（≤50 字，模板拼接禁止自由发挥）：
     * 时间段（到期依据）+ 错因段（答错过）或防遗忘说明（只对过）。
     */
    private String reason(DueCandidate candidate) {
        StringBuilder builder = new StringBuilder("距上次答对 ")
                .append(candidate.days()).append(" 天，已过半衰期");
        if (candidate.wrongCount() > 0 && candidate.lastErrorType() != null
                && !candidate.lastErrorType().isBlank()) {
            builder.append(" + ")
                    .append(RepairPriorityService.errorLabel(candidate.lastErrorType()))
                    .append("型错误");
        } else if (candidate.wrongCount() == 0) {
            builder.append("，防遗忘复习");
        }
        String reason = builder.toString();
        return reason.length() <= MAX_REASON_LENGTH ? reason
                : reason.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }

    private static int difficulty(QuestionView question) {
        return question.difficulty() == null ? 2 : question.difficulty();
    }

    /** 距今天的整天数（满 24 小时计数，与 PRP daysSinceCorrect 同口径）。 */
    private static int days(LocalDateTime at, LocalDateTime now) {
        return (int) Math.max(0, Duration.between(at, now).toDays());
    }

    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

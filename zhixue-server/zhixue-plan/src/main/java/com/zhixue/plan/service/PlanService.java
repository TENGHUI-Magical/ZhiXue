package com.zhixue.plan.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.dto.QuestionView;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.service.AnswerGrader;
import com.zhixue.diagnosis.service.PracticeService;
import com.zhixue.diagnosis.service.RepairPriorityService;
import com.zhixue.plan.dto.CheckinRequest;
import com.zhixue.plan.dto.CheckinResult;
import com.zhixue.plan.dto.PlanGenerateRequest;
import com.zhixue.plan.dto.PlanView;
import com.zhixue.plan.entity.StudyPlan;
import com.zhixue.plan.entity.StudyPlanItem;
import com.zhixue.plan.mapper.StudyPlanItemMapper;
import com.zhixue.plan.mapper.StudyPlanMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 学习计划服务：周计划生成、列表、打卡。
 * <p>
 * 选题顺序 = PRP 补漏排序（先修 1—2 跳 × 遗忘紧迫度 × 错因可修复性，05 §3.7，
 * 与推荐共用 RepairPriorityService 同一口径）；课程题池仍不足时按题库既有顺序兜底；
 * 同一周内尽量不重复题（冷却见 recommend.cooldown-days），题库不足时允许跨日重复。
 */
@Service
public class PlanService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * P1-2：answered=false 不写 practice_record（契约 docs/01:208），没有 (user_id, client_token)
     * 唯一键可作幂等凭据，故用进程内短窗口缓存识别「同用户同 token 重放」，命中返回首次结果。
     * 非持久化：多实例/进程重启后缓存丢失，重放会退回 40900（残余风险，见交付摘要）。
     */
    private static final long RECORD_ONLY_IDEMPOTENCY_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private static final int RECORD_ONLY_IDEMPOTENCY_MAX_SIZE = 4096;

    /** key = userId:clientToken；value = 首次 recordOnly 回执 + 登记时间。有界容量 + 过期淘汰，防泄漏。 */
    private final ConcurrentHashMap<String, IdempotencyEntry> recordOnlyIdempotency = new ConcurrentHashMap<>();

    private final StudyPlanMapper planMapper;
    private final StudyPlanItemMapper itemMapper;
    private final CourseService courseService;
    private final QuestionBankService questionBankService;
    private final PracticeService practiceService;
    private final RepairPriorityService repairPriorityService;
    private final int cooldownDays;

    public PlanService(StudyPlanMapper planMapper,
                       StudyPlanItemMapper itemMapper,
                       CourseService courseService,
                       QuestionBankService questionBankService,
                       PracticeService practiceService,
                       RepairPriorityService repairPriorityService,
                       @Value("${recommend.cooldown-days:7}") int cooldownDays) {
        this.planMapper = planMapper;
        this.itemMapper = itemMapper;
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.practiceService = practiceService;
        this.repairPriorityService = repairPriorityService;
        this.cooldownDays = Math.max(1, cooldownDays);
    }

    /** 按日期区间查计划；缺省为本周一至周日，明细批量补齐避免 N+1。 */
    public List<PlanView> list(LocalDate from, LocalDate to) {
        long userId = StpUtil.getLoginIdAsLong();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start;
        LocalDate end;
        if (from == null && to == null) {
            start = today.with(DayOfWeek.MONDAY);
            end = start.plusDays(6);
        } else if (from == null) {
            end = to;
            start = end.minusDays(6);
        } else if (to == null) {
            start = from;
            end = start.plusDays(6);
        } else {
            start = from;
            end = to;
        }
        if (start.isAfter(end)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "from 不能晚于 to");
        }
        List<StudyPlan> plans = planMapper.selectList(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .ge(StudyPlan::getPlanDate, start)
                .le(StudyPlan::getPlanDate, end)
                .orderByAsc(StudyPlan::getPlanDate));
        return toViews(plans);
    }

    /**
     * 生成一周 7 条日计划；已存在的日期复用，不覆盖已打卡计划。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<PlanView> generate(PlanGenerateRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        int daily = request.dailyQuestionCount() == null ? 3 : request.dailyQuestionCount();
        if (daily < 3 || daily > 5) {
            throw new BizException(ErrorCode.PARAM_ERROR, "dailyQuestionCount 必须在 3—5 之间");
        }
        LocalDate weekStart = request.weekStart();
        if (weekStart == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "weekStart 不能为空");
        }
        // 契约 V2.3.0：一周计划固定按自然周（周一至周日）生成，非周一会与“本周进度”聚合口径错位
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BizException(ErrorCode.PARAM_ERROR, "weekStart 必须为周一");
        }
        Course course = courseService.getActiveById(request.courseId());
        // 05 §3.7：补漏排序（先修 1—2 跳 × 遗忘紧迫度 × 错因可修复性）与推荐共用同一口径
        RepairPriorityService.RankedCourse ranked =
                repairPriorityService.rank(userId, course.getCourseId());
        List<KnowledgePoint> orderedKps = ranked.knowledgePoints();
        if (orderedKps.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程暂无知识点");
        }
        List<QuestionView> allQuestions = questionBankService.listByCourse(course.getCourseId());
        if (allQuestions.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程暂无题目");
        }
        Map<String, List<QuestionView>> questionsByKp = new LinkedHashMap<>();
        for (KnowledgePoint kp : orderedKps) {
            questionsByKp.put(kp.getKpId(), new ArrayList<>());
        }
        // 一次取回后按知识点分组，避免每个知识点再查一次库（N+1）
        for (QuestionView question : allQuestions) {
            if (question.primaryKpId() != null) {
                questionsByKp.computeIfAbsent(question.primaryKpId(), key -> new ArrayList<>())
                        .add(question);
            }
        }

        LocalDate weekEnd = weekStart.plusDays(6);
        List<StudyPlan> existing = planMapper.selectList(new LambdaQueryWrapper<StudyPlan>()
                .eq(StudyPlan::getUserId, userId)
                .between(StudyPlan::getPlanDate, weekStart, weekEnd)
                .orderByAsc(StudyPlan::getPlanDate));
        Map<LocalDate, StudyPlan> existingByDate = new HashMap<>();
        for (StudyPlan plan : existing) {
            existingByDate.put(plan.getPlanDate(), plan);
        }
        Map<Long, List<StudyPlanItem>> itemsByPlan = loadItems(existing);
        Set<String> usedQids = new HashSet<>();
        for (List<StudyPlanItem> items : itemsByPlan.values()) {
            for (StudyPlanItem item : items) {
                usedQids.add(item.getQId());
            }
        }
        // 冷却：已练题优先跳过（recommend.cooldown-days，与推荐、到期复习共用）；题库不足时 pickQuestions 会兜底放宽
        usedQids.addAll(practiceService.recentPracticedQids(userId, cooldownDays));

        List<PlanView> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            StudyPlan plan = existingByDate.get(date);
            List<StudyPlanItem> items;
            if (plan != null) {
                items = itemsByPlan.getOrDefault(plan.getPlanId(), List.of());
            } else {
                List<QuestionView> picked = pickQuestions(daily, orderedKps, questionsByKp,
                        allQuestions, usedQids);
                if (picked.isEmpty()) {
                    throw new BizException(ErrorCode.NOT_FOUND, "题库题量不足，无法生成计划");
                }
                plan = new StudyPlan();
                plan.setUserId(userId);
                plan.setPlanDate(date);
                plan.setPlanCount(picked.size());
                plan.setDoneCount(0);
                plan.setStatus("PENDING");
                planMapper.insert(plan);
                items = new ArrayList<>();
                int sortNo = 1;
                for (QuestionView question : picked) {
                    StudyPlanItem item = new StudyPlanItem();
                    item.setPlanId(plan.getPlanId());
                    item.setQId(question.qId());
                    item.setKpId(question.primaryKpId());
                    item.setSortNo(sortNo++);
                    item.setIsDone(0);
                    itemMapper.insert(item);
                    items.add(item);
                    usedQids.add(question.qId());
                }
            }
            result.add(toView(plan, items));
        }
        return result;
    }

    /**
     * 打卡：answered=true 写 practice_record(source=PLAN) 并按真实作答记 is_correct；
     * answered=false 仅推进度、不写 practice_record（不计正确率/不入可信闭环）。
     * 同一 clientToken 重放返回成功（duplicate=true）；不同 token 打同一已完成题返回 40900。
     *
     * <p>B3（残余 P1，本轮保留方法级 {@code @Transactional}）：事务在入口即建立 REPEATABLE READ
     * 快照，覆盖到 {@code practiceService.recordPlanCompletion()} 内部的
     * {@code DuplicateKeyException} 幂等回查；并发同 token 双击时，回查在本事务快照内
     * 看不到并发方已提交的首条 practice_record，会误判 40900/50000 并整单回滚。
     * 推荐后续按 audit_reports/Java_05_练习错题与复习.md §5.4 收口：去掉本方法
     * {@code @Transactional}、注入 {@code TransactionTemplate} 只包住写操作，把幂等回查
     * 移到事务结束之后用新快照执行（跨模块需同步改 PracticeService.recordPlanCompletion
     * 与 PlanServiceTest 构造器，超出本轮授权范围）。
     */
    @Transactional(rollbackFor = Exception.class)
    public CheckinResult checkin(Long planId, CheckinRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        StudyPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "计划不存在: " + planId);
        }
        if (!Objects.equals(plan.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该计划");
        }
        // P1-1：服务端日期校验，拒绝提前打卡未来计划 / 超过补卡期限的过期计划刷完成率。
        // planDate 取自库里的 StudyPlan，绝不接受客户端日期参数；时区统一用类内 ZONE。
        // planDate 为 null 视为历史数据（旧计划未落日期），放行以兼容存量。
        LocalDate planDate = plan.getPlanDate();
        if (planDate != null) {
            LocalDate today = LocalDate.now(ZONE);
            if (planDate.isAfter(today)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "不能为未来的计划打卡");
            }
            if (planDate.isBefore(today.minusDays(7))) {
                throw new BizException(ErrorCode.PARAM_ERROR, "超过补打卡期限（7 天）");
            }
        }
        StudyPlanItem item = itemMapper.selectOne(new LambdaQueryWrapper<StudyPlanItem>()
                .eq(StudyPlanItem::getPlanId, planId)
                .eq(StudyPlanItem::getQId, request.qId())
                .last("LIMIT 1"));
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该计划不含此题目: " + request.qId());
        }
        boolean answered = Boolean.TRUE.equals(request.answered());
        validateCheckin(answered, request);
        return answered
                ? answeredCheckin(plan, item, request, userId)
                : recordOnlyCheckin(plan, item, request, userId);
    }

    private void validateCheckin(boolean answered, CheckinRequest request) {
        if (!answered && (request.myAnswer() != null || request.isCorrect() != null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅记录进度时不能提交答案或对错");
        }
        if (answered) {
            if (request.myAnswer() == null || request.myAnswer().isBlank()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "已作答打卡必须提交 myAnswer");
            }
            // 客观题由服务端判分，isCorrect 可不传；主观题是否必填在 answeredCheckin 里按题型判断
        }
    }

    /** 仅记录进度：不写 practice_record，只把明细置完成（不计正确率/样本）。 */
    private CheckinResult recordOnlyCheckin(StudyPlan plan, StudyPlanItem item,
                                            CheckinRequest request, long userId) {
        long now = System.currentTimeMillis();
        // P1-2：key = userId:clientToken；clientToken 由 @NotBlank 保证非空，null 仅可能来自绕过 @Valid 的内部调用
        String cacheKey = request.clientToken() == null ? null
                : userId + ":" + request.clientToken();
        int rows = itemMapper.update(null, new LambdaUpdateWrapper<StudyPlanItem>()
                .eq(StudyPlanItem::getItemId, item.getItemId())
                .eq(StudyPlanItem::getIsDone, 0)
                .set(StudyPlanItem::getIsDone, 1));
        if (rows == 0) {
            // 同 clientToken 重放：返回首次结果（duplicate=true），不重复写（对齐契约 docs/01:34）。
            // 不同 token / 无 token 打同一已完成题仍是重复提交，按契约 40900。
            CheckinResult replay = lookupRecordOnlyIdempotent(cacheKey, request.qId(), now);
            if (replay != null) {
                return replay;
            }
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该题已打卡");
        }
        planMapper.refreshProgress(plan.getPlanId());
        CheckinResult result = buildResult(plan, request.qId(), false, null, null, false, true, null);
        storeRecordOnlyIdempotent(cacheKey, result, now);
        return result;
    }

    /** 查同 token 重放：命中未过期且 qId 一致则返回「首次结果 + duplicate=true」，否则返回 null。 */
    private CheckinResult lookupRecordOnlyIdempotent(String key, String qId, long now) {
        if (key == null) {
            return null;
        }
        IdempotencyEntry entry = recordOnlyIdempotency.get(key);
        if (entry == null) {
            return null;
        }
        if (now - entry.createdAtMillis > RECORD_ONLY_IDEMPOTENCY_TTL_MS) {
            recordOnlyIdempotency.remove(key, entry);
            return null;
        }
        // 同一 clientToken 被用于其它题目时不按幂等处理，避免跨题吞掉重复提交
        if (!Objects.equals(entry.result.qId(), qId)) {
            return null;
        }
        CheckinResult first = entry.result;
        return new CheckinResult(first.planId(), first.qId(), first.doneCount(),
                first.planCount(), first.verified(), first.isCorrect(),
                first.gradingMode(), first.practiceId(), true);
    }

    /** 登记首次结果：computeIfAbsent 保证并发下只保留首次；写入前按容量做过期/最老淘汰。 */
    private void storeRecordOnlyIdempotent(String key, CheckinResult result, long now) {
        if (key == null) {
            return;
        }
        if (recordOnlyIdempotency.size() >= RECORD_ONLY_IDEMPOTENCY_MAX_SIZE) {
            evictRecordOnlyIdempotent(now);
        }
        recordOnlyIdempotency.computeIfAbsent(key, k -> new IdempotencyEntry(result, now));
    }

    /** 有界淘汰：先清过期项；仍超上限则按登记时间删最老的一批，保证缓存不无限增长。 */
    private void evictRecordOnlyIdempotent(long now) {
        recordOnlyIdempotency.forEach((key, entry) -> {
            if (now - entry.createdAtMillis > RECORD_ONLY_IDEMPOTENCY_TTL_MS) {
                recordOnlyIdempotency.remove(key, entry);
            }
        });
        int over = recordOnlyIdempotency.size() - RECORD_ONLY_IDEMPOTENCY_MAX_SIZE;
        if (over <= 0) {
            return;
        }
        recordOnlyIdempotency.entrySet().stream()
                .sorted(Comparator.comparingLong(
                        (Map.Entry<String, IdempotencyEntry> e) -> e.getValue().createdAtMillis))
                .limit((long) over + 1)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(recordOnlyIdempotency::remove);
    }

    /** 幂等缓存条目：首次回执 + 登记时间（毫秒，仅用于进程内相对过期判断）。 */
    private static final class IdempotencyEntry {
        private final CheckinResult result;
        private final long createdAtMillis;

        private IdempotencyEntry(CheckinResult result, long createdAtMillis) {
            this.result = result;
            this.createdAtMillis = createdAtMillis;
        }
    }

    /** 已作答打卡：客观题服务端判分、主观题自评；幂等命中返回首次结果。 */
    private CheckinResult answeredCheckin(StudyPlan plan, StudyPlanItem item,
                                          CheckinRequest request, long userId) {
        PracticeService.PlanCompletion existing =
                practiceService.findCompletionByToken(userId, request.clientToken());
        if (existing != null && (!Objects.equals(existing.qId(), request.qId())
                || !"PLAN".equals(existing.source()))) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "clientToken 已用于其他作答");
        }
        if (item.getIsDone() != null && item.getIsDone() == 1) {
            if (existing != null) {
                AnswerGrader.Grading grading = grade(item, request);
                return buildResult(plan, request.qId(), true, existing.isCorrect(),
                        existing.practiceId(), true, false, grading.mode());
            }
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该题已打卡");
        }

        QuestionBank question = questionBankService.getById(item.getQId());
        AnswerGrader.Grading grading = AnswerGrader.grade(question, request.myAnswer(),
                request.isCorrect());
        if (AnswerGrader.SELF.equals(grading.mode()) && request.isCorrect() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "主观题必须提交自评结果");
        }
        PracticeService.PlanCompletion completion = existing;
        if (completion == null) {
            // 与自主练习共用错因推断：答错不落 null，客观题不信任客户端错因
            String errorType = practiceService.resolveErrorType(userId, question,
                    request.myAnswer(), grading.correct(), request.errorType(), item.getKpId());
            completion = practiceService.recordPlanCompletion(userId, item.getQId(), item.getKpId(),
                    request.clientToken(), request.myAnswer(), grading.correct(),
                    // 计划打卡契约不含 startToken（无服务端计时），客户端自报耗时不可信，一律落 null；
                    // 打卡成功、幂等、判分与 errorType 推断不受影响，字段保留仅为兼容旧客户端。
                    null, errorType);
        }
        int rows = itemMapper.update(null, new LambdaUpdateWrapper<StudyPlanItem>()
                .eq(StudyPlanItem::getItemId, item.getItemId())
                .eq(StudyPlanItem::getIsDone, 0)
                .set(StudyPlanItem::getIsDone, 1)
                .set(StudyPlanItem::getPracticeId, completion.practiceId()));
        if (rows == 0) {
            StudyPlanItem current = itemMapper.selectById(item.getItemId());
            if (current != null && current.getIsDone() != null
                    && current.getIsDone() == 1 && existing != null) {
                return buildResult(plan, request.qId(), true, existing.isCorrect(),
                        existing.practiceId(), true, false, grading.mode());
            }
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT, "该题已打卡");
        }
        // 原子重算完成数与状态，避免并发打卡把 done_count 写小
        planMapper.refreshProgress(plan.getPlanId());
        return buildResult(plan, request.qId(), true,
                existing != null ? existing.isCorrect() : grading.correct(),
                completion.practiceId(), existing != null, true, grading.mode());
    }

    /** 客观题取题目 answerText 服务端判分；主观题保留客户端自评。 */
    private AnswerGrader.Grading grade(StudyPlanItem item, CheckinRequest request) {
        QuestionBank question = questionBankService.getById(item.getQId());
        return AnswerGrader.grade(question, request.myAnswer(), request.isCorrect());
    }

    /** 回执用内存计划对象推导完成数，避免每次都再 SELECT 一次。 */
    private CheckinResult buildResult(StudyPlan plan, String qId, boolean verified,
                                      Boolean isCorrect, Long practiceId,
                                      boolean duplicate, boolean incremented, String gradingMode) {
        int total = plan.getPlanCount() == null ? 0 : plan.getPlanCount();
        int base = plan.getDoneCount() == null ? 0 : plan.getDoneCount();
        int done = Math.max(0, base + (incremented ? 1 : 0));
        if (total > 0) {
            done = Math.min(done, total);
        }
        return new CheckinResult(plan.getPlanId(), qId, done, total, verified, isCorrect,
                gradingMode, practiceId, duplicate);
    }

    private List<PlanView> toViews(List<StudyPlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<Long, List<StudyPlanItem>> itemsByPlan = loadItems(plans);
        List<PlanView> result = new ArrayList<>(plans.size());
        for (StudyPlan plan : plans) {
            result.add(toView(plan, itemsByPlan.getOrDefault(plan.getPlanId(), List.of())));
        }
        return result;
    }

    private Map<Long, List<StudyPlanItem>> loadItems(List<StudyPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return Map.of();
        }
        List<Long> planIds = plans.stream().map(StudyPlan::getPlanId).toList();
        List<StudyPlanItem> items = itemMapper.selectList(new LambdaQueryWrapper<StudyPlanItem>()
                .in(StudyPlanItem::getPlanId, planIds)
                .orderByAsc(StudyPlanItem::getSortNo));
        Map<Long, List<StudyPlanItem>> grouped = new HashMap<>();
        for (StudyPlanItem item : items) {
            grouped.computeIfAbsent(item.getPlanId(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private PlanView toView(StudyPlan plan, List<StudyPlanItem> items) {
        List<PlanView.PlanItemView> itemViews = items.stream()
                .sorted(Comparator.comparing(item -> item.getSortNo() == null ? 0 : item.getSortNo()))
                .map(item -> new PlanView.PlanItemView(item.getItemId(), item.getQId(),
                        item.getKpId(), item.getSortNo(),
                        item.getIsDone() != null && item.getIsDone() == 1))
                .toList();
        // 没有定时任务扫描过期计划：读取时派生 OVERDUE，避免前端“已逾期”成为死分支
        String status = plan.getStatus();
        int doneCount = plan.getDoneCount() == null ? 0 : plan.getDoneCount();
        int planCount = plan.getPlanCount() == null ? 0 : plan.getPlanCount();
        if ("PENDING".equals(status) && plan.getPlanDate() != null
                && plan.getPlanDate().isBefore(LocalDate.now(ZONE)) && doneCount < planCount) {
            status = "OVERDUE";
        }
        return new PlanView(plan.getPlanId(), plan.getPlanDate(), plan.getPlanCount(),
                plan.getDoneCount(), status, itemViews);
    }

    /**
     * 选题：先薄弱知识点，再补全课程题；同一周已用题尽量跳过，题量不足时允许跨日重复。
     */
    private List<QuestionView> pickQuestions(int count,
                                             List<KnowledgePoint> orderedKps,
                                             Map<String, List<QuestionView>> questionsByKp,
                                             List<QuestionView> allQuestions,
                                             Set<String> usedQids) {
        List<QuestionView> picked = new ArrayList<>(count);
        Set<String> pickedQids = new HashSet<>();
        for (KnowledgePoint kp : orderedKps) {
            for (QuestionView question : questionsByKp.getOrDefault(kp.getKpId(), List.of())) {
                if (picked.size() >= count) {
                    return picked;
                }
                if (!usedQids.contains(question.qId()) && pickedQids.add(question.qId())) {
                    picked.add(question);
                }
            }
        }
        for (QuestionView question : allQuestions) {
            if (picked.size() >= count) {
                return picked;
            }
            if (!usedQids.contains(question.qId()) && pickedQids.add(question.qId())) {
                picked.add(question);
            }
        }
        // 题库确实不足：允许跨日重复，但同一天不重复
        for (QuestionView question : allQuestions) {
            if (picked.size() >= count) {
                return picked;
            }
            if (pickedQids.add(question.qId())) {
                picked.add(question);
            }
        }
        return picked;
    }
}

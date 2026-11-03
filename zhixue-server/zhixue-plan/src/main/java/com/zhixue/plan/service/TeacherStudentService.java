package com.zhixue.plan.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.api.ErrorCode;
import com.zhixue.common.exception.BizException;
import com.zhixue.course.entity.Course;
import com.zhixue.course.entity.KnowledgePoint;
import com.zhixue.course.entity.QuestionBank;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.KnowledgePointService;
import com.zhixue.course.service.QuestionBankService;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserPracticeAgg;
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.diagnosis.service.MasteryCalculator;
import com.zhixue.common.util.CsvWriter;
import com.zhixue.common.util.NameMasker;
import com.zhixue.plan.dto.TeacherStudentViews;
import com.zhixue.plan.dto.UserPlanAgg;
import com.zhixue.plan.mapper.StudyPlanMapper;
import com.zhixue.user.service.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师查看学生学习记录（契约 C12）。
 *
 * <p>学生集合 = 在该课程题目上有作答记录的学生（无 course_student 表，复用 practice_record）。
 * 权限：TEACHER 且课程归属教师；姓名脱敏；不返回图片/OCR 原文。</p>
 *
 * <p>性能（2026-10-31 二次审核修复，整合为单一实现）：
 * 列表与摘要一律走 SQL 聚合（GROUP BY user_id / user_id+kp_id），JVM 只接收
 * “每学生一行 / 每学生-知识点一行”的有界结果；排序（含 masteryAsc）在全局聚合结果上完成，
 * 修正“只对当前页排序”的全局顺序错误。明细分页使用参数绑定的 LIMIT/OFFSET。</p>
 */
@Service
public class TeacherStudentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ERROR_LIMIT = 20;
    private static final List<String> SORTS = List.of("lastActiveDesc", "masteryAsc", "practiceDesc");

    private final CourseService courseService;
    private final QuestionBankService questionBankService;
    private final KnowledgePointService knowledgePointService;
    private final PracticeRecordMapper practiceRecordMapper;
    private final StudentErrorRecordMapper errorRecordMapper;
    private final StudyPlanMapper studyPlanMapper;
    private final UserQueryService userQueryService;
    private final MasteryCalculator masteryCalculator;
    private final int minSamples;
    private final double halfLifeDays;
    /** 聚合时间窗（天）：与掌握度半衰期共用配置组，窗口外历史不实时算 */
    private final int aggregateWindowDays;

    public TeacherStudentService(CourseService courseService,
                                 QuestionBankService questionBankService,
                                 KnowledgePointService knowledgePointService,
                                 PracticeRecordMapper practiceRecordMapper,
                                 StudentErrorRecordMapper errorRecordMapper,
                                 StudyPlanMapper studyPlanMapper,
                                 UserQueryService userQueryService,
                                 @Value("${mastery.half-life-days:30}") double halfLifeDays,
                                 @Value("${mastery.min-samples:3}") int minSamples,
                                 @Value("${mastery.aggregate-window-days:180}") int aggregateWindowDays) {
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.knowledgePointService = knowledgePointService;
        this.practiceRecordMapper = practiceRecordMapper;
        this.errorRecordMapper = errorRecordMapper;
        this.studyPlanMapper = studyPlanMapper;
        this.userQueryService = userQueryService;
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
        this.masteryCalculator = new MasteryCalculator(this.halfLifeDays);
        this.minSamples = Math.max(1, minSamples);
        this.aggregateWindowDays = Math.max(1, aggregateWindowDays);
    }

    /** 聚合窗口起点：教师端全课程聚合只扫窗口内记录，跨学年历史走快照。 */
    private LocalDateTime aggregateSince() {
        return LocalDateTime.now(ZONE).minusDays(aggregateWindowDays);
    }

    /** 课程学生聚合列表。 */
    public TeacherStudentViews.Page page(long courseId, int page, int size, String sort) {
        StpUtil.checkRole("TEACHER");
        validatePage(page, size);
        String sortKey = normalizeSort(sort);
        List<TeacherStudentViews.StudentItem> items = aggregateAll(courseId, sortKey);
        long total = items.size();
        int fromIndex = Math.min(items.size(), (page - 1) * size);
        int toIndex = Math.min(items.size(), fromIndex + size);
        return new TeacherStudentViews.Page(page, size, total,
                new ArrayList<>(items.subList(fromIndex, toIndex)));
    }

    /**
     * 班级学情 CSV 导出（学期材料）：与列表同一聚合与排序，导出完整筛选结果而非当前分页。
     * 权限与列表一致：TEACHER + 课程归属；姓名脱敏同口径。
     */
    public byte[] exportCsv(long courseId, String sort) {
        StpUtil.checkRole("TEACHER");
        String sortKey = normalizeSort(sort);
        List<TeacherStudentViews.StudentItem> items = aggregateAll(courseId, sortKey);
        List<List<String>> rows = new ArrayList<>(items.size());
        for (TeacherStudentViews.StudentItem item : items) {
            rows.add(List.of(
                    String.valueOf(item.userId()),
                    item.displayName(),
                    str(item.practiceCount()),
                    item.correctRate() == null ? "" : String.valueOf(item.correctRate()),
                    item.avgCostSeconds() == null ? "" : String.valueOf(item.avgCostSeconds()),
                    item.avgMastery() == null ? "" : String.valueOf(item.avgMastery()),
                    str(item.errorCount()),
                    str(item.planDone()),
                    str(item.planTotal()),
                    item.lastActiveAt() == null ? "" : item.lastActiveAt()));
        }
        return CsvWriter.write(CLASS_CSV_HEADER, rows);
    }

    private static final List<String> CLASS_CSV_HEADER = List.of(
            "学生ID", "姓名（脱敏）", "练习次数", "正确率", "平均耗时（秒）",
            "平均掌握度", "错题数", "本周完成题数", "本周计划题数", "最近活跃时间");

    private String normalizeSort(String sort) {
        String sortKey = sort == null || sort.isBlank() ? "lastActiveDesc" : sort.trim();
        if (!SORTS.contains(sortKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "sort 仅允许 lastActiveDesc/masteryAsc/practiceDesc");
        }
        return sortKey;
    }

    /** 全量聚合（列表分页与 CSV 导出共用，保证两边口径一致）。 */
    private List<TeacherStudentViews.StudentItem> aggregateAll(long courseId, String sortKey) {
        ensureOwnership(courseId);
        List<String> qIds = questionBankService.listQidsByCourse(courseId);
        if (qIds.isEmpty()) {
            return List.of();
        }
        List<UserPracticeAgg> aggregates = practiceRecordMapper.aggregateByUser(qIds, null,
                aggregateSince());
        if (aggregates.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = aggregates.stream().map(UserPracticeAgg::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<UserKpAgg>> kpByUser = practiceRecordMapper
                .aggregateByUserKp(qIds, userIds, halfLifeDays, LocalDateTime.now(ZONE),
                        aggregateSince()).stream()
                .filter(item -> item.getUserId() != null)
                .collect(Collectors.groupingBy(UserKpAgg::getUserId));
        Map<Long, int[]> planProgress = loadPlanProgress(userIds);
        Map<Long, String> names = userQueryService.findDisplayNames(userIds);

        List<TeacherStudentViews.StudentItem> items = new ArrayList<>(aggregates.size());
        for (UserPracticeAgg aggregate : aggregates) {
            Long userId = aggregate.getUserId();
            if (userId == null) {
                continue;
            }
            items.add(buildItem(userId, aggregate, kpByUser.getOrDefault(userId, List.of()),
                    NameMasker.mask(names.get(userId)), planProgress.getOrDefault(userId, new int[2])));
        }
        items.sort(comparator(sortKey));
        return items;
    }

    private static String str(int value) {
        return String.valueOf(value);
    }

    /** 单个学生学习记录详情。 */
    public TeacherStudentViews.LearningRecord learningRecords(long courseId, long userId,
                                                              LocalDate from, LocalDate to,
                                                              int page, int size) {
        StpUtil.checkRole("TEACHER");
        validatePage(page, size);
        ensureOwnership(courseId);
        List<String> qIds = questionBankService.listQidsByCourse(courseId);
        if (qIds.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "该学生在本课程暂无学习记录");
        }
        List<Long> single = List.of(userId);
        List<UserPracticeAgg> aggregateList = practiceRecordMapper.aggregateByUser(qIds, single,
                aggregateSince());
        if (aggregateList.isEmpty() || aggregateList.get(0).getPracticeCount() == null
                || aggregateList.get(0).getPracticeCount() <= 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "该学生在本课程暂无学习记录");
        }
        UserPracticeAgg aggregate = aggregateList.get(0);
        List<UserKpAgg> kpAggs = practiceRecordMapper.aggregateByUserKp(
                qIds, single, halfLifeDays, LocalDateTime.now(ZONE), aggregateSince());
        Map<String, KnowledgePoint> kpById = knowledgePointService.findByIds(courseKpIds(courseId));
        Map<Long, String> names = userQueryService.findDisplayNames(single);
        Map<Long, int[]> planProgress = loadPlanProgress(Set.of(userId));
        TeacherStudentViews.StudentItem summary = buildItem(userId, aggregate, kpAggs,
                mask(names.get(userId)), planProgress.getOrDefault(userId, new int[2]));

        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.atTime(23, 59, 59);
        long total = practiceRecordMapper.countByUserPaged(userId, qIds, fromTime, toTime);
        List<PracticeRecord> rows = total == 0 ? List.of()
                : practiceRecordMapper.selectByUserPaged(userId, qIds, fromTime, toTime,
                size, (page - 1) * size);
        Set<String> pageQIds = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row.getQId() != null && !row.getQId().isBlank()) {
                pageQIds.add(row.getQId());
            }
        });
        Map<String, QuestionBank> questionById = pageQIds.isEmpty() ? Map.of()
                : questionBankService.findByIds(pageQIds);
        List<TeacherStudentViews.PracticeItem> practices = new ArrayList<>(rows.size());
        for (PracticeRecord row : rows) {
            QuestionBank question = questionById.get(row.getQId());
            KnowledgePoint kp = row.getKpId() == null ? null : kpById.get(row.getKpId());
            practices.add(new TeacherStudentViews.PracticeItem(
                    row.getPracticeId(), row.getQId(),
                    question == null ? null : question.getQuestionText(),
                    row.getKpId(), kp == null ? null : kp.getName(),
                    row.getIsCorrect() == null ? null : row.getIsCorrect() == 1,
                    row.getMyAnswer(),
                    question == null ? null : question.getAnswerText(),
                    row.getCostSeconds(), row.getErrorType(), row.getSource(),
                    row.getCreatedAt() == null ? null : DATE_TIME.format(row.getCreatedAt())));
        }

        List<TeacherStudentViews.ErrorItem> errors = loadErrors(userId, kpById);
        List<TeacherStudentViews.KpMastery> kpMastery =
                buildKpMastery(kpAggs, kpById);
        return new TeacherStudentViews.LearningRecord(summary,
                new TeacherStudentViews.PracticePage(page, size, total, practices),
                errors, kpMastery);
    }

    private void validatePage(int page, int size) {
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "page 必须大于 0");
        }
        if (size < 1 || size > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR, "size 必须在 1—50 之间");
        }
    }

    private Course ensureOwnership(long courseId) {
        if (courseId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "courseId 非法");
        }
        Course course = courseService.getActiveById(courseId);
        long teacherId = StpUtil.getLoginIdAsLong();
        if (course.getTeacherId() != null && !Objects.equals(course.getTeacherId(), teacherId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看其他教师的课程");
        }
        return course;
    }

    private List<String> courseKpIds(long courseId) {
        return courseService.listActiveKnowledgePoints(courseId).stream()
                .map(KnowledgePoint::getKpId)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<Long, int[]> loadPlanProgress(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        LocalDate monday = LocalDate.now(ZONE).with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        List<UserPlanAgg> rows = studyPlanMapper.aggregateProgress(userIds, monday, sunday);
        Map<Long, int[]> result = new HashMap<>();
        for (UserPlanAgg row : rows) {
            if (row.getUserId() == null) {
                continue;
            }
            result.put(row.getUserId(), new int[]{
                    row.getPlanTotal() == null ? 0 : row.getPlanTotal(),
                    row.getPlanDone() == null ? 0 : row.getPlanDone()});
        }
        return result;
    }

    private TeacherStudentViews.StudentItem buildItem(long userId, UserPracticeAgg aggregate,
                                                      List<UserKpAgg> kpAggs,
                                                      String displayName, int[] plan) {
        int practiceCount = aggregate.getPracticeCount() == null ? 0 : aggregate.getPracticeCount();
        int wrongCount = aggregate.getWrongCount() == null ? 0 : aggregate.getWrongCount();
        int errorQidCount = aggregate.getErrorQidCount() == null ? 0 : aggregate.getErrorQidCount();
        Double correctRate = practiceCount >= minSamples
                ? round3((practiceCount - wrongCount) / (double) practiceCount) : null;
        Integer avgCostSeconds = aggregate.getAvgCostSeconds() == null ? null
                : (int) Math.round(aggregate.getAvgCostSeconds());
        Double avgMastery = null;
        if (!kpAggs.isEmpty()) {
            double sum = 0;
            for (UserKpAgg kp : kpAggs) {
                // 与学生端完全同源：SQL 已按 (user_id, kp_id) 逐事件衰减求和，
                // 不再用 per-user lastActiveAt 当所有知识点的衰减锚。
                sum += masteryCalculator.mastery(
                        kp.getSuccessWeight() == null ? 0.0 : kp.getSuccessWeight(),
                        kp.getSeveritySum() == null ? 0.0 : kp.getSeveritySum());
            }
            avgMastery = round3(sum / kpAggs.size());
        }
        return new TeacherStudentViews.StudentItem(userId, displayName, practiceCount,
                correctRate, avgCostSeconds, avgMastery, errorQidCount,
                // StudentItem 字段顺序是 planDone、planTotal；int[] 是 [total, done]，不要写反
                plan[1], plan[0],
                aggregate.getLastActiveAt() == null ? null : DATE_TIME.format(aggregate.getLastActiveAt()));
    }

    private List<TeacherStudentViews.ErrorItem> loadErrors(long userId,
                                                           Map<String, KnowledgePoint> kpById) {
        List<String> kpIds = new ArrayList<>(kpById.keySet());
        if (kpIds.isEmpty()) {
            return List.of();
        }
        List<StudentErrorRecord> records = errorRecordMapper.selectList(
                new LambdaQueryWrapper<StudentErrorRecord>()
                        .eq(StudentErrorRecord::getUserId, userId)
                        .in(StudentErrorRecord::getKpId, kpIds)
                        .orderByDesc(StudentErrorRecord::getCreatedAt)
                        .last("LIMIT " + ERROR_LIMIT));
        List<TeacherStudentViews.ErrorItem> result = new ArrayList<>(records.size());
        for (StudentErrorRecord record : records) {
            KnowledgePoint kp = record.getKpId() == null ? null : kpById.get(record.getKpId());
            result.add(new TeacherStudentViews.ErrorItem(
                    record.getLogId(), record.getQId(), record.getKpId(),
                    kp == null ? null : kp.getName(), record.getStatus(), record.getErrorType(),
                    record.getOcrConfidence() == null ? null : record.getOcrConfidence().doubleValue(),
                    record.getCreatedAt() == null ? null : DATE_TIME.format(record.getCreatedAt())));
        }
        return result;
    }

    private List<TeacherStudentViews.KpMastery> buildKpMastery(List<UserKpAgg> kpAggs,
                                                               Map<String, KnowledgePoint> kpById) {
        List<TeacherStudentViews.KpMastery> result = new ArrayList<>();
        for (UserKpAgg aggregate : kpAggs) {
            String kpId = aggregate.getKpId();
            if (kpId == null || kpId.isBlank()) {
                continue;
            }
            KnowledgePoint kp = kpById.get(kpId);
            int events = aggregate.getEvents() == null ? 0 : aggregate.getEvents();
            int errors = aggregate.getErrorCount() == null ? 0 : aggregate.getErrorCount();
            // 与学生端 /api/portraits/me 同一入口：successWeight、severitySum 均由 SQL 逐事件衰减
            result.add(new TeacherStudentViews.KpMastery(kpId,
                    kp == null ? null : kp.getName(),
                    round3(masteryCalculator.mastery(
                            aggregate.getSuccessWeight() == null ? 0.0 : aggregate.getSuccessWeight(),
                            aggregate.getSeveritySum() == null ? 0.0 : aggregate.getSeveritySum())),
                    events, errors));
        }
        result.sort(Comparator.comparingDouble(TeacherStudentViews.KpMastery::mastery));
        return result;
    }

    private Comparator<TeacherStudentViews.StudentItem> comparator(String sort) {
        return switch (sort) {
            case "masteryAsc" -> Comparator.comparing(
                    TeacherStudentViews.StudentItem::avgMastery,
                    Comparator.nullsLast(Double::compareTo));
            case "practiceDesc" -> Comparator.comparingInt(
                    TeacherStudentViews.StudentItem::practiceCount).reversed();
            default -> Comparator.comparing(
                    TeacherStudentViews.StudentItem::lastActiveAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    /** 姓名脱敏：单一事实源在 {@link NameMasker}（教师端列表与导出共用），此处保留委托供既有调用。 */
    static String mask(String name) {
        return NameMasker.mask(name);
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

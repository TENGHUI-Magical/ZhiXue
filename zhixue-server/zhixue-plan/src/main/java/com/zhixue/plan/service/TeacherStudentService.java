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
import com.zhixue.diagnosis.entity.PracticeRecord;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import com.zhixue.diagnosis.service.MasteryCalculator;
import com.zhixue.plan.dto.TeacherStudentViews;
import com.zhixue.plan.entity.StudyPlan;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 教师查看学生学习记录（契约 C12）。
 *
 * <p>学生集合 = 在该课程题目上有作答记录的学生（无 course_student 表，复用 practice_record）。
 * 权限：TEACHER 且课程归属教师；姓名脱敏；不返回图片/OCR 原文。</p>
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

    public TeacherStudentService(CourseService courseService,
                                 QuestionBankService questionBankService,
                                 KnowledgePointService knowledgePointService,
                                 PracticeRecordMapper practiceRecordMapper,
                                 StudentErrorRecordMapper errorRecordMapper,
                                 StudyPlanMapper studyPlanMapper,
                                 UserQueryService userQueryService,
                                 @Value("${mastery.half-life-days:30}") double halfLifeDays,
                                 @Value("${mastery.min-samples:3}") int minSamples) {
        this.courseService = courseService;
        this.questionBankService = questionBankService;
        this.knowledgePointService = knowledgePointService;
        this.practiceRecordMapper = practiceRecordMapper;
        this.errorRecordMapper = errorRecordMapper;
        this.studyPlanMapper = studyPlanMapper;
        this.userQueryService = userQueryService;
        this.masteryCalculator = new MasteryCalculator(halfLifeDays);
        this.minSamples = Math.max(1, minSamples);
    }

    /** 课程学生聚合列表。 */
    public TeacherStudentViews.Page page(long courseId, int page, int size, String sort) {
        StpUtil.checkRole("TEACHER");
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "page 必须大于 0");
        }
        if (size < 1 || size > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR, "size 必须在 1—50 之间");
        }
        String sortKey = sort == null || sort.isBlank() ? "lastActiveDesc" : sort.trim();
        if (!SORTS.contains(sortKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "sort 仅允许 lastActiveDesc/masteryAsc/practiceDesc");
        }
        Course course = ensureOwnership(courseId);
        List<String> qIds = questionBankService.listQidsByCourse(courseId);
        if (qIds.isEmpty()) {
            return new TeacherStudentViews.Page(page, size, 0, List.of());
        }
        List<PracticeRecord> records = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .select(PracticeRecord::getUserId, PracticeRecord::getQId,
                                PracticeRecord::getKpId, PracticeRecord::getIsCorrect,
                                PracticeRecord::getErrorType, PracticeRecord::getCostSeconds,
                                PracticeRecord::getCreatedAt)
                        .in(PracticeRecord::getQId, qIds));
        Map<Long, List<PracticeRecord>> byUser = new HashMap<>();
        for (PracticeRecord record : records) {
            if (record.getUserId() != null) {
                byUser.computeIfAbsent(record.getUserId(), key -> new ArrayList<>()).add(record);
            }
        }
        if (byUser.isEmpty()) {
            return new TeacherStudentViews.Page(page, size, 0, List.of());
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<Long, String> names = userQueryService.findDisplayNames(byUser.keySet());
        Map<Long, int[]> planProgress = loadPlanProgress(byUser.keySet());
        List<TeacherStudentViews.StudentItem> items = new ArrayList<>(byUser.size());
        for (Map.Entry<Long, List<PracticeRecord>> entry : byUser.entrySet()) {
            items.add(buildItem(entry.getKey(), entry.getValue(),
                    mask(names.get(entry.getKey())),
                    planProgress.getOrDefault(entry.getKey(), new int[2]), now));
        }
        items.sort(comparator(sortKey));

        long total = items.size();
        int fromIndex = Math.min(items.size(), (page - 1) * size);
        int toIndex = Math.min(items.size(), fromIndex + size);
        return new TeacherStudentViews.Page(page, size, total,
                new ArrayList<>(items.subList(fromIndex, toIndex)));
    }

    /** 单个学生学习记录详情。 */
    public TeacherStudentViews.LearningRecord learningRecords(long courseId, long userId,
                                                              LocalDate from, LocalDate to,
                                                              int page, int size) {
        StpUtil.checkRole("TEACHER");
        if (page < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "page 必须大于 0");
        }
        if (size < 1 || size > 50) {
            throw new BizException(ErrorCode.PARAM_ERROR, "size 必须在 1—50 之间");
        }
        ensureOwnership(courseId);
        List<String> qIds = questionBankService.listQidsByCourse(courseId);
        if (qIds.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "该学生在本课程暂无学习记录");
        }
        List<PracticeRecord> all = practiceRecordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .in(PracticeRecord::getQId, qIds));
        if (all.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "该学生在本课程暂无学习记录");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<String, KnowledgePoint> kpById = knowledgePointService.findByIds(courseKpIds(courseId));
        Map<Long, String> names = userQueryService.findDisplayNames(List.of(userId));
        Map<Long, int[]> planProgress = loadPlanProgress(Set.of(userId));
        TeacherStudentViews.StudentItem summary = buildItem(userId, all,
                mask(names.get(userId)), planProgress.getOrDefault(userId, new int[2]), now);

        LambdaQueryWrapper<PracticeRecord> query = new LambdaQueryWrapper<PracticeRecord>()
                .eq(PracticeRecord::getUserId, userId)
                .in(PracticeRecord::getQId, qIds);
        if (from != null) {
            query.ge(PracticeRecord::getCreatedAt, from.atStartOfDay());
        }
        if (to != null) {
            query.le(PracticeRecord::getCreatedAt, to.atTime(23, 59, 59));
        }
        query.orderByDesc(PracticeRecord::getCreatedAt).orderByDesc(PracticeRecord::getPracticeId);
        long total = practiceRecordMapper.selectCount(query);
        List<PracticeRecord> rows = total == 0 ? List.of()
                : practiceRecordMapper.selectList(query.last(
                "LIMIT " + size + " OFFSET " + (long) (page - 1) * size));
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
        List<TeacherStudentViews.KpMastery> kpMastery = buildKpMastery(all, kpById, now);
        return new TeacherStudentViews.LearningRecord(summary,
                new TeacherStudentViews.PracticePage(page, size, total, practices),
                errors, kpMastery);
    }

    private Course ensureOwnership(long courseId) {
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
        List<StudyPlan> plans = studyPlanMapper.selectList(new LambdaQueryWrapper<StudyPlan>()
                .in(StudyPlan::getUserId, userIds)
                .ge(StudyPlan::getPlanDate, monday)
                .le(StudyPlan::getPlanDate, sunday));
        Map<Long, int[]> result = new HashMap<>();
        for (StudyPlan plan : plans) {
            if (plan.getUserId() == null) {
                continue;
            }
            int[] arr = result.computeIfAbsent(plan.getUserId(), key -> new int[2]);
            arr[0] += plan.getPlanCount() == null ? 0 : plan.getPlanCount();
            arr[1] += plan.getDoneCount() == null ? 0 : plan.getDoneCount();
        }
        return result;
    }

    private TeacherStudentViews.StudentItem buildItem(long userId, List<PracticeRecord> list,
                                                      String displayName, int[] plan,
                                                      LocalDateTime now) {
        int practiceCount = list.size();
        int wrongCount = 0;
        Set<String> errorQIds = new HashSet<>();
        long costSum = 0;
        int costN = 0;
        Map<String, Integer> kpEvents = new HashMap<>();
        Map<String, Double> kpSeverity = new HashMap<>();
        LocalDateTime lastActive = null;
        for (PracticeRecord record : list) {
            boolean wrong = record.getIsCorrect() != null && record.getIsCorrect() == 0;
            if (wrong) {
                wrongCount++;
                if (record.getQId() != null) {
                    errorQIds.add(record.getQId());
                }
            }
            if (record.getCostSeconds() != null) {
                costSum += record.getCostSeconds();
                costN++;
            }
            String kpId = record.getKpId();
            if (kpId != null && !kpId.isBlank()) {
                kpEvents.merge(kpId, 1, Integer::sum);
                if (wrong && record.getCreatedAt() != null) {
                    kpSeverity.merge(kpId, masteryCalculator.errorWeight(
                            record.getErrorType(), record.getCreatedAt(), now), Double::sum);
                }
            }
            if (record.getCreatedAt() != null
                    && (lastActive == null || record.getCreatedAt().isAfter(lastActive))) {
                lastActive = record.getCreatedAt();
            }
        }
        Double correctRate = practiceCount >= minSamples
                ? round3((practiceCount - wrongCount) / (double) practiceCount) : null;
        Integer avgCostSeconds = costN == 0 ? null : (int) Math.round(costSum / (double) costN);
        Double avgMastery = null;
        if (!kpEvents.isEmpty()) {
            double sum = 0;
            for (Map.Entry<String, Integer> entry : kpEvents.entrySet()) {
                sum += masteryCalculator.mastery(entry.getValue(),
                        kpSeverity.getOrDefault(entry.getKey(), 0.0));
            }
            avgMastery = round3(sum / kpEvents.size());
        }
        return new TeacherStudentViews.StudentItem(userId, displayName, practiceCount,
                correctRate, avgCostSeconds, avgMastery, errorQIds.size(),
                plan[0], plan[1],
                lastActive == null ? null : DATE_TIME.format(lastActive));
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

    private List<TeacherStudentViews.KpMastery> buildKpMastery(List<PracticeRecord> records,
                                                               Map<String, KnowledgePoint> kpById,
                                                               LocalDateTime now) {
        Map<String, Integer> events = new HashMap<>();
        Map<String, Double> severity = new HashMap<>();
        Map<String, Integer> errors = new HashMap<>();
        for (PracticeRecord record : records) {
            String kpId = record.getKpId();
            if (kpId == null || kpId.isBlank()) {
                continue;
            }
            events.merge(kpId, 1, Integer::sum);
            if (record.getIsCorrect() != null && record.getIsCorrect() == 0) {
                errors.merge(kpId, 1, Integer::sum);
                if (record.getCreatedAt() != null) {
                    severity.merge(kpId, masteryCalculator.errorWeight(
                            record.getErrorType(), record.getCreatedAt(), now), Double::sum);
                }
            }
        }
        List<TeacherStudentViews.KpMastery> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : events.entrySet()) {
            String kpId = entry.getKey();
            KnowledgePoint kp = kpById.get(kpId);
            double mastery = masteryCalculator.mastery(entry.getValue(),
                    severity.getOrDefault(kpId, 0.0));
            result.add(new TeacherStudentViews.KpMastery(kpId,
                    kp == null ? null : kp.getName(), round3(mastery),
                    entry.getValue(), errors.getOrDefault(kpId, 0)));
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

    private String mask(String name) {
        if (name == null || name.isBlank()) {
            return "学生";
        }
        String value = name.trim();
        return value.length() <= 1 ? value : value.charAt(0) + "*";
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

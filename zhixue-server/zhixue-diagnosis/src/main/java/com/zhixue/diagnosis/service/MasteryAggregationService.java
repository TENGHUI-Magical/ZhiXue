package com.zhixue.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.diagnosis.dto.UserKpAgg;
import com.zhixue.diagnosis.dto.UserQid;
import com.zhixue.diagnosis.entity.StudentErrorRecord;
import com.zhixue.diagnosis.mapper.PracticeRecordMapper;
import com.zhixue.diagnosis.mapper.StudentErrorRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 掌握度唯一聚合入口（docs/05 §2）。
 *
 * <p><b>口径（唯一事实源）</b>：训练事件 = 窗口内的 {@code practice_record}
 * + 无对应练习记录的 CONFIRMED 上传错题（按 qId 互斥）；同一学生、同一题目、同一自然日
 * 只计最优 1 次（答对优先，同日全错取当天最后一次），避免同日重复作答刷分。
 * 学生画像、班级画像、教师端学生列表、到期复习全部调用本服务，禁止任何 Service 再自建一套。</p>
 *
 * <p>掌握度由 {@link MasteryCalculator}（Beta(2,2) + 30 天半衰）计算；本服务额外输出
 * <b>预计保留率</b> {@code 0.5^(距上次答对天数/半衰期)}，与掌握度并列，用于区分
 * “停练导致掌握度回落 0.5”和“真的记住了”。从未答对时保留率为 0。</p>
 */
@Service
public class MasteryAggregationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String STATUS_CONFIRMED = "CONFIRMED";

    private final PracticeRecordMapper practiceRecordMapper;
    private final StudentErrorRecordMapper errorRecordMapper;
    private final MasteryCalculator masteryCalculator;
    private final double halfLifeDays;
    private final int aggregateWindowDays;

    public MasteryAggregationService(PracticeRecordMapper practiceRecordMapper,
                                     StudentErrorRecordMapper errorRecordMapper,
                                     @Value("${mastery.half-life-days:30}") double halfLifeDays,
                                     @Value("${mastery.aggregate-window-days:180}") int aggregateWindowDays) {
        this.practiceRecordMapper = practiceRecordMapper;
        this.errorRecordMapper = errorRecordMapper;
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
        this.masteryCalculator = new MasteryCalculator(this.halfLifeDays);
        this.aggregateWindowDays = Math.max(1, aggregateWindowDays);
    }

    /** 单知识点掌握度快照；retention 为预计保留率（从未答对为 0）。 */
    public record KpMastery(String kpId, int events, int errorCount, double successWeight,
                            double severitySum, double mastery, double retention) {
    }

    /** 单学生结果；uploaded* 为 CONFIRMED 错题补事件对总量/错因分布的增量。 */
    public record StudentMastery(List<KpMastery> kps, int uploadedEvents, int uploadedErrors,
                                 Map<String, Integer> uploadedErrorDist) {
    }

    /** 课程结果；byUser 只含传入学生集合，uploadedErrorDist 供班级错因分布合并。 */
    public record CourseMastery(Map<Long, List<KpMastery>> byUser,
                                Map<String, Integer> uploadedErrorDist) {
    }

    public LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public LocalDateTime windowStart() {
        return now().minusDays(aggregateWindowDays);
    }

    public StudentMastery studentMastery(long userId) {
        return studentMastery(userId, now(), windowStart());
    }

    /** 单学生全量知识点掌握度（学生画像 / 到期复习 / 解析复用）。 */
    public StudentMastery studentMastery(long userId, LocalDateTime now, LocalDateTime since) {
        Map<String, Acc> accs = new HashMap<>();
        for (UserKpAgg agg : practiceRecordMapper.aggregateByUserKp(
                null, List.of(userId), halfLifeDays, now, since)) {
            addPractice(accs, agg);
        }
        Set<String> practicedQids = new HashSet<>(practiceRecordMapper.selectDistinctQidsByUser(userId));
        List<StudentErrorRecord> uploads = errorRecordMapper.selectList(
                new LambdaQueryWrapper<StudentErrorRecord>()
                        .eq(StudentErrorRecord::getUserId, userId)
                        .eq(StudentErrorRecord::getStatus, STATUS_CONFIRMED)
                        .ge(StudentErrorRecord::getCreatedAt, since)
                        .select(StudentErrorRecord::getLogId, StudentErrorRecord::getQId,
                                StudentErrorRecord::getKpId, StudentErrorRecord::getErrorType,
                                StudentErrorRecord::getConfirmedAt, StudentErrorRecord::getCreatedAt));
        UploadedStats uploaded = mergeUploads(accs, uploads, practicedQids, null, now);
        return new StudentMastery(toList(accs, now), uploaded.events(), uploaded.errors(),
                uploaded.dist());
    }

    /**
     * 课程 × 学生 × 知识点掌握度（班级画像 / 教师端列表）。
     * qIds 为课程题目，kpIds 为课程知识点（上传错题按知识点归属课程），userIds 为课程学生集合。
     */
    public CourseMastery courseMastery(List<String> qIds, Collection<String> kpIds,
                                       Collection<Long> userIds, LocalDateTime now,
                                       LocalDateTime since) {
        Map<Long, Map<String, Acc>> byUser = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return new CourseMastery(Map.of(), Map.of());
        }
        for (UserKpAgg agg : practiceRecordMapper.aggregateByUserKp(
                qIds, userIds, halfLifeDays, now, since)) {
            if (agg.getUserId() == null) {
                continue;
            }
            addPractice(byUser.computeIfAbsent(agg.getUserId(), key -> new HashMap<>()), agg);
        }

        Set<Long> allowedUsers = new HashSet<>(userIds);
        Map<String, Integer> uploadedDist = new HashMap<>();
        Set<String> courseKps = kpIds == null ? Set.of() : new HashSet<>(kpIds);
        if (courseKps.isEmpty()) {
            Map<Long, List<KpMastery>> onlyPractice = new LinkedHashMap<>();
            for (Map.Entry<Long, Map<String, Acc>> entry : byUser.entrySet()) {
                onlyPractice.put(entry.getKey(), toList(entry.getValue(), now));
            }
            return new CourseMastery(onlyPractice, uploadedDist);
        }
        Map<Long, Set<String>> practicedByUser = practicedQidsByUser(userIds);
        List<StudentErrorRecord> uploads = errorRecordMapper.selectList(
                new LambdaQueryWrapper<StudentErrorRecord>()
                        .in(StudentErrorRecord::getUserId, userIds)
                        .eq(StudentErrorRecord::getStatus, STATUS_CONFIRMED)
                        .ge(StudentErrorRecord::getCreatedAt, since)
                        .select(StudentErrorRecord::getLogId, StudentErrorRecord::getUserId,
                                StudentErrorRecord::getQId, StudentErrorRecord::getKpId,
                                StudentErrorRecord::getErrorType,
                                StudentErrorRecord::getConfirmedAt, StudentErrorRecord::getCreatedAt));
        Map<Long, List<StudentErrorRecord>> uploadsByUser = new HashMap<>();
        for (StudentErrorRecord record : uploads) {
            Long userId = record.getUserId();
            if (userId == null || !allowedUsers.contains(userId)
                    || (!courseKps.isEmpty() && !courseKps.contains(record.getKpId()))) {
                continue;
            }
            uploadsByUser.computeIfAbsent(userId, key -> new ArrayList<>()).add(record);
        }
        for (Map.Entry<Long, List<StudentErrorRecord>> entry : uploadsByUser.entrySet()) {
            UploadedStats part = mergeUploads(
                    byUser.computeIfAbsent(entry.getKey(), key -> new HashMap<>()),
                    entry.getValue(), practicedByUser.getOrDefault(entry.getKey(), Set.of()),
                    courseKps.isEmpty() ? null : courseKps, now);
            for (Map.Entry<String, Integer> dist : part.dist().entrySet()) {
                uploadedDist.merge(dist.getKey(), dist.getValue(), Integer::sum);
            }
        }
        Map<Long, List<KpMastery>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Acc>> entry : byUser.entrySet()) {
            result.put(entry.getKey(), toList(entry.getValue(), now));
        }
        return new CourseMastery(result, uploadedDist);
    }

    /** 单学生在指定课程内的掌握度（教师端详情）。 */
    public StudentMastery studentCourseMastery(List<String> qIds, Collection<String> kpIds,
                                               long userId, LocalDateTime now,
                                               LocalDateTime since) {
        CourseMastery course = courseMastery(qIds, kpIds, List.of(userId), now, since);
        List<KpMastery> kps = course.byUser().getOrDefault(userId, List.of());
        return new StudentMastery(kps, 0, 0, course.uploadedErrorDist());
    }

    /** 每个学生已练题目集合（批量，一次 SQL）。 */
    private Map<Long, Set<String>> practicedQidsByUser(Collection<Long> userIds) {
        Map<Long, Set<String>> result = new HashMap<>();
        for (UserQid row : practiceRecordMapper.selectDistinctQidsByUsers(userIds)) {
            if (row.getUserId() == null || row.getQId() == null) {
                continue;
            }
            result.computeIfAbsent(row.getUserId(), key -> new HashSet<>()).add(row.getQId());
        }
        return result;
    }

    private void addPractice(Map<String, Acc> accs, UserKpAgg agg) {
        if (agg.getKpId() == null || agg.getKpId().isBlank() || agg.getUserId() == null) {
            return;
        }
        Acc acc = accs.computeIfAbsent(agg.getKpId(), Acc::new);
        acc.events += agg.getEvents() == null ? 0 : agg.getEvents();
        acc.errors += agg.getErrorCount() == null ? 0 : agg.getErrorCount();
        acc.successWeight += agg.getSuccessWeight() == null ? 0.0 : agg.getSuccessWeight();
        acc.severitySum += agg.getSeveritySum() == null ? 0.0 : agg.getSeveritySum();
        if (agg.getLastCorrectAt() != null
                && (acc.lastCorrectAt == null || agg.getLastCorrectAt().isAfter(acc.lastCorrectAt))) {
            acc.lastCorrectAt = agg.getLastCorrectAt();
        }
    }

    /**
     * CONFIRMED 上传错题合并：已由练习计入的 qId 跳过；同一题目同一自然日重复确认只取最后一次。
     * 不计入 lastCorrectAt（错题本身不是正确证据）。
     */
    private UploadedStats mergeUploads(Map<String, Acc> accs, List<StudentErrorRecord> records,
                                       Set<String> practicedQids, Set<String> allowedKpIds,
                                       LocalDateTime now) {
        Map<String, StudentErrorRecord> perDay = new LinkedHashMap<>();
        for (StudentErrorRecord record : records) {
            String kpId = record.getKpId();
            if (kpId == null || kpId.isBlank()) {
                continue;
            }
            if (allowedKpIds != null && !allowedKpIds.contains(kpId)) {
                continue;
            }
            String qId = record.getQId();
            if (qId != null && !qId.isBlank() && practicedQids.contains(qId)) {
                continue;
            }
            LocalDateTime at = eventTime(record);
            if (at == null) {
                continue;
            }
            String key = (qId == null || qId.isBlank() ? "log:" + record.getLogId() : qId)
                    + "|" + at.toLocalDate();
            StudentErrorRecord previous = perDay.get(key);
            if (previous == null || eventTime(previous).isBefore(at)) {
                perDay.put(key, record);
            }
        }
        int events = 0;
        int errors = 0;
        Map<String, Integer> dist = new HashMap<>();
        for (StudentErrorRecord record : perDay.values()) {
            String kpId = record.getKpId();
            LocalDateTime at = eventTime(record);
            Acc acc = accs.computeIfAbsent(kpId, Acc::new);
            acc.events++;
            acc.errors++;
            acc.severitySum += masteryCalculator.errorWeight(record.getErrorType(), at, now);
            events++;
            errors++;
            if (record.getErrorType() != null && !record.getErrorType().isBlank()) {
                dist.merge(record.getErrorType(), 1, Integer::sum);
            }
        }
        return new UploadedStats(events, errors, dist);
    }

    private LocalDateTime eventTime(StudentErrorRecord record) {
        return record.getConfirmedAt() != null ? record.getConfirmedAt() : record.getCreatedAt();
    }

    private List<KpMastery> toList(Map<String, Acc> accs, LocalDateTime now) {
        List<KpMastery> result = new ArrayList<>(accs.size());
        for (Acc acc : accs.values()) {
            double mastery = masteryCalculator.mastery(acc.successWeight, acc.severitySum);
            double retention = acc.lastCorrectAt == null ? 0.0
                    : masteryCalculator.decay(acc.lastCorrectAt, now);
            result.add(new KpMastery(acc.kpId, acc.events, acc.errors, acc.successWeight,
                    acc.severitySum, mastery, retention));
        }
        result.sort((a, b) -> a.kpId().compareTo(b.kpId()));
        return result;
    }

    private record UploadedStats(int events, int errors, Map<String, Integer> dist) {
    }

    /** 可变累加器，避免合并上传错题时反复重建不可变记录。 */
    private static final class Acc {
        private final String kpId;
        private int events;
        private int errors;
        private double successWeight;
        private double severitySum;
        private LocalDateTime lastCorrectAt;

        private Acc(String kpId) {
            this.kpId = kpId;
        }
    }
}

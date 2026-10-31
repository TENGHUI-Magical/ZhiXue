package com.zhixue.plan.dto;

import java.util.List;

/**
 * 教师查看学生学习记录的视图（契约 openapi TeacherStudent* / C12）。
 *
 * <p>学生集合口径：在该课程题目上有作答记录的学生（复用现有 practice_record，
 * 不新增 course_student 表）。姓名脱敏展示。</p>
 */
public final class TeacherStudentViews {

    private TeacherStudentViews() {
    }

    /** 学生聚合项（列表与详情摘要共用）。 */
    public record StudentItem(Long userId, String displayName, int practiceCount,
                              Double correctRate, Integer avgCostSeconds, Double avgMastery,
                              int errorCount, int planDone, int planTotal, String lastActiveAt) {
    }

    public record Page(int page, int size, long total, List<StudentItem> records) {
    }

    public record PracticeItem(Long practiceId, String qId, String questionText,
                               String kpId, String kpName, Boolean isCorrect, String myAnswer,
                               String correctAnswer, Integer costSeconds, String errorType,
                               String source, String createdAt) {
    }

    public record ErrorItem(String logId, String qId, String kpId, String kpName,
                            String status, String errorType, Double ocrConfidence, String createdAt) {
    }

    public record KpMastery(String kpId, String name, Double mastery, int sampleCount, int errorCount) {
    }

    public record PracticePage(int page, int size, long total, List<PracticeItem> records) {
    }

    public record LearningRecord(StudentItem summary, PracticePage practices,
                                 List<ErrorItem> errors, List<KpMastery> kpMastery) {
    }
}

package com.zhixue.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 题库实体（对齐 init-v2.sql 的 question_bank 表）。
 * 题目编号为业务主键，人工赋值，故显式 IdType.INPUT。
 */
@TableName("question_bank")
public class QuestionBank {

    @TableId(type = IdType.INPUT)
    private String qId;

    private Long courseId;

    /** 主知识点，用于展示与默认推荐；多知识点见 question_kp */
    private String primaryKpId;

    /** 单选/多选/填空/简答/编程 */
    private String questionType;

    private String questionText;

    /** 选项数组 JSON：[{"key":"A","text":"..."}]；非选择题为 NULL */
    private String optionsJson;

    private String answerText;

    private String analysisText;

    /** 1 简单，2 中等，3 困难 */
    private Integer difficulty;

    private String source;

    private String variantHint;

    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public String getQId() { return qId; }
    public void setQId(String qId) { this.qId = qId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getPrimaryKpId() { return primaryKpId; }
    public void setPrimaryKpId(String primaryKpId) { this.primaryKpId = primaryKpId; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public String getAnalysisText() { return analysisText; }
    public void setAnalysisText(String analysisText) { this.analysisText = analysisText; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getVariantHint() { return variantHint; }
    public void setVariantHint(String variantHint) { this.variantHint = variantHint; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

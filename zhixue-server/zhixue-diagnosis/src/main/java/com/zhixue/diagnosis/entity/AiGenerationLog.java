package com.zhixue.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 调用留痕（对齐 init-v2.sql 的 ai_generation_log 表）。
 * 论文实验数据、溯源率、教师评阅队列都依赖本表。
 */
@TableName("ai_generation_log")
public class AiGenerationLog {

    @TableId(value = "log_pk", type = IdType.AUTO)
    private Long logPk;

    /** 讲解编号，如 EX_20261025120000_a1b2；同一讲解只落一行 */
    private String explainId;

    /** 全链路追踪 ID（TraceIdFilter 生成，契约 §1.2） */
    private String traceId;

    /** 学生 user_id */
    private Long userId;

    /** 关联错题记录 log_id；可为空 */
    private String logId;

    /** OCR / RAG / LLM / RECOMMEND */
    private String scene;

    /** 实际使用的模型名（deepseek-chat / glm-4-flash / local-template） */
    private String model;

    /** Prompt 模板版本（Prompt 库入 Git，版本可回溯） */
    private String promptVersion;

    /** 是否降级：1 是，0 否 */
    private Integer isDegraded;

    /** 降级级别：L2 / L3；未降级为 NULL */
    private String degradeLevel;

    /** 讲解耗时毫秒 */
    private Integer latencyMs;

    /** 输入 token 数（上游 usage 透传；本地模板为 0） */
    private Integer promptTokens;

    /** 输出 token 数 */
    private Integer completionTokens;

    /** 引用的知识切片 kp_id 数组 JSON */
    private String sourceIds;

    /** 讲解正文快照（上限 20000 字符，超限截断并 log.warn） */
    private String contentText;

    /** 是否触发合规拦截：1 是，0 否 */
    private Integer isBlocked;

    /** 教师评阅分数 1—5；未评为 NULL（G5 双评数据源） */
    private Integer teacherScore;

    /** 教师评语 */
    private String teacherComment;

    /** 评阅时间 */
    private LocalDateTime reviewedAt;

    /** 生成时间 */
    private LocalDateTime createdAt;

    public Long getLogPk() { return logPk; }
    public void setLogPk(Long logPk) { this.logPk = logPk; }
    public String getExplainId() { return explainId; }
    public void setExplainId(String explainId) { this.explainId = explainId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Integer getIsDegraded() { return isDegraded; }
    public void setIsDegraded(Integer isDegraded) { this.isDegraded = isDegraded; }
    public String getDegradeLevel() { return degradeLevel; }
    public void setDegradeLevel(String degradeLevel) { this.degradeLevel = degradeLevel; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }
    public String getSourceIds() { return sourceIds; }
    public void setSourceIds(String sourceIds) { this.sourceIds = sourceIds; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public Integer getIsBlocked() { return isBlocked; }
    public void setIsBlocked(Integer isBlocked) { this.isBlocked = isBlocked; }
    public Integer getTeacherScore() { return teacherScore; }
    public void setTeacherScore(Integer teacherScore) { this.teacherScore = teacherScore; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

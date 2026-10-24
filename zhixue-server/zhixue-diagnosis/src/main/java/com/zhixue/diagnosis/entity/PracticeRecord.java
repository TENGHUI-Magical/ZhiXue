package com.zhixue.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 练习作答记录（对齐 init-v2.sql 的 practice_record 表）。
 * 掌握度的分母与推荐难度基准、7 天冷却都来自本表。
 */
@TableName("practice_record")
public class PracticeRecord {

    /** 作答 ID，自增 */
    @TableId(value = "practice_id", type = IdType.AUTO)
    private Long practiceId;

    /** 学生 user_id */
    private Long userId;

    /** 客户端幂等键，防止重复提交 */
    private String clientToken;

    /** 题目编号 */
    private String qId;

    /** 冗余主知识点，避免画像聚合时关联查询 */
    private String kpId;

    /** 来源：RECOMMEND / PLAN / SELF */
    private String source;

    /** 是否答对：1 对，0 错 */
    private Integer isCorrect;

    /** 学生作答内容 */
    private String myAnswer;

    /** 答错时的错因英文 Key */
    private String errorType;

    /** 作答耗时秒数 */
    private Integer costSeconds;

    /** 若来自推荐，关联 recommendation.recommendation_id */
    private Long recommendationId;

    /** 作答时间（数据库时间，防客户端改时间刷分） */
    private LocalDateTime createdAt;

    public Long getPracticeId() { return practiceId; }
    public void setPracticeId(Long practiceId) { this.practiceId = practiceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getClientToken() { return clientToken; }
    public void setClientToken(String clientToken) { this.clientToken = clientToken; }
    public String getQId() { return qId; }
    public void setQId(String qId) { this.qId = qId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
    public String getMyAnswer() { return myAnswer; }
    public void setMyAnswer(String myAnswer) { this.myAnswer = myAnswer; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public Integer getCostSeconds() { return costSeconds; }
    public void setCostSeconds(Integer costSeconds) { this.costSeconds = costSeconds; }
    public Long getRecommendationId() { return recommendationId; }
    public void setRecommendationId(Long recommendationId) { this.recommendationId = recommendationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

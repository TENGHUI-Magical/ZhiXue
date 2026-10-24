package com.zhixue.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 推荐记录（对齐 recommendation 表）。
 */
@TableName("recommendation")
public class RecommendationRecord {

    @TableId(value = "recommendation_id", type = IdType.AUTO)
    private Long recommendationId;

    private Long userId;

    private String logId;

    private String qId;

    /** CONSOLIDATE / VARIANT / ADVANCE */
    private String layer;

    private String reason;

    private Integer rankNo;

    private Integer isAdopted;

    private LocalDateTime createdAt;

    public Long getRecommendationId() { return recommendationId; }
    public void setRecommendationId(Long recommendationId) { this.recommendationId = recommendationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getQId() { return qId; }
    public void setQId(String qId) { this.qId = qId; }
    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getRankNo() { return rankNo; }
    public void setRankNo(Integer rankNo) { this.rankNo = rankNo; }
    public Integer getIsAdopted() { return isAdopted; }
    public void setIsAdopted(Integer isAdopted) { this.isAdopted = isAdopted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

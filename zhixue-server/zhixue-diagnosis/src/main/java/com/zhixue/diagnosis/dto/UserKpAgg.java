package com.zhixue.diagnosis.dto;

import java.time.LocalDateTime;

/**
 * 按（用户, 知识点）聚合的练习统计；successWeight/severitySum 由 SQL 按各事件时间
 * 逐条衰减后求和（docs/05 §2.3），教师端与学生端共用，避免“最后证据”统一衰减的口径差。
 * lastCorrectAt 为该知识点最后一次正确作答时间，供“预计保留率”输出（无正确证据为 null）。
 */
public class UserKpAgg {

    private Long userId;
    private String kpId;
    private Integer events;
    private Double successWeight;
    private Double severitySum;
    private Integer errorCount;
    private LocalDateTime lastCorrectAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getKpId() {
        return kpId;
    }

    public void setKpId(String kpId) {
        this.kpId = kpId;
    }

    public Integer getEvents() {
        return events;
    }

    public void setEvents(Integer events) {
        this.events = events;
    }

    public Double getSuccessWeight() {
        return successWeight;
    }

    public void setSuccessWeight(Double successWeight) {
        this.successWeight = successWeight;
    }

    public Double getSeveritySum() {
        return severitySum;
    }

    public void setSeveritySum(Double severitySum) {
        this.severitySum = severitySum;
    }

    public Integer getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(Integer errorCount) {
        this.errorCount = errorCount;
    }

    public LocalDateTime getLastCorrectAt() {
        return lastCorrectAt;
    }

    public void setLastCorrectAt(LocalDateTime lastCorrectAt) {
        this.lastCorrectAt = lastCorrectAt;
    }
}

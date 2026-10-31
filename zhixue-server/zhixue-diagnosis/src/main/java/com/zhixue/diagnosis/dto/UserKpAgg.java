package com.zhixue.diagnosis.dto;

/**
 * 按（用户, 知识点）聚合的练习统计；severitySum 由 SQL 按 docs/05 §2.2 权重计算。
 */
public class UserKpAgg {

    private Long userId;
    private String kpId;
    private Integer events;
    private Double severitySum;
    private Integer errorCount;

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
}

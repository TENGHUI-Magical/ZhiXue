package com.zhixue.diagnosis.dto;

import java.time.LocalDateTime;

/**
 * 按用户聚合的练习统计（C12 教师学生列表）。
 */
public class UserPracticeAgg {

    private Long userId;
    private Integer practiceCount;
    private Integer wrongCount;
    private Integer errorQidCount;
    private Double avgCostSeconds;
    private LocalDateTime lastActiveAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getPracticeCount() {
        return practiceCount;
    }

    public void setPracticeCount(Integer practiceCount) {
        this.practiceCount = practiceCount;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
    }

    public Integer getErrorQidCount() {
        return errorQidCount;
    }

    public void setErrorQidCount(Integer errorQidCount) {
        this.errorQidCount = errorQidCount;
    }

    public Double getAvgCostSeconds() {
        return avgCostSeconds;
    }

    public void setAvgCostSeconds(Double avgCostSeconds) {
        this.avgCostSeconds = avgCostSeconds;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}

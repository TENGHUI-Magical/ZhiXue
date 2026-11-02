package com.zhixue.diagnosis.dto;

import java.time.LocalDateTime;

/**
 * 按（用户, 知识点）聚合的补漏信号；SQL GROUP BY 一次取回，避免逐知识点查询。
 * lastErrorType 取该知识点最近一次答错记录的错因，供“错因可修复性”评分。
 */
public class KpPracticeSignal {

    private String kpId;
    private Integer totalCount;
    private Integer wrongCount;
    private String lastErrorType;
    private LocalDateTime lastPracticedAt;

    public String getKpId() {
        return kpId;
    }

    public void setKpId(String kpId) {
        this.kpId = kpId;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
    }

    public String getLastErrorType() {
        return lastErrorType;
    }

    public void setLastErrorType(String lastErrorType) {
        this.lastErrorType = lastErrorType;
    }

    public LocalDateTime getLastPracticedAt() {
        return lastPracticedAt;
    }

    public void setLastPracticedAt(LocalDateTime lastPracticedAt) {
        this.lastPracticedAt = lastPracticedAt;
    }
}

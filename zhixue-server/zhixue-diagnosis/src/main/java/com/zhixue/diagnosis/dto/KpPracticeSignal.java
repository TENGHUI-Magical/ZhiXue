package com.zhixue.diagnosis.dto;

import java.time.LocalDateTime;

/**
 * 按（用户, 知识点）聚合的补漏信号；SQL GROUP BY 一次取回，避免逐知识点查询。
 * lastErrorType 取该知识点最近一次答错记录的错因，供“错因可修复性”评分；
 * lastCorrectAt 取最后一次正确作答时间，供“遗忘紧迫度”（05 §3.7：按距上次
 * <b>正确</b>作答衰减，刚答错不代表没遗忘）。
 */
public class KpPracticeSignal {

    private String kpId;
    private Integer totalCount;
    private Integer wrongCount;
    private String lastErrorType;
    /** 该知识点最后一次正确作答时间（遗忘紧迫度锚点）；从未答对为 null */
    private LocalDateTime lastCorrectAt;

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

    public LocalDateTime getLastCorrectAt() {
        return lastCorrectAt;
    }

    public void setLastCorrectAt(LocalDateTime lastCorrectAt) {
        this.lastCorrectAt = lastCorrectAt;
    }
}

package com.zhixue.diagnosis.dto;

import java.time.LocalDateTime;

/**
 * 按（用户, 知识点, 题目）聚合的复习信号；SQL GROUP BY 一次取回，不拉明细。
 * 到期复习选题用（05 §3.8）：lastWrongAt 非空的题优先重做（最近失败的最值得重测），
 * 其次 lastCorrectAt 非空的题（同题重测保留率），都没有时用未练过的题兜底。
 */
public class QuestionPracticeSignal {

    private String kpId;
    private String qId;
    /** 该题最后一次答错时间；从未答错为 null */
    private LocalDateTime lastWrongAt;
    /** 该题最后一次答对时间；从未答对为 null */
    private LocalDateTime lastCorrectAt;

    public String getKpId() {
        return kpId;
    }

    public void setKpId(String kpId) {
        this.kpId = kpId;
    }

    public String getQId() {
        return qId;
    }

    public void setQId(String qId) {
        this.qId = qId;
    }

    public LocalDateTime getLastWrongAt() {
        return lastWrongAt;
    }

    public void setLastWrongAt(LocalDateTime lastWrongAt) {
        this.lastWrongAt = lastWrongAt;
    }

    public LocalDateTime getLastCorrectAt() {
        return lastCorrectAt;
    }

    public void setLastCorrectAt(LocalDateTime lastCorrectAt) {
        this.lastCorrectAt = lastCorrectAt;
    }
}

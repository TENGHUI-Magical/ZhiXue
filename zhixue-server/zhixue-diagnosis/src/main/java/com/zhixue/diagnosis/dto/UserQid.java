package com.zhixue.diagnosis.dto;

/** 学生练过的题目投影（user_id + q_id），用于批量判断“错题是否已由练习计入”。 */
public class UserQid {

    private Long userId;
    private String qId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQId() {
        return qId;
    }

    public void setQId(String qId) {
        this.qId = qId;
    }
}

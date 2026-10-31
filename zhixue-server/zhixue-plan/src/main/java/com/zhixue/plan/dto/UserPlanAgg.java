package com.zhixue.plan.dto;

/**
 * 按用户聚合的周计划进度（C12）。
 */
public class UserPlanAgg {

    private Long userId;
    private Integer planTotal;
    private Integer planDone;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getPlanTotal() {
        return planTotal;
    }

    public void setPlanTotal(Integer planTotal) {
        this.planTotal = planTotal;
    }

    public Integer getPlanDone() {
        return planDone;
    }

    public void setPlanDone(Integer planDone) {
        this.planDone = planDone;
    }
}

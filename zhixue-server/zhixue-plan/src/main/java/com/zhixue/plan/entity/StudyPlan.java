package com.zhixue.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习计划（对齐 init-v2.sql 的 study_plan 表，日粒度）。
 */
@TableName("study_plan")
public class StudyPlan {

    @TableId(value = "plan_id", type = IdType.AUTO)
    private Long planId;

    private Long userId;

    private LocalDate planDate;

    private Integer planCount;

    private Integer doneCount;

    /** PENDING / DONE / OVERDUE */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public Integer getPlanCount() { return planCount; }
    public void setPlanCount(Integer planCount) { this.planCount = planCount; }
    public Integer getDoneCount() { return doneCount; }
    public void setDoneCount(Integer doneCount) { this.doneCount = doneCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.zhixue.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 学习计划明细（对齐 init-v2.sql 的 study_plan_item 表）。
 */
@TableName("study_plan_item")
public class StudyPlanItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    private Long planId;

    private String qId;

    private String kpId;

    private Integer sortNo;

    /** 是否完成：1 完成，0 未完成 */
    private Integer isDone;

    /** 完成后关联的 practice_record.practice_id */
    private Long practiceId;

    private LocalDateTime createdAt;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getQId() { return qId; }
    public void setQId(String qId) { this.qId = qId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Integer getIsDone() { return isDone; }
    public void setIsDone(Integer isDone) { this.isDone = isDone; }
    public Long getPracticeId() { return practiceId; }
    public void setPracticeId(Long practiceId) { this.practiceId = practiceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

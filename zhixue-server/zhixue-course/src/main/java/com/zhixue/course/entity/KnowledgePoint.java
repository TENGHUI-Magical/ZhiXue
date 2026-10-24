package com.zhixue.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 知识点实体（对齐 init-v2.sql 的 knowledge_point 表，16 表 V2 结构）。
 * 业务编号 KP0101 等由人工录入，不走数据库自增，故显式声明 IdType.INPUT。
 */
@TableName("knowledge_point")
public class KnowledgePoint {

    /** 知识点编号，如 KP0101（业务主键，人工赋值） */
    @TableId(type = IdType.INPUT)
    private String kpId;

    /** 所属课程 course_id */
    private Long courseId;

    /** 所属章节 course_chapter.chapter_id */
    private Long chapterId;

    /** 知识点名称（数据标注规范：专有名词命名） */
    private String name;

    /** 核心概念切片 300—500 字，RAG 检索与 L3 模板降级共用 */
    private String conceptText;

    /** 常见误区 1—3 条，换行分隔 */
    private String misconception;

    /** 教材页码，如 P18，来源追溯展示 */
    private String textbookPage;

    /** 自检题提示，讲解末段用 */
    private String trialHint;

    /** 章节内排序 */
    private Integer sortNo;

    /** 是否启用：1 启用，0 停用 */
    private Integer isActive;

    /** 录入人 user_id */
    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getConceptText() { return conceptText; }
    public void setConceptText(String conceptText) { this.conceptText = conceptText; }
    public String getMisconception() { return misconception; }
    public void setMisconception(String misconception) { this.misconception = misconception; }
    public String getTextbookPage() { return textbookPage; }
    public void setTextbookPage(String textbookPage) { this.textbookPage = textbookPage; }
    public String getTrialHint() { return trialHint; }
    public void setTrialHint(String trialHint) { this.trialHint = trialHint; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

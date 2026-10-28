package com.zhixue.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 课程实体（对齐 init-v2.sql 的 course 表）。
 */
@TableName("course")
public class Course {

    @TableId(value = "course_id", type = IdType.AUTO)
    private Long courseId;

    private String courseName;

    private Long teacherId;

    private String textbook;

    private String description;

    /** 五维能力配置 JSON：[{"key":"concept","name":"概念理解"}] */
    private String dimensionJson;

    private Integer isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTextbook() { return textbook; }
    public void setTextbook(String textbook) { this.textbook = textbook; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDimensionJson() { return dimensionJson; }
    public void setDimensionJson(String dimensionJson) { this.dimensionJson = dimensionJson; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

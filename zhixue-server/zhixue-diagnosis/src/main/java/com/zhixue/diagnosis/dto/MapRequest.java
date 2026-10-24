package com.zhixue.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 知识点映射请求（契约 V2：POST /api/diagnosis/map）。
 */
public class MapRequest {

    /** 题干文本，最长 3000 字（契约 MapRequest.text） */
    @NotBlank(message = "text 不能为空")
    @Size(max = 3000, message = "text 最长 3000 字")
    private String text;

    /** 课程 ID，契约必填 */
    @NotNull(message = "courseId 不能为空")
    private Long courseId;

    /** 章节 ID，可选；当前竖切不按章节过滤 */
    private Long chapterId;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}

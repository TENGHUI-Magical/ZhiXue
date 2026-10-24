package com.zhixue.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 知识点先修边（对齐 knowledge_prereq 表）。
 */
@TableName("knowledge_prereq")
public class KnowledgePrereq {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 后继知识点 */
    private String kpId;

    /** 先修知识点 */
    private String prereqKpId;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getPrereqKpId() { return prereqKpId; }
    public void setPrereqKpId(String prereqKpId) { this.prereqKpId = prereqKpId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

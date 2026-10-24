package com.zhixue.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 知识点关键词（对齐 init-v2.sql 的 kp_keyword 表）。
 * 映射通道一的数据源：关键词命中打分，先于向量检索兜底。
 */
@TableName("kp_keyword")
public class KpKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属知识点，如 KP0201 */
    private String kpId;

    /** 关键词或同义词，如 主键 / PK */
    private String keyword;

    /** 权重 0.50—1.00，区分核心术语与辅助词 */
    private BigDecimal weight;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.zhixue.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学情画像快照（对齐 init-v2.sql 的 learning_portrait_snapshot 表）。
 */
@TableName("learning_portrait_snapshot")
public class LearningPortraitSnapshot {

    @TableId(value = "snapshot_id", type = IdType.AUTO)
    private Long snapshotId;

    private Long userId;

    private LocalDate statDate;

    /** 维度得分 JSON 对象 */
    private String dimensionScores;

    /** 薄弱知识点 JSON 数组 */
    private String weakestKps;

    /** 错因分布 JSON 对象 */
    private String errorDist;

    private Integer totalPractice;

    private Integer totalError;

    private LocalDateTime createdAt;

    public Long getSnapshotId() { return snapshotId; }
    public void setSnapshotId(Long snapshotId) { this.snapshotId = snapshotId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }
    public String getDimensionScores() { return dimensionScores; }
    public void setDimensionScores(String dimensionScores) { this.dimensionScores = dimensionScores; }
    public String getWeakestKps() { return weakestKps; }
    public void setWeakestKps(String weakestKps) { this.weakestKps = weakestKps; }
    public String getErrorDist() { return errorDist; }
    public void setErrorDist(String errorDist) { this.errorDist = errorDist; }
    public Integer getTotalPractice() { return totalPractice; }
    public void setTotalPractice(Integer totalPractice) { this.totalPractice = totalPractice; }
    public Integer getTotalError() { return totalError; }
    public void setTotalError(Integer totalError) { this.totalError = totalError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

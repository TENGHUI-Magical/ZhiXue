package com.zhixue.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OCR 任务（对齐 init-v2.sql 的 ocr_task 表，契约 2.2 节任务表轮询）。
 */
@TableName("ocr_task")
public class OcrTask {

    @TableId(type = IdType.INPUT)
    private String taskId;

    private Long userId;

    /** 客户端幂等键，防重复上传 */
    private String clientToken;

    private String imagePath;

    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;

    private BigDecimal confidence;

    /** 结构化结果 JSON：stem/rawText/lines/structured/engine */
    private String resultJson;

    private String errorCode;

    private String errorMsg;

    private Integer retryCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getClientToken() { return clientToken; }
    public void setClientToken(String clientToken) { this.clientToken = clientToken; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

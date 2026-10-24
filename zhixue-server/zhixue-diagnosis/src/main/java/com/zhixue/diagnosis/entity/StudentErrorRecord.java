package com.zhixue.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 错题记录（对齐 init-v2.sql 的 student_error_record 表）。
 */
@TableName("student_error_record")
public class StudentErrorRecord {

    @TableId(type = IdType.INPUT)
    private String logId;

    private Long userId;

    /** 客户端幂等键，与 userId 组成唯一键 */
    private String clientToken;

    private String qId;

    /** 学生确认的知识点；DRAFT 阶段可为空 */
    private String kpId;

    private String imageUrl;

    /** DRAFT / CONFIRMED */
    private String status;

    private String errorType;

    private String correctedText;

    private String ocrText;

    private BigDecimal ocrConfidence;

    private String studentNote;

    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getClientToken() { return clientToken; }
    public void setClientToken(String clientToken) { this.clientToken = clientToken; }
    public String getQId() { return qId; }
    public void setQId(String qId) { this.qId = qId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public BigDecimal getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(BigDecimal ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    public String getStudentNote() { return studentNote; }
    public void setStudentNote(String studentNote) { this.studentNote = studentNote; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

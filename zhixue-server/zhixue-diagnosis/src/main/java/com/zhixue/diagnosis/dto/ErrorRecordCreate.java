package com.zhixue.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建错题记录（契约 V2：POST /api/error/records）。
 * 允许字段不全，先落 DRAFT；确认后进入 CONFIRMED。
 */
public class ErrorRecordCreate {

    @NotNull(message = "courseId 不能为空")
    private Long courseId;

    /** 客户端幂等键，与 userId 组成唯一键 */
    @NotBlank(message = "clientToken 不能为空")
    @Size(max = 64, message = "clientToken 最长 64 字")
    private String clientToken;

    private String qId;
    private String kpId;
    private String imageUrl;
    private String ocrText;
    private String correctedText;
    private BigDecimal ocrConfidence;
    private String errorType;
    private String studentNote;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getClientToken() { return clientToken; }
    public void setClientToken(String clientToken) { this.clientToken = clientToken; }
    public String getQId() { return qId; }
    public void setQId(String qId) { this.qId = qId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getOcrText() { return ocrText; }
    public void setOcrText(String ocrText) { this.ocrText = ocrText; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public BigDecimal getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(BigDecimal ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getStudentNote() { return studentNote; }
    public void setStudentNote(String studentNote) { this.studentNote = studentNote; }
}

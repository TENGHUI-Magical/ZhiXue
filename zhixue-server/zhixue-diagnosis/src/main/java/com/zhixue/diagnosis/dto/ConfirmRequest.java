package com.zhixue.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 确认错题（契约 V2：PUT /api/error/records/{logId}/confirm）。
 */
public class ConfirmRequest {

    @NotBlank(message = "kpId 不能为空")
    private String kpId;

    @NotBlank(message = "errorType 不能为空")
    private String errorType;

    @Size(max = 2000, message = "correctedText 最长 2000 字")
    private String correctedText;

    @Size(max = 500, message = "studentNote 最长 500 字")
    private String studentNote;

    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String correctedText) { this.correctedText = correctedText; }
    public String getStudentNote() { return studentNote; }
    public void setStudentNote(String studentNote) { this.studentNote = studentNote; }
}

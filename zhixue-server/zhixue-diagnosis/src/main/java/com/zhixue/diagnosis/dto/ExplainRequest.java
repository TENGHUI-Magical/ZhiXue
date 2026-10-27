package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 讲解请求（契约 V2：POST /api/diagnosis/explain）。
 * <p>
 * 契约要求 logId + CONFIRMED 记录；RagExplainService 会校验记录状态与 kpId 一致性。
 */
public class ExplainRequest {

    /** 错题记录编号；契约必填，且必须是 CONFIRMED 状态 */
    @NotBlank(message = "logId 不能为空")
    private String logId;

    /** 知识点编号 */
    @NotBlank(message = "kpId 不能为空")
    private String kpId;

    /** 五类错因英文 Key（契约枚举） */
    @NotBlank(message = "errorType 不能为空")
    @Schema(allowableValues = {"CONCEPT_CONFUSION", "METHOD_MISSING", "STEP_OMISSION",
            "CALCULATION_ERROR", "BOUNDARY_ERROR"},
            description = "五类标准错因枚举（契约）")
    private String errorType;

    /** 学生题干，可选 */
    @Size(max = 2000, message = "questionText 最长 2000 字")
    private String questionText;

    /** 跳过缓存；当前未接缓存，字段先对齐契约 */
    private Boolean forceRefresh;

    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getKpId() { return kpId; }
    public void setKpId(String kpId) { this.kpId = kpId; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public Boolean getForceRefresh() { return forceRefresh; }
    public void setForceRefresh(Boolean forceRefresh) { this.forceRefresh = forceRefresh; }
}

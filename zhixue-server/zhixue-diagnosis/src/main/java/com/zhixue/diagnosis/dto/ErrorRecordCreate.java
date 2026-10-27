package com.zhixue.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建错题记录（契约 V2：POST /api/error/records）。
 * 允许字段不全，先落 DRAFT；确认后进入 CONFIRMED。
 * <p>
 * 使用 record 保证 qId 组件名原样保留：POJO getter 内省会让 springdoc 额外生成 qid，
 * 造成生成规范与冻结契约字段名漂移。
 */
public record ErrorRecordCreate(
        @NotNull(message = "courseId 不能为空") Long courseId,

        /** 客户端幂等键，与 userId 组成唯一键 */
        @NotBlank(message = "clientToken 不能为空")
        @Size(max = 64, message = "clientToken 最长 64 字") String clientToken,

        String qId,
        String kpId,
        String imageUrl,
        String ocrText,
        String correctedText,
        BigDecimal ocrConfidence,
        String errorType,
        String studentNote) {
}
package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 提交作答响应（契约 openapi PracticeResponse.data）。
 *
 * @param practiceId 作答记录 ID
 * @param duplicate  幂等命中时 true，返回首次结果
 * @param gradingMode AUTO=客观题服务端判分；SELF=主观题自评；幂等回放时可为 null
 * @param isCorrect  服务端最终对错；幂等回放时来自首次记录
 */
public record PracticeResultView(Long practiceId, boolean duplicate,
                                 @Schema(allowableValues = {"AUTO", "SELF"}, nullable = true,
                                         description = "AUTO=客观题服务端判分；SELF=主观题自评；"
                                                 + "幂等回放时可为 null")
                                 String gradingMode,
                                 Boolean isCorrect) {
}

package com.zhixue.diagnosis.dto;

/**
 * 提交作答响应（契约 openapi PracticeResponse.data）。
 *
 * @param practiceId 作答记录 ID
 * @param duplicate  幂等命中时 true，返回首次结果
 */
public record PracticeResultView(Long practiceId, boolean duplicate) {
}

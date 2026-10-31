package com.zhixue.plan.dto;

/**
 * 打卡回执（契约 openapi CheckinResponse.data）。
 *
 * @param planId     计划 ID
 * @param qId        题目编号
 * @param doneCount  当前计划已完成数（服务端重算后）
 * @param planCount  当前计划总题数
 * @param verified   是否计入可信闭环（answered=true 才为 true）
 * @param isCorrect  本题对错；answered=false 时为 null
 * @param practiceId 作答记录 ID；answered=false 时为 null
 * @param duplicate  是否幂等命中
 */
public record CheckinResult(Long planId, String qId, int doneCount, int planCount,
                            boolean verified, Boolean isCorrect, Long practiceId, boolean duplicate) {
}

package com.zhixue.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 到期复习项（契约 DueReviewItem；判定与排序见 05 §3.8）。
 * 今日任务的三种来源之一：DUE_REVIEW（到期复习）＞薄弱补漏（PRP）＞新知识推进，
 * 前端把到期复习卡放在计划/推荐之前，复习项不被新题挤掉。
 *
 * @param source               来源标识，恒为 DUE_REVIEW
 * @param kpId                 知识点编号
 * @param kpName               知识点名称
 * @param qId                  建议重做的题目编号（优先最近答错的题）
 * @param questionText         题干
 * @param difficulty           难度 1—3
 * @param daysSinceLastCorrect 距上次正确作答天数
 * @param mastery              掌握度（与画像同口径；窗口外无证据按先验 0.5）
 * @param sampleCount          该知识点累计练习次数
 * @param reason               可解释理由，≤50 字，如“距上次答对 40 天，已过半衰期 + 概念混淆型错误”
 */
public record DueReviewItem(
        @Schema(allowableValues = {"DUE_REVIEW"}) String source,
        String kpId, String kpName, String qId, String questionText, Integer difficulty,
        Integer daysSinceLastCorrect, Double mastery, Integer sampleCount,
        @Schema(maxLength = 50) String reason) {
}

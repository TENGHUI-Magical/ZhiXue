package com.zhixue.diagnosis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 掌握度：错因严重度 × 时间半衰（docs/05 §2）。
 * <p>
 * 公式：{@code mastery = clamp01(1 - Σ(severity_i × decay(d_i)) / max(1, N))}，
 * N 为训练事件数（练习作答 + 无对应练习的 CONFIRMED 错题，事件互斥见 docs/05 §2.1）。
 * <p>
 * 为什么不用 BKT：10—15 人样本拟合 slip/guess 不稳、教师也看不懂参数；
 * 这里用教学语义直接给权重，等价于“固定参数的知识追踪”，可解释、可回算。
 * 纯函数，无数据库依赖，便于单测与历史数据回算。
 */
public final class MasteryCalculator {

    /** 错因严重度（docs/05 §2.2）；未标注取中位 0.8 */
    private static final Map<String, Double> SEVERITY = Map.of(
            "CONCEPT_CONFUSION", 1.0,
            "METHOD_MISSING", 1.0,
            "STEP_OMISSION", 0.8,
            "CALCULATION_ERROR", 0.6,
            "BOUNDARY_ERROR", 0.8);

    private static final double DEFAULT_SEVERITY = 0.8;

    private final double halfLifeDays;

    public MasteryCalculator(double halfLifeDays) {
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
    }

    /** 错因 → 严重度；未知/空取 0.8。 */
    public static double severity(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return DEFAULT_SEVERITY;
        }
        return SEVERITY.getOrDefault(errorType.trim().toUpperCase(java.util.Locale.ROOT), DEFAULT_SEVERITY);
    }

    /**
     * 时间衰减：decay(d) = 0.5^(d/30)，当天为 1。
     * 事件时间缺失时视为无衰减（历史数据兼容），未来时间按 0 天处理。
     */
    public double decay(LocalDateTime eventAt, LocalDateTime now) {
        if (eventAt == null || now == null) {
            return 1.0;
        }
        long days = Math.max(0, Duration.between(eventAt, now).toDays());
        return Math.pow(0.5, days / halfLifeDays);
    }

    /**
     * @param events      训练事件数 N（对 + 错）
     * @param severitySum 错误事件的 Σ(严重度 × 衰减)
     * @return 0—1 的掌握度；无事件返回 0
     */
    public double mastery(int events, double severitySum) {
        if (events <= 0) {
            return 0.0;
        }
        double value = 1.0 - severitySum / Math.max(1, events);
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** 单条错误事件的严重度贡献，调用方累加即可。 */
    public double errorWeight(String errorType, LocalDateTime eventAt, LocalDateTime now) {
        return severity(errorType) * decay(eventAt, now);
    }
}

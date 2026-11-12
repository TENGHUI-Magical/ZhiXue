package com.zhixue.diagnosis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * 掌握度：Beta 后验 + 双侧时间衰减 + 90% 可信区间（docs/05 §2）。
 *
 * <p>旧公式 {@code 1 - Σ错误严重度/N} 只衰减错误、分母不变，长期不练会逼近 1.0。
 * 新公式把“答对”和“答错”都当作带各自事件时间的证据，两侧完全对称：</p>
 * <pre>
 * S = Σ decay(正确事件_i)
 * F = Σ(错因严重度_i × decay(错误事件_i))
 * posterior = Beta(α0 + S, β0 + F)，α0=β0=2（均值 0.5，弱先验）
 * mastery = posterior 均值；90% 区间 = Beta 的 5%/95% 分位
 * </pre>
 * <p>没有新证据时 S/F 随年龄一起衰减，后验回到 Beta(2,2)，区间变宽，不会逼近 1.0。
 * 正确侧不能用“正确数 × decay(最后证据)”统一乘：最后一条证据若是今日错误，
 * 会把 100 天前的正确证据零衰减保留，导致“以前会、最近又错”的薄弱点被系统性高估。
 * 纯函数，无数据库依赖；调用方按事件各自时间累加权重，再交给
 * {@link #estimate(double, double)}，教师端与学生端共用同一入口。</p>
 */
public final class MasteryCalculator {

    /** 错因严重度（docs/05 §2.2）；未标注/UNCLASSIFIED 取中位 0.8，不得高于最高严重度 */
    private static final Map<String, Double> SEVERITY = Map.of(
            "CONCEPT_CONFUSION", 1.0,
            "METHOD_MISSING", 1.0,
            "STEP_OMISSION", 0.8,
            "CALCULATION_ERROR", 0.6,
            "BOUNDARY_ERROR", 0.8);
    private static final double DEFAULT_SEVERITY = 0.8;
    private static final double PRIOR_SUCCESS = 2.0;
    private static final double PRIOR_FAILURE = 2.0;

    private final double halfLifeDays;

    public MasteryCalculator(double halfLifeDays) {
        this.halfLifeDays = halfLifeDays <= 0 ? 30.0 : halfLifeDays;
    }

    public record Estimate(double mean, double low, double high,
                           double successWeight, double failureWeight) {
    }

    /** 错因 → 严重度；未知/空/UNCLASSIFIED 取 0.8，低于最高档 1.0。 */
    public static double severity(String errorType) {
        if (errorType == null || errorType.isBlank()) {
            return DEFAULT_SEVERITY;
        }
        return SEVERITY.getOrDefault(errorType.trim().toUpperCase(Locale.ROOT), DEFAULT_SEVERITY);
    }

    /** 时间衰减：decay(d)=0.5^(d/halfLifeDays)，当天为 1。 */
    public double decay(LocalDateTime eventAt, LocalDateTime now) {
        if (eventAt == null || now == null) {
            return 1.0;
        }
        long days = Math.max(0, Duration.between(eventAt, now).toDays());
        return Math.pow(0.5, days / halfLifeDays);
    }

    /**
     * Beta 后验估计。
     *
     * @param successWeight 衰减后的正确证据权重
     * @param failureWeight 衰减后的错误严重度和
     */
    public Estimate estimate(double successWeight, double failureWeight) {
        double s = Math.max(0.0, successWeight);
        double f = Math.max(0.0, failureWeight);
        double alpha = PRIOR_SUCCESS + s;
        double beta = PRIOR_FAILURE + f;
        double mean = alpha / (alpha + beta);
        double low = betaQuantile(0.05, alpha, beta);
        double high = betaQuantile(0.95, alpha, beta);
        return new Estimate(mean, low, high, s, f);
    }

    /** 后验均值；successWeight/failureWeight 由调用方按事件逐条衰减后求和。 */
    public double mastery(double successWeight, double failureWeight) {
        return estimate(successWeight, failureWeight).mean();
    }

    /**
     * 仅后验均值的轻量方法（P1-4）：直接算 alpha/(alpha+beta)，alpha=2+S、beta=2+F，
     * 与 {@link #estimate(double, double)} 的 mean、及 {@link #mastery(double, double)}
     * 数值逐位等价（同一 clamp 规则、同一运算顺序），但省去 0.05/0.95 两条 Beta 分位数
     * （各 80 次二分、每次最多 200 次连分式迭代）的开销。
     * 供聚合热路径（MasteryAggregationService.toList，每学生×每知识点各调一次）使用；
     * 原 {@link #mastery(double, double)} 保留不动，其它调用方与需要可信区间的场景不受影响。
     */
    public double masteryMean(double successWeight, double failureWeight) {
        double s = Math.max(0.0, successWeight);
        double f = Math.max(0.0, failureWeight);
        double alpha = PRIOR_SUCCESS + s;
        double beta = PRIOR_FAILURE + f;
        return alpha / (alpha + beta);
    }

    /** 单条正确事件的时间权重，调用方按事件各自时间累加即可。 */
    public double correctWeight(LocalDateTime eventAt, LocalDateTime now) {
        return decay(eventAt, now);
    }

    /** 单条错误事件的严重度贡献，调用方累加即可。 */
    public double errorWeight(String errorType, LocalDateTime eventAt, LocalDateTime now) {
        return severity(errorType) * decay(eventAt, now);
    }

    // ---------- Beta 分位：纯 Java 数值实现，避免新增依赖 ----------

    private static double betaQuantile(double p, double a, double b) {
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (regularizedBeta(mid, a, b) < p) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    private static double regularizedBeta(double x, double a, double b) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        double bt = Math.exp(logGamma(a + b) - logGamma(a) - logGamma(b)
                + a * Math.log(x) + b * Math.log(1.0 - x));
        if (x < (a + 1.0) / (a + b + 2.0)) {
            return bt * betaContinuedFraction(a, b, x) / a;
        }
        return 1.0 - bt * betaContinuedFraction(b, a, 1.0 - x) / b;
    }

    private static double betaContinuedFraction(double a, double b, double x) {
        final int maxIterations = 200;
        final double eps = 3e-12;
        final double fpMin = 1e-300;
        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;
        double c = 1.0;
        double d = 1.0 - qab * x / qap;
        if (Math.abs(d) < fpMin) d = fpMin;
        d = 1.0 / d;
        double h = d;
        for (int m = 1; m <= maxIterations; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < fpMin) d = fpMin;
            c = 1.0 + aa / c;
            if (Math.abs(c) < fpMin) c = fpMin;
            d = 1.0 / d;
            h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1.0 + aa * d;
            if (Math.abs(d) < fpMin) d = fpMin;
            c = 1.0 + aa / c;
            if (Math.abs(c) < fpMin) c = fpMin;
            d = 1.0 / d;
            double del = d * c;
            h *= del;
            if (Math.abs(del - 1.0) < eps) break;
        }
        return h;
    }

    private static double logGamma(double x) {
        double[] c = {
            76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5
        };
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        double ser = 1.000000000190015;
        for (int j = 0; j < 6; j++) {
            ser += c[j] / ++y;
        }
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}

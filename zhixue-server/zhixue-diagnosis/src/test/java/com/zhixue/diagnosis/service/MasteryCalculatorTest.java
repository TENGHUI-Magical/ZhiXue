package com.zhixue.diagnosis.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 掌握度公式单测：期望值全部按 docs/05 §2.3 手算/SciPy 独立计算后写死，
 * 不用被测的 MasteryCalculator 计算期望值（避免同义反复）。
 */
class MasteryCalculatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final MasteryCalculator calculator = new MasteryCalculator(30);

    @Test
    void noEvidenceReturnsPriorAndWideInterval() {
        MasteryCalculator.Estimate estimate = calculator.estimate(0, 0);
        assertEquals(0.5, estimate.mean(), 1e-9);
        assertTrue(estimate.high() - estimate.low() > 0.7);
    }

    /**
     * 自审补：防御分支——null 时间按最新证据处理（衰减 1.0）、未知/空错因严重度取中位 0.8、
     * 负权重（脏数据）clamp 到 0 回到先验。这些分支此前无测试锁定。
     */
    @Test
    void defensiveInputsAreClampedToSafeDefaults() {
        assertEquals(1.0, calculator.decay(null, T0), 1e-12);
        assertEquals(1.0, calculator.decay(T0, null), 1e-12);
        assertEquals(0.8, MasteryCalculator.severity(null), 1e-12);
        assertEquals(0.8, MasteryCalculator.severity(""), 1e-12);
        assertEquals(0.8, MasteryCalculator.severity("NOT_A_TYPE"), 1e-12);
        MasteryCalculator.Estimate clamped = calculator.estimate(-1.0, -2.0);
        assertEquals(0.5, clamped.mean(), 1e-9);
    }

    @Test
    void decayIsHalfAtHalfLifeAndContinuousPerDay() {
        assertEquals(0.5, calculator.decay(T0, T0.plusDays(30)), 1e-12);
        // 100 天 = 3⅓ 个半衰期 → 0.5^(100/30) = 0.0992125657
        assertEquals(0.0992125657, calculator.decay(T0, T0.plusDays(100)), 1e-9);
    }

    /**
     * 衰减天数按“满 24 小时”截断，与 SQL {@code TIMESTAMPDIFF(DAY, created_at, now)} 同口径。
     * 锁死跨夜 2 小时（23:00→次日 01:00）不算 1 天，避免 DATEDIFF 日历口径回潮。
     */
    @Test
    void decayCountsFull24hPeriodsNotCalendarDays() {
        // 跨夜 2 小时：0 天 → 衰减 = 1
        assertEquals(1.0, calculator.decay(T0, T0.plusHours(2)), 1e-9);
        // 23 小时：不足 24h → 0 天 → 衰减 = 1
        assertEquals(1.0, calculator.decay(T0, T0.plusHours(23)), 1e-9);
        // 25 小时：满 1 个 24h 周期 → 衰减 = 0.5^(1/30)
        assertEquals(Math.pow(0.5, 1.0 / 30.0), calculator.decay(T0, T0.plusHours(25)), 1e-9);
        // 49 小时：满 2 个 24h 周期 → 衰减 = 0.5^(2/30)
        assertEquals(Math.pow(0.5, 2.0 / 30.0), calculator.decay(T0, T0.plusHours(49)), 1e-9);
    }

    @Test
    void oldCorrectPlusRecentErrorIsNotOverestimated() {
        // 手算：10 次正确在 100 天前 → S=10×0.5^(100/30)=0.9921256575；
        // 今日 1 次概念混淆 → F=1.0；Beta(2.9921256575, 3) mean=0.4993429425。
        MasteryCalculator.Estimate estimate = calculator.estimate(0.9921256575, 1.0);
        assertEquals(0.4993429425, estimate.mean(), 1e-6);
        // 旧口径“正确数 × decay(最后证据)”会得到 Beta(12,3)=0.800，必须显著更低
        assertTrue(estimate.mean() < 0.55);
    }

    @Test
    void oldErrorPlusRecentCorrectIsNotAnchoredByRecentActivity() {
        // 手算：今日 2 次正确 → S=2；90 天前 1 次概念混淆 → F=0.5^3=0.125；
        // Beta(4, 2.125) mean=0.6530612245。
        assertEquals(0.6530612245, calculator.estimate(2.0, 0.125).mean(), 1e-6);
        // 若错误也按“最后证据=今天”统一衰减，会得到 4/(4+3)=0.571，口径必须区分
        assertTrue(calculator.estimate(2.0, 0.125).mean() > 0.60);
    }

    @Test
    void longTermOnlyCorrectDecaysButDoesNotCollapse() {
        // 手算：10 次正确在 90 天前 → S=10×0.125=1.25；Beta(3.25, 2) mean=0.6190476190。
        assertEquals(0.6190476190, calculator.estimate(1.25, 0.0).mean(), 1e-6);
        assertTrue(calculator.estimate(1.25, 0.0).mean() > 0.5);
    }

    @Test
    void intervalWidensWhenEvidenceAges() {
        MasteryCalculator.Estimate fresh = calculator.estimate(6.0, 0);
        MasteryCalculator.Estimate stale = calculator.estimate(0.75, 0);
        assertTrue(stale.high() - stale.low() > fresh.high() - fresh.low());
    }

    @Test
    void unclassifiedSeverityIsDefaultNotMaximum() {
        assertEquals(0.8, MasteryCalculator.severity("UNCLASSIFIED"), 1e-9);
        assertEquals(0.8, MasteryCalculator.severity(null), 1e-9);
        assertEquals(1.0, MasteryCalculator.severity("CONCEPT_CONFUSION"), 1e-9);
    }
}

package com.zhixue.diagnosis.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryCalculatorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final MasteryCalculator calculator = new MasteryCalculator(30);

    @Test
    void noEvidenceReturnsPriorAndWideInterval() {
        MasteryCalculator.Estimate estimate = calculator.estimate(0, 0, null, T0);
        assertEquals(0.5, estimate.mean(), 1e-9);
        assertTrue(estimate.high() - estimate.low() > 0.7);
    }

    @Test
    void singleErrorFallsBackTowardPriorOverTime() {
        MasteryCalculator.Estimate day0 = calculator.estimate(0,
                calculator.errorWeight("CONCEPT_CONFUSION", T0, T0), T0, T0);
        MasteryCalculator.Estimate day30 = calculator.estimate(0,
                calculator.errorWeight("CONCEPT_CONFUSION", T0, T0.plusDays(30)), T0, T0.plusDays(30));
        MasteryCalculator.Estimate day60 = calculator.estimate(0,
                calculator.errorWeight("CONCEPT_CONFUSION", T0, T0.plusDays(60)), T0, T0.plusDays(60));
        MasteryCalculator.Estimate day90 = calculator.estimate(0,
                calculator.errorWeight("CONCEPT_CONFUSION", T0, T0.plusDays(90)), T0, T0.plusDays(90));
        assertTrue(day0.mean() < day30.mean());
        assertTrue(day30.mean() < day60.mean());
        assertTrue(day60.mean() < day90.mean());
        assertTrue(day90.mean() < 0.5);
        assertTrue(day90.high() - day90.low() > day0.high() - day0.low());
    }

    @Test
    void manyCorrectDecayButDoNotCollapseToZero() {
        MasteryCalculator.Estimate day0 = calculator.estimate(10, 0, T0, T0);
        MasteryCalculator.Estimate day30 = calculator.estimate(10, 0, T0, T0.plusDays(30));
        MasteryCalculator.Estimate day60 = calculator.estimate(10, 0, T0, T0.plusDays(60));
        MasteryCalculator.Estimate day90 = calculator.estimate(10, 0, T0, T0.plusDays(90));
        assertTrue(day0.mean() > day30.mean());
        assertTrue(day30.mean() > day60.mean());
        assertTrue(day60.mean() > day90.mean());
        assertTrue(day90.mean() > 0.5);
        assertTrue(day90.mean() > 0.55);
    }

    @Test
    void intervalWidensWhenEvidenceAges() {
        MasteryCalculator.Estimate fresh = calculator.estimate(6, 0, T0, T0);
        MasteryCalculator.Estimate stale = calculator.estimate(6, 0, T0, T0.plusDays(90));
        assertTrue(stale.high() - stale.low() > fresh.high() - fresh.low());
    }

    @Test
    void unclassifiedSeverityIsDefaultNotMaximum() {
        assertEquals(0.8, MasteryCalculator.severity("UNCLASSIFIED"), 1e-9);
        assertEquals(0.8, MasteryCalculator.severity(null), 1e-9);
        assertEquals(1.0, MasteryCalculator.severity("CONCEPT_CONFUSION"), 1e-9);
    }

    @Test
    void printsOldVersusNewSamplesForDefense() {
        System.out.println("[mastery] scenario,day,old,new");
        for (int day : new int[]{0, 30, 60, 90}) {
            LocalDateTime at = T0.plusDays(day);
            double decay = calculator.decay(T0, at);
            double oldSingleError = 1.0 - decay;
            double oldTenCorrect = 1.0;
            double newSingleError = calculator.estimate(0,
                    calculator.errorWeight("CONCEPT_CONFUSION", T0, at), T0, at).mean();
            double newTenCorrect = calculator.estimate(10, 0, T0, at).mean();
            System.out.printf("[mastery] single-error,%d,%.4f,%.4f%n", day, oldSingleError, newSingleError);
            System.out.printf("[mastery] ten-correct,%d,%.4f,%.4f%n", day, oldTenCorrect, newTenCorrect);
        }
    }
}

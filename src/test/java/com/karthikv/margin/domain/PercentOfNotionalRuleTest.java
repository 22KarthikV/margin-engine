package com.karthikv.margin.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PercentOfNotionalRuleTest {

    private final PercentOfNotionalRule rule = new PercentOfNotionalRule(new BigDecimal("0.10"));

    @Test
    void longPositionIsQuantityTimesPriceTimesRate() {
        assertEquals(0, new BigDecimal("100.00").compareTo(rule.requirement("X", 10, new BigDecimal("100"))));
    }

    @Test
    void shortPositionUsesAbsoluteQuantity() {
        assertEquals(0, new BigDecimal("100.00").compareTo(rule.requirement("X", -10, new BigDecimal("100"))));
    }

    @Test
    void flatPositionRequiresNothing() {
        assertEquals(0, BigDecimal.ZERO.compareTo(rule.requirement("X", 0, new BigDecimal("100"))));
    }
}

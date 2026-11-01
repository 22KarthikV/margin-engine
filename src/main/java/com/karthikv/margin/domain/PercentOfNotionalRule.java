package com.karthikv.margin.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Requirement = |net quantity| x price x rate, the same for every instrument. */
public final class PercentOfNotionalRule implements MarginRule {

    private final BigDecimal rate;

    public PercentOfNotionalRule(BigDecimal rate) {
        this.rate = Objects.requireNonNull(rate, "rate");
        if (rate.signum() < 0) {
            throw new IllegalArgumentException("rate must not be negative");
        }
    }

    @Override
    public BigDecimal requirement(String instrument, long netQuantity, BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(Math.abs(netQuantity))).multiply(rate);
    }
}

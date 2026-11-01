package com.karthikv.margin.domain;

import java.math.BigDecimal;

/** Computes the margin one position requires. Pure function: no state, no I/O. */
@FunctionalInterface
public interface MarginRule {

    BigDecimal requirement(String instrument, long netQuantity, BigDecimal price);
}

package com.karthikv.margin.domain;

import java.math.BigDecimal;

/**
 * Raised once each time an account moves from covered to under-collateralised.
 *
 * @param callId deterministic ({@code account#n}); storing it with the id as primary key makes
 *               downstream writes idempotent
 */
public record MarginCall(String callId, String account, BigDecimal marginRequired, BigDecimal collateral) {
}

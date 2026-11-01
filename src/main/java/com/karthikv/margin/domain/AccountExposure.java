package com.karthikv.margin.domain;

import java.math.BigDecimal;

/**
 * Snapshot pushed to the dashboard after every applied trade that touches the account.
 *
 * @param emittedAtMs wall-clock time the engine produced the snapshot; the dashboard subtracts it
 *                    from its own clock to show end-to-end lag
 */
public record AccountExposure(
        String account,
        BigDecimal marginRequired,
        BigDecimal collateral,
        boolean inBreach,
        long emittedAtMs) {
}

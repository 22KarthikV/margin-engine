package com.karthikv.margin.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One trade as it arrives from Kafka.
 *
 * @param eventId  globally unique id; the engine ignores a second event with the same id
 * @param sequence strictly increasing per (account, instrument) stream; the engine ignores
 *                 events whose sequence is not newer than the last one applied to that position
 */
public record TradeEvent(
        String eventId,
        long sequence,
        String account,
        String instrument,
        Side side,
        long quantity,
        BigDecimal price) {

    public TradeEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(price, "price");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }

    /** Positive for BUY, negative for SELL. */
    public long signedQuantity() {
        return side == Side.BUY ? quantity : -quantity;
    }
}

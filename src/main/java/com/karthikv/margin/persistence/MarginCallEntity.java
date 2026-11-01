package com.karthikv.margin.persistence;

import com.karthikv.margin.domain.MarginCall;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Margin calls keyed by their deterministic id, so saving the same call twice is a no-op overwrite. */
@Entity
@Table(name = "margin_calls")
public class MarginCallEntity {

    @Id
    @Column(name = "call_id")
    private String callId;

    @Column(name = "account", nullable = false)
    private String account;

    @Column(name = "margin_required", nullable = false, precision = 24, scale = 6)
    private BigDecimal marginRequired;

    @Column(name = "collateral", nullable = false, precision = 24, scale = 6)
    private BigDecimal collateral;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarginCallEntity() {
        // required by JPA
    }

    public static MarginCallEntity from(MarginCall call) {
        MarginCallEntity entity = new MarginCallEntity();
        entity.callId = call.callId();
        entity.account = call.account();
        entity.marginRequired = call.marginRequired();
        entity.collateral = call.collateral();
        entity.createdAt = Instant.now();
        return entity;
    }

    public String getCallId() {
        return callId;
    }

    public String getAccount() {
        return account;
    }

    public BigDecimal getMarginRequired() {
        return marginRequired;
    }

    public BigDecimal getCollateral() {
        return collateral;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

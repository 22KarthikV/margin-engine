CREATE TABLE margin_calls (
    call_id         VARCHAR(128)   PRIMARY KEY,
    account         VARCHAR(64)    NOT NULL,
    margin_required NUMERIC(24, 6) NOT NULL,
    collateral      NUMERIC(24, 6) NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_margin_calls_account ON margin_calls (account);

CREATE TABLE wallets (
                         id UUID PRIMARY KEY,
                         user_id BIGINT NOT NULL UNIQUE,
                         currency VARCHAR(3) NOT NULL,
                         available_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
                         reserved_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
                         version BIGINT NOT NULL DEFAULT 0,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT wallet_available_balance_non_negative
                             CHECK (available_balance >= 0),

                         CONSTRAINT wallet_reserved_balance_non_negative
                             CHECK (reserved_balance >= 0)
);

CREATE TABLE processed_messages (
                                    event_id VARCHAR(64) PRIMARY KEY,
                                    event_type VARCHAR(100) NOT NULL,
                                    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
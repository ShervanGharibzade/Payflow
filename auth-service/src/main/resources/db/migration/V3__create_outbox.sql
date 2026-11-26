CREATE TABLE outbox_events (
                               id            UUID PRIMARY KEY,
                               topic         VARCHAR(100)  NOT NULL,
                               message_key   VARCHAR(100)  NOT NULL,
                               event_type    VARCHAR(100)  NOT NULL,
                               payload       VARCHAR(4000) NOT NULL,
                               created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               published_at  TIMESTAMP
);

CREATE INDEX idx_outbox_published_at ON outbox_events (published_at);
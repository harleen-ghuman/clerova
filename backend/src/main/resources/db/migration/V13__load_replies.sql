-- Trucking phase 2: replies to brokers, and the dispatcher's latest update on a load.

-- A short update the dispatcher typed, like "Loaded, ETA Phoenix 6 AM"
ALTER TABLE loads ADD COLUMN status_note VARCHAR(500);

-- Drafted and sent replies on a load
CREATE TABLE load_replies (
    id                   UUID                        NOT NULL PRIMARY KEY,
    load_id              UUID                        NOT NULL REFERENCES loads (id),
    -- The email being answered
    inbound_message_id   UUID REFERENCES inbound_messages (id),
    to_email             VARCHAR(320)                NOT NULL,
    subject              VARCHAR(1000),
    kind                 VARCHAR(30)                 NOT NULL,
    summary              VARCHAR(500),
    body                 TEXT                        NOT NULL,
    status               VARCHAR(20)                 NOT NULL,
    provider_message_id  VARCHAR(255),
    created_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    sent_at              TIMESTAMP(6) WITH TIME ZONE,
    version              BIGINT
);

CREATE INDEX idx_load_replies_load ON load_replies (load_id);
CREATE INDEX idx_inbound_messages_load ON inbound_messages (load_id);

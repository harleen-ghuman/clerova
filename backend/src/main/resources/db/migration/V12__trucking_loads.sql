-- Trucking workspaces: rate confirmations become loads.

-- The text of a PDF attachment, read when a trucking workspace's email
-- arrives, so the AI can read a rate con without fetching the file again.
ALTER TABLE inbound_attachments ADD COLUMN extracted_text TEXT;

CREATE TABLE brokers (
    id                UUID                        NOT NULL PRIMARY KEY,
    organization_id   UUID                        NOT NULL REFERENCES organizations (id),
    name              VARCHAR(255)                NOT NULL,
    mc_number         VARCHAR(20),
    email             VARCHAR(320),
    phone             VARCHAR(50),
    -- The FMCSA check: ACTIVE, NOT_AUTHORIZED or NOT_FOUND
    fmcsa_status      VARCHAR(30),
    fmcsa_name        VARCHAR(255),
    fmcsa_checked_at  TIMESTAMP(6) WITH TIME ZONE,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version           BIGINT
);

CREATE INDEX idx_brokers_org_mc ON brokers (organization_id, mc_number);

CREATE TABLE loads (
    id                      UUID                        NOT NULL PRIMARY KEY,
    organization_id         UUID                        NOT NULL REFERENCES organizations (id),
    broker_id               UUID REFERENCES brokers (id),
    load_number             VARCHAR(100),
    status                  VARCHAR(30)                 NOT NULL,
    equipment               VARCHAR(100),
    -- Amounts in cents
    rate_cents              BIGINT,
    agreed_rate_cents       BIGINT,
    detention_free_minutes  INTEGER,
    detention_rate_cents    BIGINT,
    detention_rule          TEXT,
    tonu_cents              BIGINT,
    payment_terms           TEXT,
    paperwork_rule          TEXT,
    rate_con_file           VARCHAR(500),
    created_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version                 BIGINT
);

CREATE INDEX idx_loads_org_created ON loads (organization_id, created_at);
CREATE INDEX idx_loads_org_number ON loads (organization_id, load_number);

CREATE TABLE load_stops (
    id           UUID         NOT NULL PRIMARY KEY,
    load_id      UUID         NOT NULL REFERENCES loads (id),
    position     INTEGER      NOT NULL,
    kind         VARCHAR(20)  NOT NULL,
    place        TEXT,
    appointment  VARCHAR(500)
);

CREATE INDEX idx_load_stops_load ON load_stops (load_id);

-- Things to check on a rate con before signing it
CREATE TABLE load_flags (
    id           UUID                        NOT NULL PRIMARY KEY,
    load_id      UUID                        NOT NULL REFERENCES loads (id),
    code         VARCHAR(50)                 NOT NULL,
    level        VARCHAR(10)                 NOT NULL,
    title        VARCHAR(255)                NOT NULL,
    detail       TEXT,
    source_text  TEXT,
    resolved_at  TIMESTAMP(6) WITH TIME ZONE,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_load_flags_load ON load_flags (load_id);

-- The load an email belongs to (trucking workspaces)
ALTER TABLE inbound_messages ADD COLUMN load_id UUID REFERENCES loads (id);

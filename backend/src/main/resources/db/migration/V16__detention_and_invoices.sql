-- Trucking phase 3: carrier details, detention and other charges, invoices.

-- What the rate con says about getting paid
ALTER TABLE loads ADD COLUMN detention_max_cents BIGINT;
ALTER TABLE loads ADD COLUMN quick_pay_percent NUMERIC(5, 2);
ALTER TABLE loads ADD COLUMN invoice_email VARCHAR(320);

-- When the truck got to a stop and left it
ALTER TABLE load_stops ADD COLUMN arrived_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE load_stops ADD COLUMN left_at TIMESTAMP(6) WITH TIME ZONE;

-- The carrier's details printed on invoices, one row per workspace
CREATE TABLE carrier_profiles (
    organization_id      UUID                        NOT NULL PRIMARY KEY REFERENCES organizations (id),
    legal_name           VARCHAR(255),
    mc_number            VARCHAR(20),
    dot_number           VARCHAR(20),
    address              VARCHAR(1000),
    phone                VARCHAR(50),
    email                VARCHAR(320),
    invoice_prefix       VARCHAR(20),
    next_invoice_number  INTEGER                     NOT NULL,
    uses_factoring       BOOLEAN                     NOT NULL,
    factor_name          VARCHAR(255),
    factor_address       VARCHAR(1000),
    factor_email         VARCHAR(320),
    updated_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version              BIGINT
);

-- Detention, lumper, TONU, layover and other charges on a load
CREATE TABLE load_charges (
    id            UUID                        NOT NULL PRIMARY KEY,
    load_id       UUID                        NOT NULL REFERENCES loads (id),
    stop_id       UUID REFERENCES load_stops (id) ON DELETE SET NULL,
    kind          VARCHAR(20)                 NOT NULL,
    description   VARCHAR(255),
    amount_cents  BIGINT                      NOT NULL,
    status        VARCHAR(20)                 NOT NULL,
    claimed_at    TIMESTAMP(6) WITH TIME ZONE,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version       BIGINT
);

CREATE INDEX idx_load_charges_load ON load_charges (load_id);

CREATE TABLE invoices (
    id                  UUID                        NOT NULL PRIMARY KEY,
    organization_id     UUID                        NOT NULL REFERENCES organizations (id),
    load_id             UUID                        NOT NULL REFERENCES loads (id),
    number              VARCHAR(40)                 NOT NULL,
    status              VARCHAR(20)                 NOT NULL,
    -- Who gets the invoice and who is paid: FACTOR or BROKER
    send_to             VARCHAR(20)                 NOT NULL,
    bill_to             VARCHAR(1000),
    remit_to            VARCHAR(1000),
    total_cents         BIGINT                      NOT NULL,
    quick_pay           BOOLEAN                     NOT NULL,
    quick_pay_fee_cents BIGINT,
    -- How it went out: EMAIL or PORTAL (downloaded and uploaded by hand)
    sent_via            VARCHAR(20),
    sent_email          VARCHAR(320),
    sent_at             TIMESTAMP(6) WITH TIME ZONE,
    due_date            DATE,
    paid_at             TIMESTAMP(6) WITH TIME ZONE,
    paid_cents          BIGINT,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version             BIGINT,
    CONSTRAINT uq_invoices_org_number UNIQUE (organization_id, number)
);

CREATE INDEX idx_invoices_org_created ON invoices (organization_id, created_at);
CREATE INDEX idx_invoices_load ON invoices (load_id);

CREATE TABLE invoice_lines (
    id            UUID          NOT NULL PRIMARY KEY,
    invoice_id    UUID          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    position      INTEGER       NOT NULL,
    description   VARCHAR(255)  NOT NULL,
    amount_cents  BIGINT        NOT NULL
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines (invoice_id);

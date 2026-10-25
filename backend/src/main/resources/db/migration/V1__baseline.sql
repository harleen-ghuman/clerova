-- Baseline schema, matching what Hibernate's ddl-auto=update generated before
-- Flyway was introduced. Existing databases are baselined at version 1 and skip
-- this script; fresh databases run it.

CREATE TABLE work_items (
    id               UUID                        NOT NULL PRIMARY KEY,
    address          VARCHAR(255),
    category         VARCHAR(255),
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    organization_id  VARCHAR(255)                NOT NULL,
    original_message TEXT                        NOT NULL,
    priority         VARCHAR(255),
    status           VARCHAR(255),
    summary          TEXT,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE proposed_actions (
    id               UUID                        NOT NULL PRIMARY KEY,
    action_type      VARCHAR(255)                NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    drafted_response TEXT,
    reasoning        TEXT,
    status           VARCHAR(255)                NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    work_item_id     UUID                        NOT NULL REFERENCES work_items (id)
);

CREATE TABLE maintenance_requests (
    id                 UUID                        NOT NULL PRIMARY KEY,
    address            VARCHAR(255),
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    description        VARCHAR(2000)               NOT NULL,
    notes              TEXT,
    organization_id    VARCHAR(255)                NOT NULL,
    priority           VARCHAR(255)                NOT NULL,
    status             VARCHAR(255)                NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    proposed_action_id UUID                        NOT NULL UNIQUE REFERENCES proposed_actions (id),
    work_item_id       UUID                        NOT NULL REFERENCES work_items (id)
);

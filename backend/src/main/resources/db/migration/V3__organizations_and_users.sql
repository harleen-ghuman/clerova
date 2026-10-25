-- Organizations (tenants) and the users that belong to them.
-- Users authenticate with Supabase Auth; app_users.id is the Supabase user id.

CREATE TABLE organizations (
    id                     UUID                        NOT NULL PRIMARY KEY,
    name                   VARCHAR(255)                NOT NULL,
    industry               VARCHAR(50)                 NOT NULL,
    company_size           VARCHAR(50),
    referral_source        VARCHAR(255),
    plan                   VARCHAR(50)                 NOT NULL,
    trial_ends_at          TIMESTAMP(6) WITH TIME ZONE,
    ai_monthly_limit       INTEGER                     NOT NULL,
    ai_usage_period        VARCHAR(7),
    ai_usage_count         INTEGER                     NOT NULL DEFAULT 0,
    created_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE app_users (
    id               UUID                        NOT NULL PRIMARY KEY,
    email            VARCHAR(320)                NOT NULL,
    full_name        VARCHAR(255),
    organization_id  UUID                        NOT NULL REFERENCES organizations (id),
    role             VARCHAR(50)                 NOT NULL,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_app_users_organization ON app_users (organization_id);

-- Everything created before accounts existed came from the public demo.
-- It moves to a demo organization so it stays available but isolated.
INSERT INTO organizations (
    id, name, industry, plan, ai_monthly_limit, ai_usage_count, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Demo Property Management',
    'PROPERTY_MANAGEMENT',
    'INTERNAL',
    0,
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- organization_id was free-form text set by the client. Replace it with a
-- UUID reference; all existing rows belong to the demo organization.
ALTER TABLE work_items DROP COLUMN organization_id;
ALTER TABLE work_items
    ADD COLUMN organization_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE work_items ALTER COLUMN organization_id DROP DEFAULT;
ALTER TABLE work_items
    ADD CONSTRAINT fk_work_items_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id);

ALTER TABLE maintenance_requests DROP COLUMN organization_id;
ALTER TABLE maintenance_requests
    ADD COLUMN organization_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE maintenance_requests ALTER COLUMN organization_id DROP DEFAULT;
ALTER TABLE maintenance_requests
    ADD CONSTRAINT fk_maintenance_requests_organization
    FOREIGN KEY (organization_id) REFERENCES organizations (id);

DROP INDEX idx_work_items_status_created;
CREATE INDEX idx_work_items_org_status_created ON work_items (organization_id, status, created_at);
CREATE INDEX idx_maintenance_requests_org_created ON maintenance_requests (organization_id, created_at);

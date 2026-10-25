-- Hibernate generated CHECK constraints listing every enum value. They block
-- adding new statuses, and enum values are already validated by the application.
ALTER TABLE work_items DROP CONSTRAINT IF EXISTS work_items_category_check;
ALTER TABLE work_items DROP CONSTRAINT IF EXISTS work_items_priority_check;
ALTER TABLE work_items DROP CONSTRAINT IF EXISTS work_items_status_check;
ALTER TABLE proposed_actions DROP CONSTRAINT IF EXISTS proposed_actions_action_type_check;
ALTER TABLE proposed_actions DROP CONSTRAINT IF EXISTS proposed_actions_status_check;
ALTER TABLE maintenance_requests DROP CONSTRAINT IF EXISTS maintenance_requests_priority_check;
ALTER TABLE maintenance_requests DROP CONSTRAINT IF EXISTS maintenance_requests_status_check;

-- Optimistic locking, so concurrent generate/approve/reject requests cannot
-- create duplicate proposals or execute the same action twice.
ALTER TABLE work_items ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE proposed_actions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- The response text the reviewer actually approved (may differ from the AI draft).
ALTER TABLE proposed_actions ADD COLUMN final_response TEXT;

CREATE INDEX idx_work_items_status_created ON work_items (status, created_at);
CREATE INDEX idx_proposed_actions_work_item ON proposed_actions (work_item_id, created_at);
CREATE INDEX idx_maintenance_requests_work_item ON maintenance_requests (work_item_id);

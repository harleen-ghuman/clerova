-- New suggestions and drafts asked for since the last email in either direction
ALTER TABLE work_items ADD COLUMN suggestions_used SMALLINT NOT NULL DEFAULT 0;

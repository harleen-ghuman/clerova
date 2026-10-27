-- The sender's new email during a job looks urgent (safety or active damage).
ALTER TABLE work_items ADD COLUMN unread_reply_urgent BOOLEAN NOT NULL DEFAULT FALSE;

-- When the sender wrote again while the request's job is in progress, and
-- nobody has answered or dismissed it yet.
ALTER TABLE work_items ADD COLUMN unread_reply_at TIMESTAMP(6) WITH TIME ZONE;

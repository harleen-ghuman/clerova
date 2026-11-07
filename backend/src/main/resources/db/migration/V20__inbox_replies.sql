-- Replies from the Inbox: AI drafts asked for on an email (10 at most) and
-- when the carrier last replied to it from Clerova.
alter table inbound_messages add column reply_drafts_used integer not null default 0;
alter table inbound_messages add column replied_at timestamp with time zone;

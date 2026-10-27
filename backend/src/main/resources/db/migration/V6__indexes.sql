-- Indexes for lookups that grow with use (flagged by Supabase's advisor):
-- the Today page's replies in the last 7 days, and foreign keys used when
-- loading or removing an organization's data.
CREATE INDEX IF NOT EXISTS idx_outbound_messages_org_sent
    ON outbound_messages (organization_id, sent_at);

CREATE INDEX IF NOT EXISTS idx_outbound_messages_proposed_action
    ON outbound_messages (proposed_action_id);

CREATE INDEX IF NOT EXISTS idx_work_item_events_org
    ON work_item_events (organization_id);

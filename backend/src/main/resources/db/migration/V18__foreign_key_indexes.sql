-- Indexes for foreign keys the database advisor found without one, so
-- looking up or removing a broker, stop or email doesn't scan whole tables.
CREATE INDEX IF NOT EXISTS idx_loads_broker ON loads (broker_id);
CREATE INDEX IF NOT EXISTS idx_load_replies_inbound_message ON load_replies (inbound_message_id);
CREATE INDEX IF NOT EXISTS idx_load_charges_stop ON load_charges (stop_id);

-- The carrier's time zone, for invoice dates, due dates and the monthly
-- export (until now always Pacific). Null means Pacific.
ALTER TABLE organizations ADD COLUMN time_zone VARCHAR(64);

-- Which stop or charge an email from a load was about, so the load page can
-- show "Arrival notice emailed to ... at ..." next to it.
ALTER TABLE load_replies ADD COLUMN stop_id UUID REFERENCES load_stops (id) ON DELETE SET NULL;
ALTER TABLE load_replies ADD COLUMN charge_id UUID REFERENCES load_charges (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_load_replies_stop ON load_replies (stop_id);
CREATE INDEX IF NOT EXISTS idx_load_replies_charge ON load_replies (charge_id);

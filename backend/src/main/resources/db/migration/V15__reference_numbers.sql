-- Numbers printed on a rate con (PO, pickup, BOL...) and on other documents,
-- so a BOL that carries the shipper's PO still finds its load
ALTER TABLE loads ADD COLUMN reference_numbers VARCHAR(1000);
ALTER TABLE inbound_attachments ADD COLUMN reference_numbers VARCHAR(1000);
ALTER TABLE load_documents ADD COLUMN reference_numbers VARCHAR(1000);

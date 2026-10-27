-- The company's standard answers for suggested replies (JSON, see CompanyInfo)
ALTER TABLE organizations ADD COLUMN company_info TEXT;

-- Where a kept attachment is in storage; null means only its name was kept
ALTER TABLE inbound_attachments ADD COLUMN storage_path VARCHAR(500);

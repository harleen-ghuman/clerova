-- Where a copy of each reply sent from Clerova goes (BCC); null means off
ALTER TABLE organizations ADD COLUMN reply_copy_email VARCHAR(320);

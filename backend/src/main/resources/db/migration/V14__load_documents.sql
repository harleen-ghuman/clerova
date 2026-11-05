-- What each email attachment is (rate con, BOL, POD...), when Clerova read it
ALTER TABLE inbound_attachments ADD COLUMN document_kind VARCHAR(30);

-- Documents uploaded by hand: a BOL from a driver's phone, a POD from a portal
CREATE TABLE load_documents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    -- Null until it is filed under a load
    load_id UUID REFERENCES loads(id) ON DELETE CASCADE,
    kind VARCHAR(30) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_load_documents_load ON load_documents (load_id);
CREATE INDEX idx_load_documents_organization ON load_documents (organization_id, created_at);

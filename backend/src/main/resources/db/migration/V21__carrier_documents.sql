-- The carrier's own paperwork for a broker's carrier packet: W-9, authority,
-- insurance certificate, notice of assignment and the like.
create table carrier_documents (
    id uuid primary key,
    organization_id uuid not null references organizations(id) on delete cascade,
    kind varchar(30) not null,
    file_name varchar(500) not null,
    content_type varchar(100),
    size_bytes bigint not null,
    storage_path varchar(500) not null,
    expires_on date,
    uploaded_by uuid,
    created_at timestamp with time zone not null,
    version bigint
);

create index carrier_documents_organization_idx on carrier_documents (organization_id);

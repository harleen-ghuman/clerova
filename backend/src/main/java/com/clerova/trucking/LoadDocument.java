package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A document uploaded by hand and filed under a load (or waiting to be). */
@Entity
@Table(name = "load_documents")
public class LoadDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    private UUID loadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentKind kind;

    @Column(nullable = false, length = 500)
    private String fileName;

    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 500)
    private String storagePath;

    private UUID uploadedBy;

    @Column(length = 1000)
    private String referenceNumbers;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getLoadId() { return loadId; }
    public void setLoadId(UUID loadId) { this.loadId = loadId; }
    public DocumentKind getKind() { return kind; }
    public void setKind(DocumentKind kind) { this.kind = kind; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public UUID getUploadedBy() { return uploadedBy; }
    public String getReferenceNumbers() { return referenceNumbers; }
    public void setReferenceNumbers(String referenceNumbers) { this.referenceNumbers = referenceNumbers; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}

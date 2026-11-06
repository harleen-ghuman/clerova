package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One of the carrier's own documents for the carrier packet brokers ask for. */
@Entity
@Table(name = "carrier_documents")
public class CarrierDocument {

    /** What the document is, in the order a carrier packet usually goes. */
    public enum Kind {
        AUTHORITY("MC authority letter"),
        INSURANCE("Certificate of insurance"),
        W9("W-9"),
        NOA("Notice of assignment (factoring)"),
        VOID_CHECK("Voided check"),
        OTHER("Other");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(nullable = false, length = 500)
    private String fileName;

    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 500)
    private String storagePath;

    private LocalDate expiresOn;

    private UUID uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public UUID getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(UUID uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}

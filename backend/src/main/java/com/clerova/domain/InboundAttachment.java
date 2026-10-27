package com.clerova.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * An attachment on an inbound email. The file itself is in FileStorage at
 * storagePath; older or oversized files keep only their name.
 */
@Entity
@Table(name = "inbound_attachments")
public class InboundAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inbound_message_id", nullable = false)
    private InboundMessage inboundMessage;

    @Column(length = 500)
    private String fileName;

    private String contentType;

    private Long sizeBytes;

    /** Where the file is in storage; null when only the name was kept. */
    @Column(length = 500)
    private String storagePath;

    public UUID getId() {
        return id;
    }

    public InboundMessage getInboundMessage() {
        return inboundMessage;
    }

    public void setInboundMessage(InboundMessage inboundMessage) {
        this.inboundMessage = inboundMessage;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
}

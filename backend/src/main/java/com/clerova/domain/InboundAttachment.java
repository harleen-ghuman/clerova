package com.clerova.domain;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * Metadata for an attachment on an inbound email. File contents are not
 * stored yet.
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
}

package com.clerova.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * An email reply sent from Clerova after a user approved it.
 */
@Entity
@Table(name = "outbound_messages")
public class OutboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID workItemId;

    private UUID proposedActionId;

    @Column(nullable = false, length = 320)
    private String toEmail;

    @Column(nullable = false, length = 1000)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    private String providerMessageId;

    private UUID sentByUserId;

    @Column(nullable = false)
    private Instant sentAt;

    @PrePersist
    public void onCreate() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(UUID workItemId) {
        this.workItemId = workItemId;
    }

    public UUID getProposedActionId() {
        return proposedActionId;
    }

    public void setProposedActionId(UUID proposedActionId) {
        this.proposedActionId = proposedActionId;
    }

    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public UUID getSentByUserId() {
        return sentByUserId;
    }

    public void setSentByUserId(UUID sentByUserId) {
        this.sentByUserId = sentByUserId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

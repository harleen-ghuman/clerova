package com.clerova.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An email received at an organization's Clerova forwarding address.
 */
@Entity
@Table(name = "inbound_messages")
public class InboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    private UUID workItemId;

    @Column(nullable = false, length = 998)
    private String dedupeKey;

    private String providerMessageId;

    @Column(length = 998)
    private String messageIdHeader;

    @Column(length = 998)
    private String inReplyTo;

    @Column(name = "references_header", columnDefinition = "TEXT")
    private String references;

    private String mailboxHash;

    @Column(length = 320)
    private String fromEmail;

    private String fromName;

    @Column(columnDefinition = "TEXT")
    private String toAddresses;

    @Column(length = 1000)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String textBody;

    /** The new part of a reply, without quoted earlier messages. */
    @Column(columnDefinition = "TEXT")
    private String replyText;

    @Column(name = "is_automated", nullable = false)
    private boolean automated;

    @Column(nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InboundMessageStatus status;

    @Column(length = 1000)
    private String statusReason;

    @Column(nullable = false)
    private int attempts;

    private Instant nextAttemptAt;

    @OneToMany(mappedBy = "inboundMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InboundAttachment> attachments = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = InboundMessageStatus.RECEIVED;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    /** The text to show and analyze: the reply part when available. */
    public String getContentText() {
        return replyText != null && !replyText.isBlank() ? replyText : textBody;
    }

    public void addAttachment(InboundAttachment attachment) {
        attachment.setInboundMessage(this);
        attachments.add(attachment);
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

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getMessageIdHeader() {
        return messageIdHeader;
    }

    public void setMessageIdHeader(String messageIdHeader) {
        this.messageIdHeader = messageIdHeader;
    }

    public String getInReplyTo() {
        return inReplyTo;
    }

    public void setInReplyTo(String inReplyTo) {
        this.inReplyTo = inReplyTo;
    }

    public String getReferences() {
        return references;
    }

    public void setReferences(String references) {
        this.references = references;
    }

    public String getMailboxHash() {
        return mailboxHash;
    }

    public void setMailboxHash(String mailboxHash) {
        this.mailboxHash = mailboxHash;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getToAddresses() {
        return toAddresses;
    }

    public void setToAddresses(String toAddresses) {
        this.toAddresses = toAddresses;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getReplyText() {
        return replyText;
    }

    public void setReplyText(String replyText) {
        this.replyText = replyText;
    }

    public boolean isAutomated() {
        return automated;
    }

    public void setAutomated(boolean automated) {
        this.automated = automated;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public InboundMessageStatus getStatus() {
        return status;
    }

    public void setStatus(InboundMessageStatus status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public List<InboundAttachment> getAttachments() {
        return attachments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

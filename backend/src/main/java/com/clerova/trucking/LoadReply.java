package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A reply to a broker about a load: drafted by Clerova, sent once approved. */
@Entity
@Table(name = "load_replies")
public class LoadReply {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID loadId;

    private UUID inboundMessageId;

    /** The stop an arrival or detention notice was about. */
    private UUID stopId;

    /** The charge a claim was for. */
    private UUID chargeId;

    @Column(nullable = false)
    private String toEmail;

    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReplyKind kind;

    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReplyStatus status;

    private String providerMessageId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant sentAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();

        if (status == null) {
            status = ReplyStatus.DRAFT;
        }
    }

    public UUID getId() { return id; }
    public UUID getLoadId() { return loadId; }
    public void setLoadId(UUID loadId) { this.loadId = loadId; }
    public UUID getInboundMessageId() { return inboundMessageId; }
    public void setInboundMessageId(UUID inboundMessageId) { this.inboundMessageId = inboundMessageId; }
    public String getToEmail() { return toEmail; }
    public void setToEmail(String toEmail) { this.toEmail = toEmail; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public ReplyKind getKind() { return kind; }
    public void setKind(ReplyKind kind) { this.kind = kind; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public ReplyStatus getStatus() { return status; }
    public void setStatus(ReplyStatus status) { this.status = status; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public UUID getStopId() { return stopId; }
    public void setStopId(UUID stopId) { this.stopId = stopId; }
    public UUID getChargeId() { return chargeId; }
    public void setChargeId(UUID chargeId) { this.chargeId = chargeId; }
}

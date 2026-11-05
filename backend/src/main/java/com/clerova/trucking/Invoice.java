package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The invoice for a load. Amounts are in cents. */
@Entity
@Table(name = "invoices")
public class Invoice {

    /** Who gets the invoice and is paid. */
    public enum SendTo { FACTOR, BROKER }

    /** How it went out: emailed by Clerova, or uploaded to a portal by hand. */
    public enum SentVia { EMAIL, PORTAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID loadId;

    @Column(nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SendTo sendTo;

    @Column(length = 1000)
    private String billTo;

    @Column(length = 1000)
    private String remitTo;

    @Column(nullable = false)
    private long totalCents;

    @Column(nullable = false)
    private boolean quickPay;

    private Long quickPayFeeCents;

    @Enumerated(EnumType.STRING)
    private SentVia sentVia;

    private String sentEmail;
    private Instant sentAt;
    private LocalDate dueDate;
    private Instant paidAt;
    private Long paidCents;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;

        if (status == null) {
            status = InvoiceStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceLines(List<InvoiceLine> newLines) {
        lines.clear();
        long total = 0;

        for (InvoiceLine line : newLines) {
            line.setInvoice(this);
            line.setPosition(lines.size());
            lines.add(line);
            total += line.getAmountCents();
        }

        totalCents = total;
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public UUID getLoadId() { return loadId; }
    public void setLoadId(UUID loadId) { this.loadId = loadId; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public SendTo getSendTo() { return sendTo; }
    public void setSendTo(SendTo sendTo) { this.sendTo = sendTo; }
    public String getBillTo() { return billTo; }
    public void setBillTo(String billTo) { this.billTo = billTo; }
    public String getRemitTo() { return remitTo; }
    public void setRemitTo(String remitTo) { this.remitTo = remitTo; }
    public long getTotalCents() { return totalCents; }
    public boolean isQuickPay() { return quickPay; }
    public void setQuickPay(boolean quickPay) { this.quickPay = quickPay; }
    public Long getQuickPayFeeCents() { return quickPayFeeCents; }
    public void setQuickPayFeeCents(Long quickPayFeeCents) { this.quickPayFeeCents = quickPayFeeCents; }
    public SentVia getSentVia() { return sentVia; }
    public void setSentVia(SentVia sentVia) { this.sentVia = sentVia; }
    public String getSentEmail() { return sentEmail; }
    public void setSentEmail(String sentEmail) { this.sentEmail = sentEmail; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Long getPaidCents() { return paidCents; }
    public void setPaidCents(Long paidCents) { this.paidCents = paidCents; }
    public List<InvoiceLine> getLines() { return lines; }
    public Instant getCreatedAt() { return createdAt; }
}

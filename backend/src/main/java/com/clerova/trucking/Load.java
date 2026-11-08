package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One load, created from a broker's rate confirmation. Amounts are in cents. */
@Entity
@Table(name = "loads")
public class Load {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id")
    private Broker broker;

    private String loadNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoadStatus status;

    private String equipment;

    private Long rateCents;

    /** A different all-in rate the broker's email stated, if any. */
    private Long agreedRateCents;

    private Integer detentionFreeMinutes;
    private Long detentionRateCents;

    @Column(columnDefinition = "TEXT")
    private String detentionRule;

    private Long tonuCents;

    /** The most detention pays per stop, if the rate con caps it. */
    private Long detentionMaxCents;

    /** USD or CAD, from the rate con; the invoice is in this currency too. */
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    /** The quick pay fee, like 2.5 (percent), if the broker offers quick pay. */
    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal quickPayPercent;

    /** Where the broker wants invoices, if the rate con names an address. */
    private String invoiceEmail;

    @Column(columnDefinition = "TEXT")
    private String paymentTerms;

    @Column(columnDefinition = "TEXT")
    private String paperworkRule;

    private String rateConFile;

    /** The dispatcher's latest update, like "Loaded, ETA Phoenix 6 AM". */
    private String statusNote;

    /** Normalized numbers from the rate con (PO, pickup, BOL...), space-separated. */
    @Column(length = 1000)
    private String referenceNumbers;

    @OneToMany(mappedBy = "load", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LoadStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "load", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    private List<LoadFlag> flags = new ArrayList<>();

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
            status = LoadStatus.BOOKED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceStops(List<LoadStop> newStops) {
        stops.clear();

        for (LoadStop stop : newStops) {
            stop.setLoad(this);
            stops.add(stop);
        }
    }

    public void replaceFlags(List<LoadFlag> newFlags) {
        flags.clear();

        for (LoadFlag flag : newFlags) {
            flag.setLoad(this);
            flags.add(flag);
        }
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public Broker getBroker() { return broker; }
    public void setBroker(Broker broker) { this.broker = broker; }
    public String getLoadNumber() { return loadNumber; }
    public void setLoadNumber(String loadNumber) { this.loadNumber = loadNumber; }
    public LoadStatus getStatus() { return status; }
    public void setStatus(LoadStatus status) { this.status = status; }
    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }
    public Long getRateCents() { return rateCents; }
    public void setRateCents(Long rateCents) { this.rateCents = rateCents; }
    public Long getAgreedRateCents() { return agreedRateCents; }
    public void setAgreedRateCents(Long agreedRateCents) { this.agreedRateCents = agreedRateCents; }
    public Integer getDetentionFreeMinutes() { return detentionFreeMinutes; }
    public void setDetentionFreeMinutes(Integer detentionFreeMinutes) { this.detentionFreeMinutes = detentionFreeMinutes; }
    public Long getDetentionRateCents() { return detentionRateCents; }
    public void setDetentionRateCents(Long detentionRateCents) { this.detentionRateCents = detentionRateCents; }
    public String getDetentionRule() { return detentionRule; }
    public void setDetentionRule(String detentionRule) { this.detentionRule = detentionRule; }
    public Long getTonuCents() { return tonuCents; }
    public void setTonuCents(Long tonuCents) { this.tonuCents = tonuCents; }
    public Long getDetentionMaxCents() { return detentionMaxCents; }
    public void setDetentionMaxCents(Long detentionMaxCents) { this.detentionMaxCents = detentionMaxCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public java.math.BigDecimal getQuickPayPercent() { return quickPayPercent; }
    public void setQuickPayPercent(java.math.BigDecimal quickPayPercent) { this.quickPayPercent = quickPayPercent; }
    public String getInvoiceEmail() { return invoiceEmail; }
    public void setInvoiceEmail(String invoiceEmail) { this.invoiceEmail = invoiceEmail; }
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    public String getPaperworkRule() { return paperworkRule; }
    public void setPaperworkRule(String paperworkRule) { this.paperworkRule = paperworkRule; }
    public String getRateConFile() { return rateConFile; }
    public void setRateConFile(String rateConFile) { this.rateConFile = rateConFile; }
    public String getStatusNote() { return statusNote; }
    public String getReferenceNumbers() { return referenceNumbers; }
    public void setReferenceNumbers(String referenceNumbers) { this.referenceNumbers = referenceNumbers; }
    public void setStatusNote(String statusNote) { this.statusNote = statusNote; }
    public List<LoadStop> getStops() { return stops; }
    public List<LoadFlag> getFlags() { return flags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

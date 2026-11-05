package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Detention, a lumper fee, TONU or another charge on a load. Amounts are in cents. */
@Entity
@Table(name = "load_charges")
public class LoadCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID loadId;

    /** The stop a detention charge is for. */
    private UUID stopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeKind kind;

    private String description;

    @Column(nullable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeStatus status;

    private Instant claimedAt;

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
            status = ChargeStatus.TO_CLAIM;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getLoadId() { return loadId; }
    public void setLoadId(UUID loadId) { this.loadId = loadId; }
    public UUID getStopId() { return stopId; }
    public void setStopId(UUID stopId) { this.stopId = stopId; }
    public ChargeKind getKind() { return kind; }
    public void setKind(ChargeKind kind) { this.kind = kind; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public ChargeStatus getStatus() { return status; }
    public void setStatus(ChargeStatus status) { this.status = status; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public Instant getCreatedAt() { return createdAt; }
}

package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A freight broker a trucking workspace hauls for, with its FMCSA check. */
@Entity
@Table(name = "brokers")
public class Broker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    private String mcNumber;
    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    private FmcsaStatus fmcsaStatus;

    private String fmcsaName;
    private Instant fmcsaCheckedAt;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMcNumber() { return mcNumber; }
    public void setMcNumber(String mcNumber) { this.mcNumber = mcNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public FmcsaStatus getFmcsaStatus() { return fmcsaStatus; }
    public void setFmcsaStatus(FmcsaStatus fmcsaStatus) { this.fmcsaStatus = fmcsaStatus; }
    public String getFmcsaName() { return fmcsaName; }
    public void setFmcsaName(String fmcsaName) { this.fmcsaName = fmcsaName; }
    public Instant getFmcsaCheckedAt() { return fmcsaCheckedAt; }
    public void setFmcsaCheckedAt(Instant fmcsaCheckedAt) { this.fmcsaCheckedAt = fmcsaCheckedAt; }
}

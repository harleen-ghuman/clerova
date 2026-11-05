package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** The carrier's details printed on invoices: one per trucking workspace. */
@Entity
@Table(name = "carrier_profiles")
public class CarrierProfile {

    @Id
    private UUID organizationId;

    private String legalName;
    private String mcNumber;
    private String dotNumber;

    @Column(length = 1000)
    private String address;

    private String phone;
    private String email;

    /** Printed before the running number, like "AQ-" in AQ-1001. */
    private String invoicePrefix;

    @Column(nullable = false)
    private int nextInvoiceNumber = 1001;

    @Column(nullable = false)
    private boolean usesFactoring;

    private String factorName;

    @Column(length = 1000)
    private String factorAddress;

    /** Where the factor takes paperwork by email, if it does. */
    private String factorEmail;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    /** The next invoice number, like "AQ-1001", and moves the counter on. */
    String takeInvoiceNumber() {
        String number = (invoicePrefix == null ? "" : invoicePrefix) + nextInvoiceNumber;
        nextInvoiceNumber++;
        return number;
    }

    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getMcNumber() { return mcNumber; }
    public void setMcNumber(String mcNumber) { this.mcNumber = mcNumber; }
    public String getDotNumber() { return dotNumber; }
    public void setDotNumber(String dotNumber) { this.dotNumber = dotNumber; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }
    public int getNextInvoiceNumber() { return nextInvoiceNumber; }
    public void setNextInvoiceNumber(int nextInvoiceNumber) { this.nextInvoiceNumber = nextInvoiceNumber; }
    public boolean isUsesFactoring() { return usesFactoring; }
    public void setUsesFactoring(boolean usesFactoring) { this.usesFactoring = usesFactoring; }
    public String getFactorName() { return factorName; }
    public void setFactorName(String factorName) { this.factorName = factorName; }
    public String getFactorAddress() { return factorAddress; }
    public void setFactorAddress(String factorAddress) { this.factorAddress = factorAddress; }
    public String getFactorEmail() { return factorEmail; }
    public void setFactorEmail(String factorEmail) { this.factorEmail = factorEmail; }
}

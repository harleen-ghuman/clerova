package com.clerova.trucking;

import jakarta.persistence.*;

import java.util.UUID;

/** One line on an invoice, like the line haul or detention. */
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private long amountCents;

    public InvoiceLine() {
    }

    public InvoiceLine(String description, long amountCents) {
        this.description = description;
        this.amountCents = amountCents;
    }

    public UUID getId() { return id; }
    void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public int getPosition() { return position; }
    void setPosition(int position) { this.position = position; }
    public String getDescription() { return description; }
    public long getAmountCents() { return amountCents; }
}

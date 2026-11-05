package com.clerova.trucking;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "load_stops")
public class LoadStop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "load_id", nullable = false)
    private Load load;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopKind kind;

    @Column(columnDefinition = "TEXT")
    private String place;

    /** The appointment as the rate con writes it, like "Nov 5, 7:00 AM". */
    private String appointment;

    /** When the truck got to the stop and left it, logged by the dispatcher. */
    private Instant arrivedAt;
    private Instant leftAt;

    public UUID getId() { return id; }
    public Load getLoad() { return load; }
    void setLoad(Load load) { this.load = load; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public StopKind getKind() { return kind; }
    public void setKind(StopKind kind) { this.kind = kind; }
    public String getPlace() { return place; }
    public void setPlace(String place) { this.place = place; }
    public String getAppointment() { return appointment; }
    public void setAppointment(String appointment) { this.appointment = appointment; }
    public Instant getArrivedAt() { return arrivedAt; }
    public void setArrivedAt(Instant arrivedAt) { this.arrivedAt = arrivedAt; }
    public Instant getLeftAt() { return leftAt; }
    public void setLeftAt(Instant leftAt) { this.leftAt = leftAt; }
}

package com.clerova.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a work item's audit trail.
 */
@Entity
@Table(name = "work_item_events")
public class WorkItemEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WorkItemEventType type;

    @Column(columnDefinition = "TEXT")
    private String detail;

    /** Null when the event came from Clerova itself (e.g. an email arrived). */
    private UUID actorUserId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
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

    public WorkItemEventType getType() {
        return type;
    }

    public void setType(WorkItemEventType type) {
        this.type = type;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

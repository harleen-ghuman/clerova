package com.clerova.domain;

public enum MaintenanceRequestStatus {
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public boolean canTransitionTo(MaintenanceRequestStatus next) {
        return switch (this) {
            case OPEN -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}

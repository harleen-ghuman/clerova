package com.clerova.dto;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.domain.MaintenanceRequestStatus;
import com.clerova.domain.Priority;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceRequestResponse(
        UUID id,
        UUID workItemId,
        UUID proposedActionId,
        String organizationId,
        String address,
        String description,
        Priority priority,
        MaintenanceRequestStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static MaintenanceRequestResponse from(
            MaintenanceRequest request
    ) {
        return new MaintenanceRequestResponse(
                request.getId(),
                request.getWorkItem().getId(),
                request.getProposedAction().getId(),
                request.getOrganizationId(),
                request.getAddress(),
                request.getDescription(),
                request.getPriority(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}
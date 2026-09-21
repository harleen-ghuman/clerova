package com.clerova.dto;

import com.clerova.domain.MaintenanceRequestStatus;

public record UpdateMaintenanceRequest(
        MaintenanceRequestStatus status,
        String notes
) {
}

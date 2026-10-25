package com.clerova.dto;

import com.clerova.domain.MaintenanceRequestStatus;
import jakarta.validation.constraints.Size;

public record UpdateMaintenanceRequest(
        MaintenanceRequestStatus status,

        @Size(
                max = 5000,
                message = "Notes must not exceed 5000 characters"
        )
        String notes
) {
}

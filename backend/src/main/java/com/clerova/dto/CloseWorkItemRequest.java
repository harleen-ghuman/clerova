package com.clerova.dto;

import com.clerova.domain.WorkItemStatus;
import jakarta.validation.constraints.NotNull;

public record CloseWorkItemRequest(

        @NotNull(message = "Status is required")
        WorkItemStatus status

) {
}

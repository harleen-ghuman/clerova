package com.clerova.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkItemRequest(

        @NotBlank
        String organizationId,

        @NotBlank
        @Size(
                max = 2000,
                message = "Message must not exceed 2000 characters"
        )
        String message

) {
}

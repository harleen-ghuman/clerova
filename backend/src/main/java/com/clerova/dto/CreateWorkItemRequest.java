package com.clerova.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkItemRequest(

        @NotBlank
        String organizationId,

        @NotBlank
        String message

) {
}

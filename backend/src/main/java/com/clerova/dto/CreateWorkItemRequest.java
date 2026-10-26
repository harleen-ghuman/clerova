package com.clerova.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkItemRequest(

        @NotBlank(message = "Paste or type the request first.")
        @Size(
                max = 10000,
                message = "The request must be 10,000 characters or fewer."
        )
        String message

) {
}

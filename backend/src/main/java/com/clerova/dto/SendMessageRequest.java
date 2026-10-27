package com.clerova.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Write a reply before sending.")
        @Size(max = 10000, message = "The reply is too long. Keep it under 10,000 characters.")
        String message
) {
}

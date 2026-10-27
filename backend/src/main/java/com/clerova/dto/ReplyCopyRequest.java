package com.clerova.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** A blank or missing email turns the copies off. */
public record ReplyCopyRequest(
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "That email address is too long.")
        String email
) {
}

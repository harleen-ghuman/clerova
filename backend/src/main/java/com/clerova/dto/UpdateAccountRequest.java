package com.clerova.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changes from the Settings page. The email is changed through Supabase. */
public record UpdateAccountRequest(

        @Size(max = 255, message = "Name must be 255 characters or fewer.")
        String fullName,

        @NotBlank(message = "Company name is required.")
        @Size(max = 255, message = "Company name must be 255 characters or fewer.")
        String organizationName

) {
}

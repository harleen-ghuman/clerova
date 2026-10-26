package com.clerova.dto;

import com.clerova.domain.Industry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OnboardingRequest(

        @NotBlank(message = "Company name is required.")
        @Size(max = 255, message = "Company name must be 255 characters or fewer.")
        String organizationName,

        @NotNull(message = "Choose an industry.")
        Industry industry,

        @Size(max = 50, message = "Units managed must be 50 characters or fewer.")
        String companySize,

        @Size(max = 255, message = "How you heard about us must be 255 characters or fewer.")
        String referralSource,

        @Size(max = 255, message = "Name must be 255 characters or fewer.")
        String fullName

) {
}

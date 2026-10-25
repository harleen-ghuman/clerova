package com.clerova.dto;

import com.clerova.domain.Industry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OnboardingRequest(

        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        String organizationName,

        @NotNull(message = "Industry is required")
        Industry industry,

        @Size(max = 50, message = "Company size must not exceed 50 characters")
        String companySize,

        @Size(max = 255, message = "Referral source must not exceed 255 characters")
        String referralSource,

        @Size(max = 255, message = "Name must not exceed 255 characters")
        String fullName

) {
}

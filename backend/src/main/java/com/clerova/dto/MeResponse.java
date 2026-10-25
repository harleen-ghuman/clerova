package com.clerova.dto;

import java.util.UUID;

/**
 * The signed-in user. organization is null until onboarding is complete.
 */
public record MeResponse(
        UUID userId,
        String email,
        String fullName,
        OrganizationResponse organization
) {
}

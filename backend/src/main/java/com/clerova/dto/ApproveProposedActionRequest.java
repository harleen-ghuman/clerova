package com.clerova.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for approving a proposed action. When finalResponse is
 * provided it replaces the AI-drafted response.
 */
public record ApproveProposedActionRequest(

        @Size(
                max = 10000,
                message = "The reply must be 10,000 characters or fewer."
        )
        String finalResponse

) {
}

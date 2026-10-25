package com.clerova.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional body for approving a proposed action. When finalResponse is
 * provided it replaces the AI-drafted response.
 */
public record ApproveProposedActionRequest(

        @Size(
                max = 10000,
                message = "Response must not exceed 10000 characters"
        )
        String finalResponse

) {
}

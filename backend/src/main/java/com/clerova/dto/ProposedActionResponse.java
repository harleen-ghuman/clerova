package com.clerova.dto;

import com.clerova.domain.ActionType;
import com.clerova.domain.ProposedActionStatus;

import java.time.Instant;
import java.util.UUID;

public record ProposedActionResponse(
        UUID id,
        UUID workItemId,
        ActionType actionType,
        ProposedActionStatus status,
        String reasoning,
        String draftedResponse,
        Instant createdAt,
        Instant updatedAt
) {
}

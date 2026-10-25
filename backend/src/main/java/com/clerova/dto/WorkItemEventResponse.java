package com.clerova.dto;

import com.clerova.domain.WorkItemEvent;
import com.clerova.domain.WorkItemEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * @param actor the user's email, or null when Clerova acted automatically
 */
public record WorkItemEventResponse(
        UUID id,
        WorkItemEventType type,
        String detail,
        String actor,
        Instant createdAt
) {

    public static WorkItemEventResponse from(WorkItemEvent event, String actor) {
        return new WorkItemEventResponse(
                event.getId(),
                event.getType(),
                event.getDetail(),
                actor,
                event.getCreatedAt()
        );
    }
}

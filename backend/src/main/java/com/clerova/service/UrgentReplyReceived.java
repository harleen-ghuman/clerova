package com.clerova.service;

import java.util.UUID;

/** A sender's new email during a job looks urgent; owners are emailed. */
public record UrgentReplyReceived(
        UUID organizationId,
        UUID workItemId,
        String sender,
        String title,
        String reason,
        String message
) {
}

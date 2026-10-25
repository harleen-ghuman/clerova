package com.clerova.dto;

import java.time.Instant;

/**
 * @param forwardingAddress null when email intake is not configured
 * @param gmailConfirmation the latest Gmail forwarding confirmation email,
 *                          which contains the code Gmail asks for
 */
public record EmailSettingsResponse(
        boolean intakeEnabled,
        boolean sendingEnabled,
        String forwardingAddress,
        GmailConfirmation gmailConfirmation
) {

    public record GmailConfirmation(Instant receivedAt, String subject, String text) {
    }
}

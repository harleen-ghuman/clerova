package com.clerova.email;

public record OutgoingEmail(
        String from,
        String replyTo,
        String to,
        String subject,
        String textBody,
        String inReplyTo,
        String references
) {
}

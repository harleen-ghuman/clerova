package com.clerova.dto;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.OutboundMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationEntryResponse(
        UUID id,
        Direction direction,
        String from,
        String to,
        String subject,
        String text,
        Instant at,
        List<String> attachments
) {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    public static ConversationEntryResponse from(InboundMessage message) {
        String from = message.getFromName() != null
                ? message.getFromName() + " <" + message.getFromEmail() + ">"
                : message.getFromEmail();

        return new ConversationEntryResponse(
                message.getId(),
                Direction.INBOUND,
                from,
                null,
                message.getSubject(),
                message.getContentText(),
                message.getReceivedAt(),
                message.getAttachments().stream()
                        .map(InboundAttachment::getFileName)
                        .toList()
        );
    }

    public static ConversationEntryResponse from(OutboundMessage message) {
        return new ConversationEntryResponse(
                message.getId(),
                Direction.OUTBOUND,
                null,
                message.getToEmail(),
                message.getSubject(),
                message.getBody(),
                message.getSentAt(),
                List.of()
        );
    }
}

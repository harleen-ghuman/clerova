package com.clerova.dto;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InboundMessageResponse(
        UUID id,
        String fromEmail,
        String fromName,
        String subject,
        String preview,
        Instant receivedAt,
        InboundMessageStatus status,
        String statusReason,
        UUID workItemId,
        List<String> attachments,
        List<AttachmentFile> files
) {

    private static final int PREVIEW_LENGTH = 240;

    public static InboundMessageResponse from(InboundMessage message) {
        String text = message.getContentText() == null
                ? ""
                : message.getContentText().replaceAll("\\s+", " ").trim();

        return new InboundMessageResponse(
                message.getId(),
                message.getFromEmail(),
                message.getFromName(),
                message.getSubject(),
                text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "..." : text,
                message.getReceivedAt(),
                message.getStatus(),
                message.getStatusReason(),
                message.getWorkItemId(),
                message.getAttachments().stream()
                        .map(InboundAttachment::getFileName)
                        .toList(),
                message.getAttachments().stream()
                        .map(AttachmentFile::from)
                        .toList()
        );
    }
}

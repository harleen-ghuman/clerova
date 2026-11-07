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
        /** The load it's filed under, in a trucking workspace. */
        UUID loadId,
        List<String> attachments,
        List<AttachmentFile> files,
        /** When the carrier last replied from the Inbox, and AI drafts left for this email. */
        Instant repliedAt,
        Integer replyDraftsLeft
) {

    /** AI reply drafts per email in the Inbox. */
    public static final int MAX_REPLY_DRAFTS = 10;

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
                message.getLoadId(),
                message.getAttachments().stream()
                        .map(InboundAttachment::getFileName)
                        .toList(),
                message.getAttachments().stream()
                        .map(AttachmentFile::from)
                        .toList(),
                message.getRepliedAt(),
                Math.max(0, MAX_REPLY_DRAFTS - message.getReplyDraftsUsed())
        );
    }
}

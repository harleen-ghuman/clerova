package com.clerova.dto;

import com.clerova.domain.InboundAttachment;

import java.util.UUID;

/**
 * @param available whether the file itself was kept and can be opened;
 *                  otherwise only its name is known
 */
public record AttachmentFile(
        UUID id,
        String fileName,
        String contentType,
        Long sizeBytes,
        boolean available
) {

    public static AttachmentFile from(InboundAttachment attachment) {
        return new AttachmentFile(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getStoragePath() != null
        );
    }
}

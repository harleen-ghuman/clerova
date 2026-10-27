package com.clerova.controller;

import com.clerova.domain.InboundAttachment;
import com.clerova.exception.ApiException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.InboundAttachmentRepository;
import com.clerova.security.CurrentUserService;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Files that came with emails, for the workspace that received them. */
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final Logger log = LoggerFactory.getLogger(AttachmentController.class);

    /** Shown in the browser; anything else downloads, so it can't run as a page. */
    static final Set<String> VIEWABLE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"
    );

    private final InboundAttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;
    private final CurrentUserService currentUserService;

    public AttachmentController(
            InboundAttachmentRepository attachmentRepository,
            FileStorage fileStorage,
            CurrentUserService currentUserService
    ) {
        this.attachmentRepository = attachmentRepository;
        this.fileStorage = fileStorage;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable UUID id) {
        InboundAttachment attachment = attachmentRepository
                .findInOrganization(id, currentUserService.requireOrganizationId())
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + id));

        if (attachment.getStoragePath() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FILE_NOT_KEPT",
                    "Only the name of this file was kept. It's in the sender's original email.");
        }

        byte[] content;

        try {
            content = fileStorage.get(attachment.getStoragePath());
        } catch (FileStorageException e) {
            log.warn("Could not read attachment {}: {}", id, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FILE_UNAVAILABLE",
                    "Couldn't open this file right now. Please try again in a minute.");
        }

        String type = attachment.getContentType() == null
                ? ""
                : attachment.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        boolean viewable = VIEWABLE_TYPES.contains(type);
        String fileName = attachment.getFileName() == null || attachment.getFileName().isBlank()
                ? "file"
                : attachment.getFileName();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(viewable ? MediaType.parseMediaType(type) : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition((viewable ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
        headers.setCacheControl("private, max-age=300");

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }
}

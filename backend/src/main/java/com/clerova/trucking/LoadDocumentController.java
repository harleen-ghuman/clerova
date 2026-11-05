package com.clerova.trucking;

import com.clerova.exception.ApiException;
import com.clerova.security.CurrentUserService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class LoadDocumentController {

    /** Either may be left out to keep it. */
    public record UpdateDocumentRequest(UUID loadId, DocumentKind kind) {
    }

    private final LoadDocumentService documentService;
    private final LoadService loadService;
    private final CurrentUserService currentUserService;

    public LoadDocumentController(
            LoadDocumentService documentService, LoadService loadService, CurrentUserService currentUserService) {
        this.documentService = documentService;
        this.loadService = loadService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/api/load-documents")
    public ResponseEntity<LoadResponses.UploadedDocument> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "loadId", required = false) UUID loadId
    ) {
        byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE", "Couldn't read that file. Please try again.");
        }

        LoadResponses.UploadedDocument uploaded = documentService.upload(
                currentUserService.requireOrganizationId(), currentUserService.currentUserId(), loadId,
                new LoadDocumentService.Upload(file.getOriginalFilename(), file.getContentType(), content));
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    @GetMapping("/api/load-documents")
    public List<LoadResponses.UploadedDocument> unfiled() {
        return documentService.unfiled(currentUserService.requireOrganizationId());
    }

    @PatchMapping("/api/load-documents/{id}")
    public LoadResponses.UploadedDocument update(@PathVariable UUID id, @RequestBody UpdateDocumentRequest request) {
        return documentService.update(currentUserService.requireOrganizationId(), id, request.loadId(), request.kind());
    }

    @DeleteMapping("/api/load-documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(currentUserService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/load-documents/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        LoadDocumentService.File file = documentService.file(currentUserService.requireOrganizationId(), id);
        HttpHeaders headers = new HttpHeaders();
        // Only PDFs and photos are accepted, so the file can be shown in the browser
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
        headers.setCacheControl("private, max-age=300");
        return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
    }

    /** Files an email under a load by hand (from the Inbox). */
    @PostMapping("/api/loads/{loadId}/emails/{messageId}")
    public LoadResponses.LoadDetail fileEmail(@PathVariable UUID loadId, @PathVariable UUID messageId) {
        return loadService.fileEmail(currentUserService.requireOrganizationId(), loadId, messageId);
    }
}

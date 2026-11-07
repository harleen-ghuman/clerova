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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The carrier's own documents for brokers' carrier packets. */
@RestController
public class CarrierPacketController {

    /** expiresOn "" clears the date; left out keeps it. */
    public record UpdateRequest(CarrierDocument.Kind kind, String expiresOn) {
    }

    public record SendRequest(String to, String message) {
    }

    private final CarrierPacketService packetService;
    private final CurrentUserService currentUserService;

    public CarrierPacketController(CarrierPacketService packetService, CurrentUserService currentUserService) {
        this.packetService = packetService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/carrier/documents")
    public List<CarrierPacketService.DocumentInfo> list() {
        return packetService.list(currentUserService.requireOrganizationId());
    }

    @PostMapping("/api/carrier/documents")
    public ResponseEntity<CarrierPacketService.DocumentInfo> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kind", required = false) CarrierDocument.Kind kind,
            @RequestParam(value = "expiresOn", required = false) String expiresOn
    ) {
        byte[] content;

        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_UNREADABLE", "Couldn't read that file. Please try again.");
        }

        CarrierPacketService.DocumentInfo saved = packetService.upload(
                currentUserService.requireOrganizationId(), currentUserService.currentUserId(), kind, date(expiresOn),
                new LoadDocumentService.Upload(file.getOriginalFilename(), file.getContentType(), content));
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/api/carrier/documents/{id}")
    public CarrierPacketService.DocumentInfo update(@PathVariable UUID id, @RequestBody UpdateRequest request) {
        boolean clear = request.expiresOn() != null && request.expiresOn().isBlank();
        return packetService.update(currentUserService.requireOrganizationId(), id, request.kind(),
                clear ? null : date(request.expiresOn()), clear);
    }

    @DeleteMapping("/api/carrier/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        packetService.delete(currentUserService.requireOrganizationId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/carrier/documents/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        return pdfOrPhoto(packetService.file(currentUserService.requireOrganizationId(), id), false);
    }

    @GetMapping("/api/carrier/packet")
    public ResponseEntity<byte[]> packet() {
        return pdfOrPhoto(packetService.packet(currentUserService.requireOrganizationId()), true);
    }

    @PostMapping("/api/carrier/packet/send")
    public Map<String, Boolean> send(@RequestBody SendRequest request) {
        packetService.send(currentUserService.requireOrganizationId(), request.to(), request.message());
        return Map.of("sent", true);
    }

    private static ResponseEntity<byte[]> pdfOrPhoto(CarrierPacketService.File file, boolean download) {
        HttpHeaders headers = new HttpHeaders();
        // Only PDFs and photos are accepted, so the file can be shown in the browser
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition((download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.fileName(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; sandbox");
        headers.setCacheControl("private, max-age=300");
        return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Enter the date like 2027-03-31.");
        }
    }
}

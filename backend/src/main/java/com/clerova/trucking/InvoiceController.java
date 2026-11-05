package com.clerova.trucking;

import com.clerova.security.CurrentUserService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class InvoiceController {

    public record SendRequest(String to, String message) {
    }

    public record PaidRequest(Long paidCents) {
    }

    private final InvoiceService invoiceService;
    private final CarrierService carrierService;
    private final CurrentUserService currentUserService;

    public InvoiceController(InvoiceService invoiceService, CarrierService carrierService,
                             CurrentUserService currentUserService) {
        this.invoiceService = invoiceService;
        this.carrierService = carrierService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/carrier")
    public CarrierService.CarrierDetails carrier() {
        return carrierService.get(currentUserService.requireOrganizationId());
    }

    @PutMapping("/api/carrier")
    public CarrierService.CarrierDetails saveCarrier(@RequestBody CarrierService.CarrierDetails request) {
        return carrierService.save(currentUserService.requireOrganizationId(), request);
    }

    @GetMapping("/api/invoices")
    public List<LoadResponses.InvoiceInfo> list() {
        return invoiceService.list(currentUserService.requireOrganizationId());
    }

    @PostMapping("/api/loads/{loadId}/invoice")
    public LoadResponses.InvoiceInfo create(@PathVariable UUID loadId) {
        return invoiceService.create(currentUserService.requireOrganizationId(), loadId);
    }

    @GetMapping("/api/invoices/{id}")
    public LoadResponses.InvoiceInfo get(@PathVariable UUID id) {
        return invoiceService.get(currentUserService.requireOrganizationId(), id);
    }

    @PatchMapping("/api/invoices/{id}")
    public LoadResponses.InvoiceInfo update(@PathVariable UUID id, @RequestBody InvoiceService.UpdateRequest request) {
        return invoiceService.update(currentUserService.requireOrganizationId(), id, request);
    }

    @GetMapping("/api/invoices/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        return pdfResponse(invoiceService.pdf(currentUserService.requireOrganizationId(), id));
    }

    @GetMapping("/api/invoices/{id}/packet")
    public ResponseEntity<byte[]> packet(@PathVariable UUID id) {
        return pdfResponse(invoiceService.packet(currentUserService.requireOrganizationId(), id));
    }

    @PostMapping("/api/invoices/{id}/send")
    public LoadResponses.InvoiceInfo send(@PathVariable UUID id, @RequestBody SendRequest request) {
        return invoiceService.send(currentUserService.requireOrganizationId(), id, request.to(), request.message());
    }

    @PostMapping("/api/invoices/{id}/mark-sent")
    public LoadResponses.InvoiceInfo markSent(@PathVariable UUID id) {
        return invoiceService.markSent(currentUserService.requireOrganizationId(), id);
    }

    @PostMapping("/api/invoices/{id}/paid")
    public LoadResponses.InvoiceInfo paid(@PathVariable UUID id, @RequestBody(required = false) PaidRequest request) {
        return invoiceService.markPaid(currentUserService.requireOrganizationId(), id,
                request == null ? null : request.paidCents());
    }

    @PostMapping("/api/invoices/{id}/unpaid")
    public LoadResponses.InvoiceInfo unpaid(@PathVariable UUID id) {
        return invoiceService.markUnpaid(currentUserService.requireOrganizationId(), id);
    }

    @PostMapping("/api/invoices/{id}/void")
    public LoadResponses.InvoiceInfo voidInvoice(@PathVariable UUID id) {
        return invoiceService.voidInvoice(currentUserService.requireOrganizationId(), id);
    }

    @GetMapping("/api/invoices/export")
    public ResponseEntity<byte[]> export(@RequestParam String month) {
        InvoiceService.FileDownload file = invoiceService.export(currentUserService.requireOrganizationId(), month);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }

    private static ResponseEntity<byte[]> pdfResponse(InvoiceService.FileDownload file) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}

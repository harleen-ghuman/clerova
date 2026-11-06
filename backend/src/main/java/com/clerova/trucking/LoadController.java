package com.clerova.trucking;

import com.clerova.security.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class LoadController {

    /** Either may be left out to keep it as it is; an empty note clears it. */
    public record UpdateLoadRequest(LoadStatus status, String statusNote) {
    }

    public record SendReplyRequest(String body) {
    }

    /** Both times as they should be; null clears one. */
    public record StopTimesRequest(java.time.Instant arrivedAt, java.time.Instant leftAt) {
    }

    public record ChargeRequest(ChargeKind kind, String description, Long amountCents, ChargeStatus status) {
    }

    public record MessageRequest(String to, String subject, String body, UUID chargeId, UUID stopId) {
    }

    private final LoadService loadService;
    private final CurrentUserService currentUserService;

    public LoadController(LoadService loadService, CurrentUserService currentUserService) {
        this.loadService = loadService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/api/loads")
    public List<LoadResponses.LoadSummary> list() {
        return loadService.list(currentUserService.requireOrganizationId());
    }

    @GetMapping("/api/loads/{id}")
    public LoadResponses.LoadDetail get(@PathVariable UUID id) {
        return loadService.get(currentUserService.requireOrganizationId(), id);
    }

    @PatchMapping("/api/loads/{id}")
    public LoadResponses.LoadDetail update(@PathVariable UUID id, @RequestBody UpdateLoadRequest request) {
        return loadService.update(currentUserService.requireOrganizationId(), id, request.status(), request.statusNote());
    }

    @PostMapping("/api/loads/{id}/flags/{flagId}/resolve")
    public LoadResponses.LoadDetail resolveFlag(@PathVariable UUID id, @PathVariable UUID flagId) {
        return loadService.resolveFlag(currentUserService.requireOrganizationId(), id, flagId);
    }

    @PostMapping("/api/loads/{id}/replies/{replyId}/send")
    public LoadResponses.LoadDetail sendReply(
            @PathVariable UUID id, @PathVariable UUID replyId, @RequestBody SendReplyRequest request) {
        return loadService.sendReply(currentUserService.requireOrganizationId(), id, replyId, request.body());
    }

    @PostMapping("/api/loads/{id}/replies/{replyId}/dismiss")
    public LoadResponses.LoadDetail dismissReply(@PathVariable UUID id, @PathVariable UUID replyId) {
        return loadService.dismissReply(currentUserService.requireOrganizationId(), id, replyId);
    }

    public record BrokerMcRequest(String mcNumber) {
    }

    @org.springframework.web.bind.annotation.PutMapping("/api/brokers/{id}/mc-number")
    public LoadResponses.BrokerInfo setBrokerMc(@PathVariable UUID id, @RequestBody BrokerMcRequest request) {
        return loadService.setBrokerMc(currentUserService.requireOrganizationId(), id, request.mcNumber());
    }

    @GetMapping("/api/brokers")
    public List<LoadResponses.BrokerSummary> brokers() {
        return loadService.brokers(currentUserService.requireOrganizationId());
    }

    @org.springframework.web.bind.annotation.PutMapping("/api/loads/{id}/stops/{stopId}/times")
    public LoadResponses.LoadDetail setStopTimes(@PathVariable UUID id, @PathVariable UUID stopId,
                                                 @RequestBody StopTimesRequest request) {
        return loadService.setStopTimes(currentUserService.requireOrganizationId(), id, stopId,
                request.arrivedAt(), request.leftAt());
    }

    @PostMapping("/api/loads/{id}/charges")
    public LoadResponses.LoadDetail addCharge(@PathVariable UUID id, @RequestBody ChargeRequest request) {
        return loadService.addCharge(currentUserService.requireOrganizationId(), id, request.kind(),
                request.description(), request.amountCents(), request.status());
    }

    @PatchMapping("/api/loads/{id}/charges/{chargeId}")
    public LoadResponses.LoadDetail updateCharge(@PathVariable UUID id, @PathVariable UUID chargeId,
                                                 @RequestBody ChargeRequest request) {
        return loadService.updateCharge(currentUserService.requireOrganizationId(), id, chargeId,
                request.description(), request.amountCents(), request.status());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/loads/{id}/charges/{chargeId}")
    public LoadResponses.LoadDetail deleteCharge(@PathVariable UUID id, @PathVariable UUID chargeId) {
        return loadService.deleteCharge(currentUserService.requireOrganizationId(), id, chargeId);
    }

    @PostMapping("/api/loads/{id}/messages")
    public LoadResponses.LoadDetail sendMessage(@PathVariable UUID id, @RequestBody MessageRequest request) {
        return loadService.sendMessage(currentUserService.requireOrganizationId(), id, request.to(),
                request.subject(), request.body(), request.chargeId(), request.stopId());
    }
}

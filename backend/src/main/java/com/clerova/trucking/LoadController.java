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

    @GetMapping("/api/brokers")
    public List<LoadResponses.BrokerSummary> brokers() {
        return loadService.brokers(currentUserService.requireOrganizationId());
    }
}

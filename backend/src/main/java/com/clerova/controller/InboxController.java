package com.clerova.controller;

import com.clerova.dto.EmailSettingsResponse;
import com.clerova.dto.InboundMessageResponse;
import com.clerova.dto.ReplyCopyRequest;
import com.clerova.email.InboxService;
import com.clerova.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InboxController {

    private final InboxService inboxService;
    private final CurrentUserService currentUserService;

    public InboxController(
            InboxService inboxService,
            CurrentUserService currentUserService
    ) {
        this.inboxService = inboxService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/inbox")
    public List<InboundMessageResponse> getInbox(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return inboxService.getRecentMessages(
                currentUserService.requireOrganizationId(),
                limit
        );
    }

    @PostMapping("/inbox/{id}/reprocess")
    public InboundMessageResponse reprocess(@PathVariable UUID id) {
        return inboxService.reprocess(
                currentUserService.requireOrganizationId(),
                id
        );
    }

    @GetMapping("/email-settings")
    public EmailSettingsResponse getEmailSettings() {
        return inboxService.getSettings(
                currentUserService.requireOrganizationId()
        );
    }

    /** Where a copy of each reply sent from Clerova goes; blank turns it off. */
    @PutMapping("/email-settings/reply-copy")
    public EmailSettingsResponse setReplyCopy(@Valid @RequestBody ReplyCopyRequest request) {
        return inboxService.setReplyCopyEmail(
                currentUserService.requireOrganizationId(),
                request.email()
        );
    }
}

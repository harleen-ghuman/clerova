package com.clerova.controller;

import com.clerova.dto.EmailSettingsResponse;
import com.clerova.dto.InboundMessageResponse;
import com.clerova.email.InboxService;
import com.clerova.security.CurrentUserService;
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
}

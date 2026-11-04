package com.clerova.trucking;

import com.clerova.security.CurrentUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/loads")
public class LoadController {

    private final LoadService loadService;
    private final CurrentUserService currentUserService;

    public LoadController(LoadService loadService, CurrentUserService currentUserService) {
        this.loadService = loadService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<LoadResponses.LoadSummary> list() {
        return loadService.list(currentUserService.requireOrganizationId());
    }

    @GetMapping("/{id}")
    public LoadResponses.LoadDetail get(@PathVariable UUID id) {
        return loadService.get(currentUserService.requireOrganizationId(), id);
    }

    @PostMapping("/{id}/flags/{flagId}/resolve")
    public LoadResponses.LoadDetail resolveFlag(@PathVariable UUID id, @PathVariable UUID flagId) {
        return loadService.resolveFlag(currentUserService.requireOrganizationId(), id, flagId);
    }
}

package com.clerova.controller;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.dto.MaintenanceRequestResponse;
import com.clerova.dto.UpdateMaintenanceRequest;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.security.CurrentUserService;
import com.clerova.service.ActionExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenance-requests")
public class MaintenanceRequestController {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final ActionExecutionService actionExecutionService;
    private final CurrentUserService currentUserService;

    public MaintenanceRequestController(
            MaintenanceRequestRepository maintenanceRequestRepository,
            ActionExecutionService actionExecutionService,
            CurrentUserService currentUserService
    ) {
        this.currentUserService = currentUserService;

        this.maintenanceRequestRepository =
                maintenanceRequestRepository;

        this.actionExecutionService =
                actionExecutionService;
    }

    @GetMapping
    public List<MaintenanceRequestResponse> getAllMaintenanceRequests() {
        return maintenanceRequestRepository
                .findByOrganizationIdOrderByCreatedAtDesc(
                        currentUserService.requireOrganizationId()
                )
                .stream()
                .map(MaintenanceRequestResponse::from)
                .toList();
    }

    @GetMapping("/work-item/{workItemId}")
    public List<MaintenanceRequestResponse> getByWorkItem(
            @PathVariable UUID workItemId
    ) {
        return maintenanceRequestRepository
                .findByWorkItemIdAndOrganizationIdOrderByCreatedAtDesc(
                        workItemId,
                        currentUserService.requireOrganizationId()
                )
                .stream()
                .map(MaintenanceRequestResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MaintenanceRequestResponse>
    updateMaintenanceRequest(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMaintenanceRequest request
    ) {
        MaintenanceRequest updated =
                actionExecutionService.updateMaintenanceRequest(
                        currentUserService.requireOrganizationId(),
                        id,
                        request.status(),
                        request.notes(),
                        currentUserService.currentUserId()
                );

        return ResponseEntity.ok(
                MaintenanceRequestResponse.from(updated)
        );
    }
}
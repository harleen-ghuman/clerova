package com.clerova.controller;

import com.clerova.domain.MaintenanceRequest;
import com.clerova.dto.MaintenanceRequestResponse;
import com.clerova.dto.UpdateMaintenanceRequest;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.service.ActionExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenance-requests")
public class MaintenanceRequestController {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final ActionExecutionService actionExecutionService;

    public MaintenanceRequestController(
            MaintenanceRequestRepository maintenanceRequestRepository,
            ActionExecutionService actionExecutionService
    ) {
        this.maintenanceRequestRepository =
                maintenanceRequestRepository;

        this.actionExecutionService =
                actionExecutionService;
    }

    @GetMapping
    public List<MaintenanceRequestResponse> getAllMaintenanceRequests() {
        return maintenanceRequestRepository.findAll()
                .stream()
                .map(MaintenanceRequestResponse::from)
                .toList();
    }

    @GetMapping("/work-item/{workItemId}")
    public List<MaintenanceRequestResponse> getByWorkItem(
            @PathVariable UUID workItemId
    ) {
        return maintenanceRequestRepository
                .findByWorkItemIdOrderByCreatedAtDesc(workItemId)
                .stream()
                .map(MaintenanceRequestResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MaintenanceRequestResponse>
    updateMaintenanceRequest(
            @PathVariable UUID id,
            @RequestBody UpdateMaintenanceRequest request
    ) {
        MaintenanceRequest updated =
                actionExecutionService.updateMaintenanceRequest(
                        id,
                        request.status(),
                        request.notes()
                );

        return ResponseEntity.ok(
                MaintenanceRequestResponse.from(updated)
        );
    }
}
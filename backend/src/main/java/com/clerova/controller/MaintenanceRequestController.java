package com.clerova.controller;

import com.clerova.dto.MaintenanceRequestResponse;
import com.clerova.repository.MaintenanceRequestRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenance-requests")
public class MaintenanceRequestController {

    private final MaintenanceRequestRepository maintenanceRequestRepository;

    public MaintenanceRequestController(
            MaintenanceRequestRepository maintenanceRequestRepository
    ) {
        this.maintenanceRequestRepository =
                maintenanceRequestRepository;
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
}
package com.clerova.controller;

import com.clerova.domain.WorkItem;
import com.clerova.dto.CreateWorkItemRequest;
import com.clerova.service.WorkItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;

    public WorkItemController(WorkItemService workItemService) {
        this.workItemService = workItemService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItem createWorkItem(
            @Valid @RequestBody CreateWorkItemRequest request
    ) {

        return workItemService.createWorkItem(
                request.organizationId(),
                request.message()
        );
    }

    @GetMapping
    public List<WorkItem> getAllWorkItems() {
        return workItemService.getAllWorkItems();
    }

    @GetMapping("/{id}")
    public WorkItem getWorkItem(@PathVariable UUID id) {
        return workItemService.getWorkItem(id);
    }
}

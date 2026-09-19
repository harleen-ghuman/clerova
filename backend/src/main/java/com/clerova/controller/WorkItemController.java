package com.clerova.controller;

import com.clerova.domain.ProposedAction;
import com.clerova.domain.WorkItem;
import com.clerova.dto.CreateWorkItemRequest;
import com.clerova.dto.ProposedActionResponse;
import com.clerova.service.WorkItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/{workItemId}/proposed-actions")
    public ResponseEntity<ProposedActionResponse> generateProposedAction(
            @PathVariable UUID workItemId
    ) {

        ProposedAction proposedAction =
                workItemService.generateProposedAction(workItemId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(proposedAction));
    }

    @PostMapping("/proposed-actions/{proposedActionId}/approve")
    public ResponseEntity<ProposedActionResponse> approveProposedAction(
            @PathVariable UUID proposedActionId
    ) {

        ProposedAction proposedAction =
                workItemService.approveProposedAction(proposedActionId);

        return ResponseEntity.ok(
                toResponse(proposedAction)
        );
    }

    @PostMapping("/proposed-actions/{proposedActionId}/reject")
    public ResponseEntity<ProposedActionResponse> rejectProposedAction(
            @PathVariable UUID proposedActionId
    ) {

        ProposedAction proposedAction =
                workItemService.rejectProposedAction(proposedActionId);

        return ResponseEntity.ok(
                toResponse(proposedAction)
        );
    }

    private ProposedActionResponse toResponse(ProposedAction proposedAction) {
        return new ProposedActionResponse(
                proposedAction.getId(),
                proposedAction.getWorkItem().getId(),
                proposedAction.getActionType(),
                proposedAction.getStatus(),
                proposedAction.getReasoning(),
                proposedAction.getDraftedResponse(),
                proposedAction.getCreatedAt(),
                proposedAction.getUpdatedAt()
        );
    }

    @GetMapping("/{workItemId}/proposed-actions")
    public ResponseEntity<List<ProposedActionResponse>> getProposedActions(
            @PathVariable UUID workItemId
    ) {

        List<ProposedActionResponse> responses =
                workItemService.getProposedActions(workItemId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(responses);
    }
}

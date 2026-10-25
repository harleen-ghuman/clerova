package com.clerova.controller;

import com.clerova.domain.ProposedAction;
import com.clerova.domain.WorkItem;
import com.clerova.dto.ApproveProposedActionRequest;
import com.clerova.dto.CloseWorkItemRequest;
import com.clerova.dto.CreateWorkItemRequest;
import com.clerova.dto.ProposedActionResponse;
import com.clerova.security.CurrentUserService;
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
    private final CurrentUserService currentUserService;

    public WorkItemController(
            WorkItemService workItemService,
            CurrentUserService currentUserService
    ) {
        this.workItemService = workItemService;
        this.currentUserService = currentUserService;
    }

    private UUID organizationId() {
        return currentUserService.requireOrganizationId();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItem createWorkItem(
            @Valid @RequestBody CreateWorkItemRequest request
    ) {

        return workItemService.createWorkItem(
                organizationId(),
                request.message()
        );
    }

    @GetMapping
    public List<WorkItem> getAllWorkItems() {
        return workItemService.getAllWorkItems(organizationId());
    }

    @GetMapping("/{id}")
    public WorkItem getWorkItem(@PathVariable UUID id) {
        return workItemService.getWorkItem(organizationId(), id);
    }

    @PostMapping("/{id}/close")
    public WorkItem closeWorkItem(
            @PathVariable UUID id,
            @Valid @RequestBody CloseWorkItemRequest request
    ) {
        return workItemService.closeWorkItem(organizationId(), id, request.status());
    }

    @PostMapping("/{workItemId}/proposed-actions")
    public ResponseEntity<ProposedActionResponse> generateProposedAction(
            @PathVariable UUID workItemId
    ) {

        ProposedAction proposedAction =
                workItemService.generateProposedAction(organizationId(), workItemId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(proposedAction));
    }

    @GetMapping("/proposed-actions/latest")
    public List<ProposedActionResponse> getLatestProposedActions() {
        return workItemService.getLatestProposedActions(organizationId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/proposed-actions/{proposedActionId}/approve")
    public ResponseEntity<ProposedActionResponse> approveProposedAction(
            @PathVariable UUID proposedActionId,
            @Valid @RequestBody(required = false) ApproveProposedActionRequest request
    ) {

        ProposedAction proposedAction =
                workItemService.approveProposedAction(
                        organizationId(),
                        proposedActionId,
                        request != null ? request.finalResponse() : null
                );

        return ResponseEntity.ok(
                toResponse(proposedAction)
        );
    }

    @PostMapping("/proposed-actions/{proposedActionId}/reject")
    public ResponseEntity<ProposedActionResponse> rejectProposedAction(
            @PathVariable UUID proposedActionId
    ) {

        ProposedAction proposedAction =
                workItemService.rejectProposedAction(organizationId(), proposedActionId);

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
                proposedAction.getFinalResponse(),
                proposedAction.getCreatedAt(),
                proposedAction.getUpdatedAt()
        );
    }

    @GetMapping("/{workItemId}/proposed-actions")
    public ResponseEntity<List<ProposedActionResponse>> getProposedActions(
            @PathVariable UUID workItemId
    ) {

        List<ProposedActionResponse> responses =
                workItemService.getProposedActions(organizationId(), workItemId)
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return ResponseEntity.ok(responses);
    }
}

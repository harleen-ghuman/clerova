package com.clerova.controller;

import com.clerova.domain.ProposedAction;
import com.clerova.domain.WorkItem;
import com.clerova.dto.ApproveProposedActionRequest;
import com.clerova.dto.CloseWorkItemRequest;
import com.clerova.dto.ConversationEntryResponse;
import com.clerova.dto.CreateWorkItemRequest;
import com.clerova.dto.ProposedActionResponse;
import com.clerova.dto.SendMessageRequest;
import com.clerova.dto.WorkItemEventResponse;
import com.clerova.email.ReplyService;
import com.clerova.repository.AppUserRepository;
import com.clerova.service.ConversationService;
import com.clerova.service.WorkItemEventService;
import com.clerova.security.CurrentUserService;
import com.clerova.service.WorkItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/work-items")
public class WorkItemController {

    private final WorkItemService workItemService;
    private final CurrentUserService currentUserService;
    private final ConversationService conversationService;
    private final WorkItemEventService workItemEventService;
    private final ReplyService replyService;
    private final AppUserRepository appUserRepository;

    public WorkItemController(
            WorkItemService workItemService,
            CurrentUserService currentUserService,
            ConversationService conversationService,
            WorkItemEventService workItemEventService,
            ReplyService replyService,
            AppUserRepository appUserRepository
    ) {
        this.workItemService = workItemService;
        this.currentUserService = currentUserService;
        this.conversationService = conversationService;
        this.workItemEventService = workItemEventService;
        this.replyService = replyService;
        this.appUserRepository = appUserRepository;
    }

    private UUID organizationId() {
        return currentUserService.requireOrganizationId();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItem createWorkItem(
            @Valid @RequestBody CreateWorkItemRequest request
    ) {

        return workItemService.createWorkItemWithProposal(
                organizationId(),
                request.message(),
                currentUserService.currentUserId()
        );
    }

    /** Open requests, or with ?closed=true those closed in the last 30 days. */
    @GetMapping
    public List<WorkItem> getAllWorkItems(
            @RequestParam(defaultValue = "false") boolean closed
    ) {
        return closed
                ? workItemService.getRecentlyClosedWorkItems(organizationId())
                : workItemService.getAllWorkItems(organizationId());
    }

    @GetMapping("/{id}")
    public WorkItem getWorkItem(@PathVariable UUID id) {
        return workItemService.getWorkItem(organizationId(), id);
    }

    @GetMapping("/{id}/conversation")
    public List<ConversationEntryResponse> getConversation(@PathVariable UUID id) {
        return conversationService.getConversation(
                workItemService.getWorkItem(organizationId(), id)
        );
    }

    @GetMapping("/{id}/events")
    public List<WorkItemEventResponse> getEvents(@PathVariable UUID id) {
        workItemService.getWorkItem(organizationId(), id);

        Map<UUID, String> actorEmails = new HashMap<>();

        return workItemEventService.getEvents(id)
                .stream()
                .map(event -> WorkItemEventResponse.from(
                        event,
                        event.getActorUserId() == null
                                ? null
                                : actorEmails.computeIfAbsent(
                                        event.getActorUserId(),
                                        userId -> appUserRepository.findById(userId)
                                                .map(user -> user.getFullName() != null
                                                        ? user.getFullName()
                                                        : user.getEmail())
                                                .orElse("Former user")
                                )
                ))
                .toList();
    }

    @PostMapping("/{id}/close")
    public WorkItem closeWorkItem(
            @PathVariable UUID id,
            @Valid @RequestBody CloseWorkItemRequest request
    ) {
        return workItemService.closeWorkItem(
                organizationId(),
                id,
                request.status(),
                currentUserService.currentUserId()
        );
    }

    /** A draft answer to the sender's latest email while the job is in progress. */
    @PostMapping("/{id}/draft-reply")
    public Map<String, Object> draftReply(@PathVariable UUID id) {
        String reply = workItemService.draftFollowUpReply(organizationId(), id);

        return Map.of(
                "reply", reply,
                "suggestionsLeft", workItemService.getWorkItem(organizationId(), id).getSuggestionsLeft()
        );
    }

    /** Emails a message written by a person to the request's sender. */
    @PostMapping("/{id}/reply")
    public WorkItem sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return replyService.sendMessage(
                organizationId(),
                id,
                request.message(),
                currentUserService.currentUserId()
        );
    }

    /** The sender's new email needs no answer. */
    @PostMapping("/{id}/reply-handled")
    public WorkItem markReplyHandled(@PathVariable UUID id) {
        return workItemService.markReplyHandled(
                organizationId(),
                id,
                currentUserService.currentUserId()
        );
    }

    @PostMapping("/{workItemId}/proposed-actions")
    public ResponseEntity<ProposedActionResponse> generateProposedAction(
            @PathVariable UUID workItemId
    ) {

        ProposedAction proposedAction =
                workItemService.requestProposedAction(
                        organizationId(),
                        workItemId,
                        currentUserService.currentUserId()
                );

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
                        request != null ? request.finalResponse() : null,
                        currentUserService.currentUserId()
                );

        return ResponseEntity.ok(
                toResponse(proposedAction)
        );
    }

    @PostMapping("/proposed-actions/{proposedActionId}/send-reply")
    public ProposedActionResponse sendReply(
            @PathVariable UUID proposedActionId
    ) {
        return toResponse(replyService.sendReply(
                organizationId(),
                proposedActionId,
                currentUserService.currentUserId()
        ));
    }

    @PostMapping("/proposed-actions/{proposedActionId}/reject")
    public ResponseEntity<ProposedActionResponse> rejectProposedAction(
            @PathVariable UUID proposedActionId
    ) {

        ProposedAction proposedAction =
                workItemService.rejectProposedAction(
                        organizationId(),
                        proposedActionId,
                        currentUserService.currentUserId()
                );

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

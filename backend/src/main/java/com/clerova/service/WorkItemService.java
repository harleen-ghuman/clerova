package com.clerova.service;

import com.clerova.ai.AiService;
import com.clerova.domain.ProposedAction;
import com.clerova.domain.ProposedActionStatus;
import com.clerova.domain.WorkItem;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.clerova.repository.ProposedActionRepository;
import com.clerova.repository.WorkItemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkItemService {

    private final AiService aiService;
    private final WorkItemRepository workItemRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final ActionExecutionService actionExecutionService;

    public WorkItemService(
            AiService aiService,
            WorkItemRepository workItemRepository,
            ProposedActionRepository proposedActionRepository,
            ActionExecutionService actionExecutionService
    ) {
        this.aiService = aiService;
        this.workItemRepository = workItemRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.actionExecutionService = actionExecutionService;
    }

    public WorkItem createWorkItem(
            String organizationId,
            String message
    ) {

        ExtractionResult extraction =
                aiService.extract(message);

        WorkItem workItem = new WorkItem();

        workItem.setOrganizationId(organizationId);
        workItem.setOriginalMessage(message);

        workItem.setCategory(extraction.category);
        workItem.setPriority(extraction.priority);
        workItem.setSummary(extraction.summary);
        workItem.setAddress(extraction.address);

        return workItemRepository.save(workItem);
    }

    public List<WorkItem> getAllWorkItems() {
        return workItemRepository.findAll();
    }

    public WorkItem getWorkItem(UUID id) {
        return workItemRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("WorkItem not found: " + id)
                );
    }

    public ProposedAction generateProposedAction(UUID workItemId) {

        WorkItem workItem = workItemRepository.findById(workItemId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Work item not found: " + workItemId
                        )
                );

        proposedActionRepository
                .findFirstByWorkItemIdOrderByCreatedAtDesc(workItemId)
                .ifPresent(latestAction -> {

                    if (latestAction.getStatus()
                            == ProposedActionStatus.PENDING_APPROVAL) {

                        throw new IllegalStateException(
                                "This work item already has a pending proposed action"
                        );
                    }

                    if (latestAction.getStatus()
                            == ProposedActionStatus.APPROVED) {

                        throw new IllegalStateException(
                                "This work item already has an approved proposed action"
                        );
                    }
                });

        ProposedActionResult result =
                aiService.proposeAction(workItem);

        ProposedAction proposedAction = new ProposedAction();

        proposedAction.setWorkItem(workItem);
        proposedAction.setActionType(result.actionType);
        proposedAction.setReasoning(result.reasoning);
        proposedAction.setDraftedResponse(result.draftedResponse);
        proposedAction.setStatus(
                ProposedActionStatus.PENDING_APPROVAL
        );

        return proposedActionRepository.save(proposedAction);
    }

    @Transactional
    public ProposedAction approveProposedAction(UUID proposedActionId) {

        ProposedAction proposedAction =
                proposedActionRepository.findById(proposedActionId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Proposed action not found: "
                                                + proposedActionId
                                )
                        );

        if (proposedAction.getStatus()
                != ProposedActionStatus.PENDING_APPROVAL) {

            throw new IllegalStateException(
                    "Only pending proposed actions can be approved"
            );
        }

        proposedAction.setStatus(
                ProposedActionStatus.APPROVED
        );

        ProposedAction savedAction =
                proposedActionRepository.save(proposedAction);

        switch (savedAction.getActionType()) {
            case CREATE_MAINTENANCE_REQUEST ->
                    actionExecutionService
                            .executeMaintenanceRequest(savedAction);

            default -> {
                // Other action types will be implemented later.
            }
        }

        return savedAction;
    }

    public ProposedAction rejectProposedAction(UUID proposedActionId) {

        ProposedAction proposedAction =
                proposedActionRepository.findById(proposedActionId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Proposed action not found: " + proposedActionId
                                )
                        );

        if (proposedAction.getStatus() != ProposedActionStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Only pending proposed actions can be rejected"
            );
        }

        proposedAction.setStatus(
                ProposedActionStatus.REJECTED
        );

        return proposedActionRepository.save(proposedAction);
    }

    public List<ProposedAction> getProposedActions(UUID workItemId) {

        if (!workItemRepository.existsById(workItemId)) {
            throw new RuntimeException(
                    "Work item not found: " + workItemId
            );
        }

        return proposedActionRepository.findByWorkItemIdOrderByCreatedAtDesc(workItemId);
    }
}

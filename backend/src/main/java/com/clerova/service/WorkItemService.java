package com.clerova.service;

import com.clerova.ai.AiService;
import com.clerova.domain.MaintenanceRequestStatus;
import com.clerova.domain.ProposedAction;
import com.clerova.domain.ProposedActionStatus;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.repository.ProposedActionRepository;
import com.clerova.repository.WorkItemRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkItemService {

    private static final List<WorkItemStatus> CLOSED_STATUSES = List.of(
            WorkItemStatus.COMPLETED,
            WorkItemStatus.CANCELLED
    );

    private static final List<MaintenanceRequestStatus> ACTIVE_MAINTENANCE_STATUSES = List.of(
            MaintenanceRequestStatus.OPEN,
            MaintenanceRequestStatus.IN_PROGRESS
    );

    private final AiService aiService;
    private final WorkItemRepository workItemRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final ActionExecutionService actionExecutionService;
    private final AiUsageService aiUsageService;
    private final TransactionTemplate transactionTemplate;

    public WorkItemService(
            AiService aiService,
            WorkItemRepository workItemRepository,
            ProposedActionRepository proposedActionRepository,
            MaintenanceRequestRepository maintenanceRequestRepository,
            ActionExecutionService actionExecutionService,
            AiUsageService aiUsageService,
            TransactionTemplate transactionTemplate
    ) {
        this.aiService = aiService;
        this.workItemRepository = workItemRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.actionExecutionService = actionExecutionService;
        this.aiUsageService = aiUsageService;
        this.transactionTemplate = transactionTemplate;
    }

    public WorkItem createWorkItem(
            UUID organizationId,
            String message
    ) {

        aiUsageService.consumeAiRequest(organizationId);

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

    public List<WorkItem> getAllWorkItems(UUID organizationId) {
        return workItemRepository
                .findByOrganizationIdAndStatusNotInOrderByCreatedAtDesc(
                        organizationId,
                        CLOSED_STATUSES
                );
    }

    public WorkItem getWorkItem(UUID organizationId, UUID id) {
        return workItemRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() ->
                        new NotFoundException("Work item not found: " + id)
                );
    }

    /**
     * The AI call takes several seconds, so it runs outside a transaction.
     * The work item's optimistic-lock version guards against two concurrent
     * generations both saving a pending proposal.
     */
    public ProposedAction generateProposedAction(
            UUID organizationId,
            UUID workItemId
    ) {

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        ensureCanGenerateProposal(workItem);

        aiUsageService.consumeAiRequest(organizationId);

        ProposedActionResult result =
                aiService.proposeAction(workItem);

        return transactionTemplate.execute(status -> {

            ensureCanGenerateProposal(workItem);

            workItem.setStatus(WorkItemStatus.WAITING_FOR_APPROVAL);

            // Merging the detached work item fails if another request
            // changed it while the AI call was running.
            WorkItem savedWorkItem = workItemRepository.save(workItem);

            ProposedAction proposedAction = new ProposedAction();

            proposedAction.setWorkItem(savedWorkItem);
            proposedAction.setActionType(result.actionType);
            proposedAction.setReasoning(result.reasoning);
            proposedAction.setDraftedResponse(result.draftedResponse);
            proposedAction.setStatus(
                    ProposedActionStatus.PENDING_APPROVAL
            );

            return proposedActionRepository.save(proposedAction);
        });
    }

    private void ensureCanGenerateProposal(WorkItem workItem) {

        if (CLOSED_STATUSES.contains(workItem.getStatus())) {
            throw new IllegalStateException(
                    "This work item is already closed"
            );
        }

        proposedActionRepository
                .findFirstByWorkItemIdOrderByCreatedAtDesc(workItem.getId())
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
    }

    @Transactional
    public ProposedAction approveProposedAction(
            UUID organizationId,
            UUID proposedActionId,
            String finalResponse
    ) {

        ProposedAction proposedAction =
                getPendingProposedAction(organizationId, proposedActionId, "approved");

        proposedAction.setStatus(
                ProposedActionStatus.APPROVED
        );

        proposedAction.setFinalResponse(
                finalResponse != null && !finalResponse.isBlank()
                        ? finalResponse.trim()
                        : proposedAction.getDraftedResponse()
        );

        ProposedAction savedAction =
                proposedActionRepository.save(proposedAction);

        WorkItem workItem = savedAction.getWorkItem();

        WorkItemStatus nextStatus = switch (savedAction.getActionType()) {

            case CREATE_MAINTENANCE_REQUEST -> {
                actionExecutionService
                        .executeMaintenanceRequest(savedAction);

                yield WorkItemStatus.IN_PROGRESS;
            }

            case REQUEST_MORE_INFORMATION ->
                    WorkItemStatus.WAITING_FOR_INFORMATION;

            case ESCALATE_TO_HUMAN ->
                    WorkItemStatus.ESCALATED;

            case RESPOND_TO_CUSTOMER, NO_ACTION ->
                    WorkItemStatus.COMPLETED;
        };

        workItem.setStatus(nextStatus);
        workItemRepository.save(workItem);

        return savedAction;
    }

    @Transactional
    public ProposedAction rejectProposedAction(
            UUID organizationId,
            UUID proposedActionId
    ) {

        ProposedAction proposedAction =
                getPendingProposedAction(organizationId, proposedActionId, "rejected");

        proposedAction.setStatus(
                ProposedActionStatus.REJECTED
        );

        // Back to the review queue so a new proposal can be generated.
        WorkItem workItem = proposedAction.getWorkItem();

        workItem.setStatus(WorkItemStatus.NEW);

        workItemRepository.save(workItem);

        return proposedActionRepository.save(proposedAction);
    }

    private ProposedAction getPendingProposedAction(
            UUID organizationId,
            UUID proposedActionId,
            String verb
    ) {
        ProposedAction proposedAction =
                proposedActionRepository
                        .findByIdAndWorkItemOrganizationId(proposedActionId, organizationId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Proposed action not found: "
                                                + proposedActionId
                                )
                        );

        if (proposedAction.getStatus()
                != ProposedActionStatus.PENDING_APPROVAL) {

            throw new IllegalStateException(
                    "Only pending proposed actions can be " + verb
            );
        }

        return proposedAction;
    }

    /**
     * Manually closes a work item, e.g. after an escalation was handled or
     * the requested information arrived and was dealt with outside Clerova.
     */
    @Transactional
    public WorkItem closeWorkItem(
            UUID organizationId,
            UUID workItemId,
            WorkItemStatus status
    ) {

        if (!CLOSED_STATUSES.contains(status)) {
            throw new IllegalStateException(
                    "Work items can only be closed as COMPLETED or CANCELLED"
            );
        }

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        if (CLOSED_STATUSES.contains(workItem.getStatus())) {
            throw new IllegalStateException(
                    "This work item is already closed"
            );
        }

        if (workItem.getStatus() == WorkItemStatus.WAITING_FOR_APPROVAL) {
            throw new IllegalStateException(
                    "Approve or reject the pending proposed action first"
            );
        }

        if (maintenanceRequestRepository.existsByWorkItemIdAndStatusIn(
                workItemId,
                ACTIVE_MAINTENANCE_STATUSES
        )) {
            throw new IllegalStateException(
                    "Complete or cancel the maintenance request to close this work item"
            );
        }

        workItem.setStatus(status);

        return workItemRepository.save(workItem);
    }

    public List<ProposedAction> getProposedActions(
            UUID organizationId,
            UUID workItemId
    ) {

        getWorkItem(organizationId, workItemId);

        return proposedActionRepository.findByWorkItemIdOrderByCreatedAtDesc(workItemId);
    }

    /**
     * Latest proposed action for every open work item, in one query instead
     * of one request per work item.
     */
    public List<ProposedAction> getLatestProposedActions(UUID organizationId) {

        Map<UUID, ProposedAction> latestByWorkItem = new LinkedHashMap<>();

        for (ProposedAction action : proposedActionRepository
                .findForOpenWorkItemsOrderByCreatedAtDesc(organizationId, CLOSED_STATUSES)) {

            latestByWorkItem.putIfAbsent(action.getWorkItem().getId(), action);
        }

        return new ArrayList<>(latestByWorkItem.values());
    }
}

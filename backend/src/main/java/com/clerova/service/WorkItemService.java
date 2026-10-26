package com.clerova.service;

import com.clerova.exception.ConflictException;
import com.clerova.ai.AiService;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.MaintenanceRequestStatus;
import com.clerova.domain.ProposedAction;
import com.clerova.domain.ProposedActionStatus;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemEventType;
import com.clerova.domain.WorkItemSource;
import com.clerova.domain.WorkItemStatus;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.repository.ProposedActionRepository;
import com.clerova.repository.WorkItemRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkItemService {

    private static final Logger log = LoggerFactory.getLogger(WorkItemService.class);

    private static final List<WorkItemStatus> CLOSED_STATUSES = List.of(
            WorkItemStatus.COMPLETED,
            WorkItemStatus.CANCELLED
    );

    private static final List<MaintenanceRequestStatus> ACTIVE_MAINTENANCE_STATUSES = List.of(
            MaintenanceRequestStatus.OPEN,
            MaintenanceRequestStatus.IN_PROGRESS
    );

    /** Limit on email text sent to the AI for extraction. */
    private static final int MAX_EXTRACTION_CHARS = 8_000;

    private final AiService aiService;
    private final WorkItemRepository workItemRepository;
    private final ProposedActionRepository proposedActionRepository;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final ActionExecutionService actionExecutionService;
    private final AiUsageService aiUsageService;
    private final WorkItemEventService workItemEventService;
    private final ConversationService conversationService;
    private final TransactionTemplate transactionTemplate;

    public WorkItemService(
            AiService aiService,
            WorkItemRepository workItemRepository,
            ProposedActionRepository proposedActionRepository,
            MaintenanceRequestRepository maintenanceRequestRepository,
            ActionExecutionService actionExecutionService,
            AiUsageService aiUsageService,
            WorkItemEventService workItemEventService,
            ConversationService conversationService,
            TransactionTemplate transactionTemplate
    ) {
        this.aiService = aiService;
        this.workItemRepository = workItemRepository;
        this.proposedActionRepository = proposedActionRepository;
        this.maintenanceRequestRepository = maintenanceRequestRepository;
        this.actionExecutionService = actionExecutionService;
        this.aiUsageService = aiUsageService;
        this.workItemEventService = workItemEventService;
        this.conversationService = conversationService;
        this.transactionTemplate = transactionTemplate;
    }

    public WorkItem createWorkItem(
            UUID organizationId,
            String message,
            UUID actorUserId
    ) {

        aiUsageService.consumeRequest(organizationId);

        ExtractionResult extraction =
                aiService.extract(message);

        WorkItem workItem = new WorkItem();

        workItem.setOrganizationId(organizationId);
        workItem.setOriginalMessage(message);
        workItem.setSource(WorkItemSource.MANUAL);

        applyExtraction(workItem, extraction);

        return transactionTemplate.execute(status -> {
            WorkItem saved = workItemRepository.save(workItem);

            workItemEventService.record(
                    organizationId,
                    saved.getId(),
                    WorkItemEventType.CREATED,
                    "Entered manually",
                    actorUserId
            );

            return saved;
        });
    }

    /**
     * Creates a work item from a typed request and, like email requests,
     * proposes an action right away. If the proposal cannot be generated
     * (for example the AI quota is used up), the work item is still
     * returned and the proposal can be generated later.
     */
    public WorkItem createWorkItemWithProposal(
            UUID organizationId,
            String message,
            UUID actorUserId
    ) {
        WorkItem workItem = createWorkItem(organizationId, message, actorUserId);

        try {
            generateProposedAction(organizationId, workItem.getId(), actorUserId);
        } catch (RuntimeException e) {
            log.warn("Could not generate a proposal for work item {}", workItem.getId(), e);
        }

        return getWorkItem(organizationId, workItem.getId());
    }

    /**
     * Creates a work item from a received email and links the email to it.
     */
    public WorkItem createWorkItemFromEmail(InboundMessage email) {

        UUID organizationId = email.getOrganizationId();

        aiUsageService.consumeRequest(organizationId);

        String content = email.getContentText() != null ? email.getContentText() : "";

        String extractionInput = """
                From: %s
                Subject: %s

                %s
                """.formatted(
                email.getFromName() != null
                        ? email.getFromName() + " <" + email.getFromEmail() + ">"
                        : email.getFromEmail(),
                email.getSubject() != null ? email.getSubject() : "(no subject)",
                content.length() > MAX_EXTRACTION_CHARS
                        ? content.substring(0, MAX_EXTRACTION_CHARS)
                        : content
        );

        ExtractionResult extraction = aiService.extract(extractionInput);

        WorkItem workItem = new WorkItem();

        workItem.setOrganizationId(organizationId);
        workItem.setOriginalMessage(
                content.isBlank() && email.getSubject() != null ? email.getSubject() : content
        );
        workItem.setSource(WorkItemSource.EMAIL);
        workItem.setSenderEmail(email.getFromEmail());
        workItem.setSenderName(email.getFromName());
        workItem.setSubject(email.getSubject());

        applyExtraction(workItem, extraction);

        return transactionTemplate.execute(status -> {
            WorkItem saved = workItemRepository.save(workItem);

            workItemEventService.record(
                    organizationId,
                    saved.getId(),
                    WorkItemEventType.CREATED,
                    "Received by email from " + email.getFromEmail(),
                    null
            );

            return saved;
        });
    }

    private static void applyExtraction(WorkItem workItem, ExtractionResult extraction) {
        workItem.setCategory(extraction.category);
        workItem.setPriority(extraction.priority);
        workItem.setSummary(extraction.summary);
        workItem.setAddress(extraction.address);
    }

    public List<WorkItem> getAllWorkItems(UUID organizationId) {
        return workItemRepository
                .findByOrganizationIdAndStatusNotInOrderByCreatedAtDesc(
                        organizationId,
                        CLOSED_STATUSES
                );
    }

    /** Requests closed in the last 30 days, most recently closed first. */
    public List<WorkItem> getRecentlyClosedWorkItems(UUID organizationId) {
        return workItemRepository
                .findTop100ByOrganizationIdAndStatusInAndUpdatedAtAfterOrderByUpdatedAtDesc(
                        organizationId,
                        CLOSED_STATUSES,
                        Instant.now().minus(Duration.ofDays(30))
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
     *
     * @param actorUserId the requesting user, or null when Clerova generates
     *                    the proposal automatically for an email
     */
    public ProposedAction generateProposedAction(
            UUID organizationId,
            UUID workItemId,
            UUID actorUserId
    ) {

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        ensureCanGenerateProposal(workItem);

        aiUsageService.requireAccess(organizationId);

        ProposedActionResult result = aiService.proposeAction(
                workItem,
                conversationService.conversationForAi(workItem)
        );

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

            ProposedAction saved = proposedActionRepository.save(proposedAction);

            workItemEventService.record(
                    organizationId,
                    workItemId,
                    WorkItemEventType.PROPOSAL_GENERATED,
                    "Recommended " + humanize(result.actionType.name()),
                    actorUserId
            );

            return saved;
        });
    }

    private void ensureCanGenerateProposal(WorkItem workItem) {

        if (CLOSED_STATUSES.contains(workItem.getStatus())) {
            throw new ConflictException(
                    "This request is already closed."
            );
        }

        proposedActionRepository
                .findFirstByWorkItemIdOrderByCreatedAtDesc(workItem.getId())
                .ifPresent(latestAction -> {

                    if (latestAction.getStatus()
                            == ProposedActionStatus.PENDING_APPROVAL) {

                        throw new ConflictException(
                                "This request already has a suggestion waiting for review."
                        );
                    }

                    // A NEW work item with an approved action was reopened
                    // by a follow-up email and needs a fresh proposal.
                    if (latestAction.getStatus() != ProposedActionStatus.REJECTED
                            && workItem.getStatus() != WorkItemStatus.NEW) {

                        throw new ConflictException(
                                "A suggestion for this request was already approved."
                        );
                    }
                });
    }

    /**
     * A follow-up email arrived for a work item. Items waiting on the sender
     * or on a decision, and recently closed items, go back to review; a
     * pending proposal is superseded because it did not see the new message.
     *
     * @return true if the work item needs a new proposal
     */
    @Transactional
    public boolean reopenForFollowUp(UUID organizationId, UUID workItemId, String fromEmail) {

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        workItemEventService.record(
                organizationId,
                workItemId,
                WorkItemEventType.FOLLOW_UP_RECEIVED,
                "New email from " + fromEmail,
                null
        );

        switch (workItem.getStatus()) {

            case WAITING_FOR_APPROVAL -> {
                proposedActionRepository
                        .findFirstByWorkItemIdOrderByCreatedAtDesc(workItemId)
                        .filter(action -> action.getStatus() == ProposedActionStatus.PENDING_APPROVAL)
                        .ifPresent(action -> {
                            action.setStatus(ProposedActionStatus.REJECTED);
                            action.recordDecision(null);
                            proposedActionRepository.save(action);

                            workItemEventService.record(
                                    organizationId,
                                    workItemId,
                                    WorkItemEventType.PROPOSAL_REJECTED,
                                    "Superseded by a new email",
                                    null
                            );
                        });
            }

            case NEW, WAITING_FOR_INFORMATION, ESCALATED -> {
                // Back to review below
            }

            case COMPLETED, CANCELLED -> workItemEventService.record(
                    organizationId,
                    workItemId,
                    WorkItemEventType.REOPENED,
                    "Reopened by a new email",
                    null
            );

            default -> {
                // In progress (e.g. maintenance underway): keep the message
                // on the item without changing its workflow.
                return false;
            }
        }

        workItem.setStatus(WorkItemStatus.NEW);
        workItemRepository.save(workItem);

        return true;
    }

    @Transactional
    public ProposedAction approveProposedAction(
            UUID organizationId,
            UUID proposedActionId,
            String finalResponse,
            UUID actorUserId
    ) {

        ProposedAction proposedAction =
                getPendingProposedAction(organizationId, proposedActionId, "approved");

        proposedAction.setStatus(
                ProposedActionStatus.APPROVED
        );
        proposedAction.recordDecision(actorUserId);

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

        workItemEventService.record(
                organizationId,
                workItem.getId(),
                WorkItemEventType.PROPOSAL_APPROVED,
                "Approved " + humanize(savedAction.getActionType().name()),
                actorUserId
        );

        return savedAction;
    }

    @Transactional
    public ProposedAction rejectProposedAction(
            UUID organizationId,
            UUID proposedActionId,
            UUID actorUserId
    ) {

        ProposedAction proposedAction =
                getPendingProposedAction(organizationId, proposedActionId, "rejected");

        proposedAction.setStatus(
                ProposedActionStatus.REJECTED
        );
        proposedAction.recordDecision(actorUserId);

        // Back to the review queue so a new proposal can be generated.
        WorkItem workItem = proposedAction.getWorkItem();

        workItem.setStatus(WorkItemStatus.NEW);

        workItemRepository.save(workItem);

        workItemEventService.record(
                organizationId,
                workItem.getId(),
                WorkItemEventType.PROPOSAL_REJECTED,
                "Rejected " + humanize(proposedAction.getActionType().name()),
                actorUserId
        );

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

            throw new ConflictException(
                    "This suggestion was already handled. Refresh to see the latest."
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
            WorkItemStatus status,
            UUID actorUserId
    ) {

        if (!CLOSED_STATUSES.contains(status)) {
            throw new ConflictException(
                    "Choose whether the request is done or cancelled."
            );
        }

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        if (CLOSED_STATUSES.contains(workItem.getStatus())) {
            throw new ConflictException(
                    "This request is already closed."
            );
        }

        if (workItem.getStatus() == WorkItemStatus.WAITING_FOR_APPROVAL) {
            throw new ConflictException(
                    "Approve or dismiss Clerova's suggestion first."
            );
        }

        if (maintenanceRequestRepository.existsByWorkItemIdAndStatusIn(
                workItemId,
                ACTIVE_MAINTENANCE_STATUSES
        )) {
            throw new ConflictException(
                    "Finish or cancel the job for this request first."
            );
        }

        workItem.setStatus(status);

        workItemEventService.record(
                organizationId,
                workItemId,
                WorkItemEventType.CLOSED,
                status == WorkItemStatus.COMPLETED ? "Marked resolved" : "Cancelled",
                actorUserId
        );

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

    static String humanize(String enumName) {
        return enumName.toLowerCase().replace('_', ' ');
    }
}

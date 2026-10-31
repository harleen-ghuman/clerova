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
import com.clerova.dto.UrgencyResult;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.MaintenanceRequestRepository;
import com.clerova.repository.ProposedActionRepository;
import com.clerova.repository.WorkItemRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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

    /** Limit on a follow-up's text sent to the AI for the urgency check. */
    private static final int MAX_URGENCY_CHARS = 4_000;

    static final String SUGGESTIONS_USED_UP =
            "You've used all " + WorkItem.MAX_SUGGESTIONS + " new suggestions for this email. "
                    + "Edit the reply yourself. You get " + WorkItem.MAX_SUGGESTIONS + " more with the next email.";

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
    private final ApplicationEventPublisher events;

    public WorkItemService(
            AiService aiService,
            WorkItemRepository workItemRepository,
            ProposedActionRepository proposedActionRepository,
            MaintenanceRequestRepository maintenanceRequestRepository,
            ActionExecutionService actionExecutionService,
            AiUsageService aiUsageService,
            WorkItemEventService workItemEventService,
            ConversationService conversationService,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher events
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
        this.events = events;
    }

    public WorkItem createWorkItem(
            UUID organizationId,
            String message,
            UUID actorUserId
    ) {

        aiUsageService.requireQuota(organizationId);

        ExtractionResult extraction =
                aiService.extract(message);

        // Counted only once the AI has read it
        aiUsageService.consumeRequest(organizationId);

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
                    "Added with New request",
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
    public WorkItem createWorkItemFromEmail(InboundMessage email, List<String> attachmentNames) {

        UUID organizationId = email.getOrganizationId();

        aiUsageService.requireQuota(organizationId);

        String content = email.getContentText() != null ? email.getContentText() : "";
        String attachments = attachmentNames.isEmpty()
                ? ""
                : "Attachments: " + String.join(", ", attachmentNames);

        String extractionInput = """
                From: %s
                Subject: %s

                %s
                %s
                """.formatted(
                email.getFromName() != null
                        ? email.getFromName() + " <" + email.getFromEmail() + ">"
                        : email.getFromEmail(),
                email.getSubject() != null ? email.getSubject() : "(no subject)",
                content.length() > MAX_EXTRACTION_CHARS
                        ? content.substring(0, MAX_EXTRACTION_CHARS)
                        : content,
                attachments
        );

        ExtractionResult extraction = aiService.extract(extractionInput);

        // Counted only once the AI has read it, so retries don't add up
        aiUsageService.consumeRequest(organizationId);

        WorkItem workItem = new WorkItem();

        workItem.setOrganizationId(organizationId);
        // A photo-only email still shows something in the request
        workItem.setOriginalMessage(
                !content.isBlank() ? content
                        : email.getSubject() != null && !email.getSubject().isBlank() ? email.getSubject()
                        : "(No message text. " + attachments + ")"
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
        // The column holds 255 characters; a long AI answer must not fail the save
        workItem.setAddress(extraction.address != null && extraction.address.length() > 255
                ? extraction.address.substring(0, 255)
                : extraction.address);
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
     * A person asked Clerova for a new suggestion. Each email in the
     * conversation allows {@link WorkItem#MAX_SUGGESTIONS} of these, shared
     * with drafts; a call that fails gives its turn back.
     */
    public ProposedAction requestProposedAction(
            UUID organizationId,
            UUID workItemId,
            UUID actorUserId
    ) {
        getWorkItem(organizationId, workItemId);
        useSuggestion(workItemId);

        try {
            return generateProposedAction(organizationId, workItemId, actorUserId);
        } catch (RuntimeException e) {
            workItemRepository.refundSuggestion(workItemId);
            throw e;
        }
    }

    private void useSuggestion(UUID workItemId) {
        if (workItemRepository.useSuggestion(workItemId, WorkItem.MAX_SUGGESTIONS) == 0) {
            throw new ConflictException(SUGGESTIONS_USED_UP);
        }
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
                    "Clerova suggested " + describe(result.actionType),
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

        workItemRepository.resetSuggestions(workItemId);

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
                                    "Suggestion replaced because a new email arrived",
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
                // In progress (e.g. maintenance underway): the job keeps
                // going, but the request is flagged so someone answers.
                workItem.setUnreadReplyAt(Instant.now());
                workItemRepository.save(workItem);
                return false;
            }
        }

        workItem.setStatus(WorkItemStatus.NEW);
        workItemRepository.save(workItem);

        return true;
    }

    /**
     * Drafts an answer to the sender's latest email on a request whose job
     * is in progress. Nothing is saved; a person edits and sends it.
     */
    public String draftFollowUpReply(UUID organizationId, UUID workItemId) {

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        if (workItem.getSource() != WorkItemSource.EMAIL || workItem.getSenderEmail() == null) {
            throw new ConflictException("This request didn't come in by email, so there's no one to reply to.");
        }

        requireCanWrite(workItem);

        useSuggestion(workItemId);

        try {
            aiUsageService.requireAccess(organizationId);

            String conversation = conversationService.conversationForAi(workItem);

            return aiService.draftReply(
                    workItem,
                    conversation != null ? conversation : "Message from the resident:\n" + workItem.getOriginalMessage(),
                    openRepairJob(organizationId, workItemId)
            );
        } catch (RuntimeException e) {
            workItemRepository.refundSuggestion(workItemId);
            throw e;
        }
    }

    /** The request's open job and the team's notes, for drafting a reply. */
    private AiService.RepairJob openRepairJob(UUID organizationId, UUID workItemId) {
        return maintenanceRequestRepository
                .findByWorkItemIdAndOrganizationIdOrderByCreatedAtDesc(workItemId, organizationId)
                .stream()
                .filter(job -> job.getStatus() == MaintenanceRequestStatus.OPEN
                        || job.getStatus() == MaintenanceRequestStatus.IN_PROGRESS)
                .findFirst()
                .map(job -> new AiService.RepairJob(
                        job.getStatus() == MaintenanceRequestStatus.IN_PROGRESS, job.getNotes()))
                .orElse(null);
    }

    /**
     * People write to the sender directly while a job or escalation is
     * underway, or to remind a sender who hasn't answered. Otherwise replies
     * go through Clerova's suggestion, so a pending suggestion never goes
     * stale.
     */
    public static void requireCanWrite(WorkItem workItem) {
        switch (workItem.getStatus()) {
            case IN_PROGRESS, ESCALATED, WAITING_FOR_INFORMATION -> {
            }
            case COMPLETED, CANCELLED -> throw new ConflictException("This request is already closed.");
            default -> throw new ConflictException(
                    "Reply using Clerova's suggestion for this request."
            );
        }
    }

    /**
     * Checks whether a new email that arrived during a job is urgent, and if
     * so marks the request and tells the owners. Best effort: if the AI or
     * the plan stops it, the request keeps its plain "New reply" flag.
     */
    public void checkFollowUpUrgency(UUID organizationId, UUID workItemId, String message) {

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        if (workItem.getUnreadReplyAt() == null
                || workItem.isUnreadReplyUrgent()
                || message == null
                || message.isBlank()) {
            return;
        }

        UrgencyResult result;

        try {
            aiUsageService.requireAccess(organizationId);
            // The start says what is happening; a long quoted tail adds cost only
            result = aiService.checkUrgency(
                    workItem,
                    message.length() > MAX_URGENCY_CHARS ? message.substring(0, MAX_URGENCY_CHARS) : message
            );
        } catch (RuntimeException e) {
            log.warn("Could not check the urgency of a follow-up for work item {}", workItemId, e);
            return;
        }

        if (result == null || !result.urgent) {
            return;
        }

        String reason = result.reason != null && !result.reason.isBlank()
                ? result.reason.trim()
                : "The new email looks urgent.";

        try {
            markUrgent(organizationId, workItemId, reason, message);
        } catch (RuntimeException e) {
            // E.g. the request changed at the same moment; the email itself
            // is already attached, so don't fail its processing
            log.warn("Could not mark work item {} urgent", workItemId, e);
        }
    }

    private void markUrgent(UUID organizationId, UUID workItemId, String reason, String message) {
        transactionTemplate.executeWithoutResult(status -> {
            WorkItem current = getWorkItem(organizationId, workItemId);

            // Answered or closed while the check ran
            if (current.getUnreadReplyAt() == null) {
                return;
            }

            current.setUnreadReplyUrgent(true);
            workItemRepository.save(current);

            workItemEventService.record(
                    organizationId,
                    workItemId,
                    WorkItemEventType.REPLY_URGENT,
                    "Marked urgent: " + reason,
                    null
            );

            events.publishEvent(new UrgentReplyReceived(
                    organizationId,
                    workItemId,
                    current.getSenderName() != null ? current.getSenderName() : current.getSenderEmail(),
                    current.getSubject() != null && !current.getSubject().isBlank()
                            ? current.getSubject()
                            : current.getSummary(),
                    reason,
                    message
            ));
        });
    }

    /** The sender's new email needs no answer, so the alert goes away. */
    @Transactional
    public WorkItem markReplyHandled(UUID organizationId, UUID workItemId, UUID actorUserId) {

        WorkItem workItem = getWorkItem(organizationId, workItemId);

        if (workItem.getUnreadReplyAt() == null) {
            return workItem;
        }

        workItem.setUnreadReplyAt(null);

        workItemEventService.record(
                organizationId,
                workItemId,
                WorkItemEventType.REPLY_HANDLED,
                "Marked the new email as handled",
                actorUserId
        );

        return workItemRepository.save(workItem);
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

        workItemRepository.resetSuggestions(workItem.getId());

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
                "Approved " + describe(savedAction.getActionType()),
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
                "Dismissed the suggestion",
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
                    "This was already handled, maybe in another tab. This is the latest."
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
                status == WorkItemStatus.COMPLETED ? "Marked done" : "Request cancelled",
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

    /** How a suggestion reads in a request's activity. */
    static String describe(com.clerova.domain.ActionType actionType) {
        return switch (actionType) {
            case CREATE_MAINTENANCE_REQUEST -> "a maintenance job and a reply";
            case RESPOND_TO_CUSTOMER -> "a reply";
            case REQUEST_MORE_INFORMATION -> "asking for more details";
            case ESCALATE_TO_HUMAN -> "escalating to your team";
            case NO_ACTION -> "closing with no action";
        };
    }
}

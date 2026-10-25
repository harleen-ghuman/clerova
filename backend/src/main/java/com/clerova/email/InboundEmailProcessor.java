package com.clerova.email;

import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import com.clerova.exception.AiServiceException;
import com.clerova.exception.ApiException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.WorkItemRepository;
import com.clerova.service.WorkItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns stored inbound emails into work items in the background: skips
 * automated mail, threads replies onto existing work items, and runs AI
 * extraction plus an automatic proposal for new requests.
 */
@Component
public class InboundEmailProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailProcessor.class);

    public static final String GMAIL_CONFIRMATION_REASON = "Gmail forwarding confirmation";
    public static final String AUTOMATED_REASON = "Automated or bulk email";

    static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 10;
    private static final Duration STALE_PROCESSING = Duration.ofMinutes(10);

    /**
     * A reply to a request closed within this window reopens it; replies to
     * older closed requests start a new one.
     */
    static final Duration REOPEN_WINDOW = Duration.ofDays(14);

    private static final List<WorkItemStatus> CLOSED_STATUSES = List.of(
            WorkItemStatus.COMPLETED,
            WorkItemStatus.CANCELLED
    );

    private final boolean enabled;
    private final InboundMessageRepository inboundMessageRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemService workItemService;
    private final TransactionTemplate transactionTemplate;

    public InboundEmailProcessor(
            @Value("${app.email.processing.enabled}") boolean enabled,
            InboundMessageRepository inboundMessageRepository,
            WorkItemRepository workItemRepository,
            WorkItemService workItemService,
            TransactionTemplate transactionTemplate
    ) {
        this.enabled = enabled;
        this.inboundMessageRepository = inboundMessageRepository;
        this.workItemRepository = workItemRepository;
        this.workItemService = workItemService;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(fixedDelayString = "${app.email.processing.interval-ms}")
    public void runScheduled() {
        if (enabled) {
            processDue();
        }
    }

    /** Processes emails that are due. Returns how many were processed. */
    public int processDue() {
        Instant now = Instant.now();

        transactionTemplate.executeWithoutResult(status ->
                inboundMessageRepository.releaseStale(now.minus(STALE_PROCESSING))
        );

        List<UUID> dueIds = inboundMessageRepository.findDueIds(
                InboundMessageStatus.RECEIVED,
                now,
                PageRequest.of(0, BATCH_SIZE)
        );

        int processed = 0;

        for (UUID id : dueIds) {
            Integer claimed = transactionTemplate.execute(status ->
                    inboundMessageRepository.claim(id)
            );

            if (claimed != null && claimed == 1) {
                process(id);
                processed++;
            }
        }

        return processed;
    }

    private void process(UUID messageId) {
        InboundMessage message = inboundMessageRepository.findById(messageId).orElseThrow();

        try {
            handle(message);

        } catch (AiServiceException e) {
            log.warn("AI processing failed for inbound email {} (attempt {})",
                    messageId, message.getAttempts(), e);

            if (message.getAttempts() >= MAX_ATTEMPTS) {
                finish(message, InboundMessageStatus.FAILED,
                        "AI processing failed after " + MAX_ATTEMPTS + " attempts");
            } else {
                retryLater(message);
            }

        } catch (ApiException e) {
            // Trial ended or quota used up: needs a person to act
            finish(message, InboundMessageStatus.FAILED, e.getMessage());

        } catch (RuntimeException e) {
            log.error("Unexpected error processing inbound email {}", messageId, e);

            if (message.getAttempts() >= MAX_ATTEMPTS) {
                finish(message, InboundMessageStatus.FAILED, "Processing failed");
            } else {
                retryLater(message);
            }
        }
    }

    private void handle(InboundMessage message) {

        if (isGmailForwardingConfirmation(message)) {
            finish(message, InboundMessageStatus.IGNORED, GMAIL_CONFIRMATION_REASON);
            return;
        }

        if (message.isAutomated()) {
            finish(message, InboundMessageStatus.IGNORED, AUTOMATED_REASON);
            return;
        }

        Optional<WorkItem> existing = findThreadWorkItem(message);

        if (existing.isPresent()) {
            attachFollowUp(message, existing.get());
            return;
        }

        WorkItem workItem = workItemService.createWorkItemFromEmail(message);

        message.setWorkItemId(workItem.getId());
        finish(message, InboundMessageStatus.PROCESSED, null);

        generateProposal(workItem);
    }

    private void attachFollowUp(InboundMessage message, WorkItem workItem) {
        message.setWorkItemId(workItem.getId());
        finish(message, InboundMessageStatus.ATTACHED, null);

        boolean needsProposal = workItemService.reopenForFollowUp(
                workItem.getOrganizationId(),
                workItem.getId(),
                message.getFromEmail()
        );

        if (needsProposal) {
            generateProposal(workItem);
        }
    }

    /**
     * The work item exists and the email is handled; a failed proposal is
     * logged and can be generated from the work item later.
     */
    private void generateProposal(WorkItem workItem) {
        try {
            workItemService.generateProposedAction(
                    workItem.getOrganizationId(),
                    workItem.getId(),
                    null
            );
        } catch (RuntimeException e) {
            log.warn("Could not generate a proposal for work item {}", workItem.getId(), e);
        }
    }

    /**
     * The work item this email replies to, found from the reply-to address
     * hash or from the threading headers, if it can still take replies.
     */
    private Optional<WorkItem> findThreadWorkItem(InboundMessage message) {
        UUID organizationId = message.getOrganizationId();

        Optional<WorkItem> byHash = WorkItemMailboxHash.parse(message.getMailboxHash())
                .flatMap(id -> workItemRepository.findByIdAndOrganizationId(id, organizationId));

        if (byHash.isPresent()) {
            return byHash.filter(InboundEmailProcessor::acceptsReplies);
        }

        List<String> referencedIds = new ArrayList<>();

        if (message.getInReplyTo() != null) {
            referencedIds.add(message.getInReplyTo().trim());
        }

        if (message.getReferences() != null) {
            referencedIds.addAll(Arrays.stream(message.getReferences().split("\\s+"))
                    .filter(id -> !id.isBlank())
                    .toList());
        }

        if (referencedIds.isEmpty()) {
            return Optional.empty();
        }

        return inboundMessageRepository
                .findByOrganizationIdAndMessageIdHeaderInAndWorkItemIdIsNotNull(
                        organizationId,
                        referencedIds
                )
                .stream()
                .map(InboundMessage::getWorkItemId)
                .distinct()
                .map(id -> workItemRepository.findByIdAndOrganizationId(id, organizationId))
                .flatMap(Optional::stream)
                .filter(InboundEmailProcessor::acceptsReplies)
                .findFirst();
    }

    /** Open, or closed recently enough that a reply should reopen it. */
    static boolean acceptsReplies(WorkItem workItem) {
        if (!CLOSED_STATUSES.contains(workItem.getStatus())) {
            return true;
        }

        return workItem.getUpdatedAt() != null
                && workItem.getUpdatedAt().isAfter(Instant.now().minus(REOPEN_WINDOW));
    }

    static boolean isGmailForwardingConfirmation(InboundMessage message) {
        String from = message.getFromEmail() == null
                ? ""
                : message.getFromEmail().toLowerCase(Locale.ROOT);

        return from.equals("forwarding-noreply@google.com");
    }

    private void retryLater(InboundMessage message) {
        Duration delay = message.getAttempts() <= 1 ? Duration.ofMinutes(1) : Duration.ofMinutes(5);

        message.setStatus(InboundMessageStatus.RECEIVED);
        message.setNextAttemptAt(Instant.now().plus(delay));

        inboundMessageRepository.save(message);
    }

    private void finish(InboundMessage message, InboundMessageStatus status, String reason) {
        message.setStatus(status);
        message.setStatusReason(reason);
        message.setNextAttemptAt(null);

        inboundMessageRepository.save(message);
    }
}

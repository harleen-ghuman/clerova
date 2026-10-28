package com.clerova.email;

import com.clerova.billing.BillingService;
import com.clerova.config.AiRateLimitInterceptor;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemStatus;
import com.clerova.exception.AiServiceException;
import com.clerova.exception.ApiException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.WorkItemRepository;
import com.clerova.service.AiUsageService;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    public static final String OWN_COPY_REASON = "Your copy of an email sent through Clerova";
    public static final String EMPTY_REASON = "Empty email, with no subject, text or attachments";
    public static final String RETRY_REASON = "Couldn't read this email yet. Clerova will try again in a few minutes.";
    static final String QUOTA_WAIT_REASON =
            "Your plan's requests for this month are used up. This email will be organized on %s, or as soon as you upgrade.";

    static final String PLAN_WAIT_REASON =
            "Your plan has ended. This email will be organized as soon as you choose a plan.";

    public static final String FAILED_REASON = "Couldn't read this email. Click Retry to try again.";

    static final int MAX_ATTEMPTS = 3;

    /** More follow-ups than this on one request within the window skip the AI. */
    static final int FOLLOW_UP_BURST_LIMIT = 10;
    static final Duration FOLLOW_UP_BURST_WINDOW = Duration.ofHours(1);
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
    private final AiUsageService aiUsageService;
    private final AiRateLimitInterceptor aiRateLimiter;
    private final TransactionTemplate transactionTemplate;
    private final EmailProperties emailProperties;
    private final ReplyCopyService replyCopyService;

    public InboundEmailProcessor(
            @Value("${app.email.processing.enabled}") boolean enabled,
            InboundMessageRepository inboundMessageRepository,
            WorkItemRepository workItemRepository,
            WorkItemService workItemService,
            AiUsageService aiUsageService,
            AiRateLimitInterceptor aiRateLimiter,
            TransactionTemplate transactionTemplate,
            EmailProperties emailProperties,
            ReplyCopyService replyCopyService
    ) {
        this.enabled = enabled;
        this.inboundMessageRepository = inboundMessageRepository;
        this.workItemRepository = workItemRepository;
        this.workItemService = workItemService;
        this.aiUsageService = aiUsageService;
        this.aiRateLimiter = aiRateLimiter;
        this.transactionTemplate = transactionTemplate;
        this.emailProperties = emailProperties;
        this.replyCopyService = replyCopyService;
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
                    inboundMessageRepository.claim(id, Instant.now())
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

            failOrRetry(message);

        } catch (ApiException e) {
            if (AiUsageService.QUOTA_EXCEEDED.equals(e.getCode())) {
                waitForQuota(message);
            } else if (BillingService.SUBSCRIPTION_REQUIRED.equals(e.getCode())) {
                waitForPlan(message);
            } else {
                // Plan or trial ended: needs a person to act
                finish(message, InboundMessageStatus.FAILED, e.getMessage());
            }

        } catch (RuntimeException e) {
            log.error("Unexpected error processing inbound email {}", messageId, e);

            failOrRetry(message);
        }
    }

    private void handle(InboundMessage message) {

        if (isGmailForwardingConfirmation(message)) {
            finish(message, InboundMessageStatus.IGNORED, GMAIL_CONFIRMATION_REASON);
            return;
        }

        if (message.isAutomated()) {
            // A forwarded inbox also forwards the copy of each sent reply
            finish(message, InboundMessageStatus.IGNORED,
                    isFromClerova(message.getFromEmail()) ? OWN_COPY_REASON : AUTOMATED_REASON);
            return;
        }

        Optional<WorkItem> existing = findThreadWorkItem(message);

        if (existing.isPresent()) {
            attachFollowUp(message, existing.get());
            return;
        }

        String text = message.getContentText();
        boolean noText = (text == null || text.isBlank())
                && (message.getSubject() == null || message.getSubject().isBlank());
        List<String> attachmentNames = inboundMessageRepository.findAttachmentNames(message.getId());

        // Nothing to read: don't spend a request on it
        if (noText && attachmentNames.isEmpty()) {
            finish(message, InboundMessageStatus.IGNORED, EMPTY_REASON);
            return;
        }

        WorkItem workItem = workItemService.createWorkItemFromEmail(message, attachmentNames);

        message.setWorkItemId(workItem.getId());
        finish(message, InboundMessageStatus.PROCESSED, null);

        // E.g. a reply to a request closed long ago, which starts a new one
        replyCopyService.copyIncoming(message, workItem);

        generateProposal(workItem);
    }

    private boolean isFromClerova(String fromEmail) {
        return fromEmail != null && fromEmail.trim().toLowerCase(java.util.Locale.ROOT)
                .endsWith("@" + emailProperties.getInboundDomain().toLowerCase(java.util.Locale.ROOT));
    }

    private void attachFollowUp(InboundMessage message, WorkItem workItem) {
        message.setWorkItemId(workItem.getId());
        finish(message, InboundMessageStatus.ATTACHED, null);

        boolean needsProposal = workItemService.reopenForFollowUp(
                workItem.getOrganizationId(),
                workItem.getId(),
                message.getFromEmail()
        );

        // Follow-ups don't count against the plan, so a burst of them (a
        // loop, or someone replying over and over) must not run the AI
        // each time; the request is still updated and flagged.
        long recent = inboundMessageRepository.countByWorkItemIdAndReceivedAtAfter(
                workItem.getId(), Instant.now().minus(FOLLOW_UP_BURST_WINDOW));

        // A burst isn't relayed to the company's inbox either
        if (recent <= FOLLOW_UP_BURST_LIMIT) {
            replyCopyService.copyIncoming(message, workItem);
        }

        if (recent > FOLLOW_UP_BURST_LIMIT || !aiRateLimiter.tryAcquireBackground()) {
            log.warn("Skipping automatic AI for follow-up on work item {} ({} emails in the last hour)",
                    workItem.getId(), recent);
            return;
        }

        if (needsProposal) {
            generateProposal(workItem);
        } else {
            // During a job: the request is flagged; check whether it's urgent
            workItemService.checkFollowUpUrgency(
                    workItem.getOrganizationId(),
                    workItem.getId(),
                    message.getContentText()
            );
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

    private static String truncate(String reason) {
        return reason != null && reason.length() > 500 ? reason.substring(0, 497) + "..." : reason;
    }

    static boolean isGmailForwardingConfirmation(InboundMessage message) {
        String from = message.getFromEmail() == null
                ? ""
                : message.getFromEmail().toLowerCase(Locale.ROOT);

        return from.equals("forwarding-noreply@google.com");
    }

    /**
     * The error itself is in the log; the Inbox gets a plain reason, since
     * provider errors can name keys and internals.
     */
    private void failOrRetry(InboundMessage message) {
        if (message.getAttempts() >= MAX_ATTEMPTS) {
            finish(message, InboundMessageStatus.FAILED, FAILED_REASON);
        } else {
            retryLater(message);
        }
    }

    /**
     * The month's requests are used up. The email waits until they reset,
     * or until an upgrade (BillingService releases waiting emails then).
     */
    private void waitForQuota(InboundMessage message) {
        Instant reset = aiUsageService.nextReset();

        message.setStatus(InboundMessageStatus.RECEIVED);
        message.setAttempts(0);
        message.setStatusReason(QUOTA_WAIT_REASON.formatted(
                DateTimeFormatter.ofPattern("MMMM d", Locale.US).withZone(ZoneOffset.UTC).format(reset)
        ));
        message.setNextAttemptAt(reset);

        inboundMessageRepository.save(message);
    }

    /**
     * The plan ended. The email waits for a new plan (BillingService
     * releases waiting emails then) and is checked again weekly.
     */
    private void waitForPlan(InboundMessage message) {
        message.setStatus(InboundMessageStatus.RECEIVED);
        message.setAttempts(0);
        message.setStatusReason(PLAN_WAIT_REASON);
        message.setNextAttemptAt(Instant.now().plus(Duration.ofDays(7)));

        inboundMessageRepository.save(message);
    }

    private void retryLater(InboundMessage message) {
        Duration delay = message.getAttempts() <= 1 ? Duration.ofMinutes(1) : Duration.ofMinutes(5);

        message.setStatus(InboundMessageStatus.RECEIVED);
        message.setStatusReason(RETRY_REASON);
        message.setNextAttemptAt(Instant.now().plus(delay));

        inboundMessageRepository.save(message);
    }

    private void finish(InboundMessage message, InboundMessageStatus status, String reason) {
        message.setStatus(status);
        message.setStatusReason(truncate(reason));
        message.setNextAttemptAt(null);

        inboundMessageRepository.save(message);
    }
}

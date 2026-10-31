package com.clerova.email;

import com.clerova.exception.ConflictException;
import com.clerova.domain.ActionType;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.Organization;
import com.clerova.domain.OutboundMessage;
import com.clerova.domain.ProposedAction;
import com.clerova.domain.ProposedActionStatus;
import com.clerova.domain.WorkItem;
import com.clerova.domain.WorkItemEventType;
import com.clerova.domain.WorkItemSource;
import com.clerova.exception.ApiException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import com.clerova.repository.OutboundMessageRepository;
import com.clerova.repository.ProposedActionRepository;
import com.clerova.repository.WorkItemRepository;
import com.clerova.service.WorkItemEventService;
import com.clerova.service.WorkItemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends an approved response to the person who emailed the request. The
 * reply comes from the organization's Clerova address, and its reply-to
 * routes answers back to the same work item.
 */
@Service
public class ReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReplyService.class);

    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final InboundAddressService inboundAddressService;
    private final ProposedActionRepository proposedActionRepository;
    private final OrganizationRepository organizationRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final OutboundMessageRepository outboundMessageRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemEventService workItemEventService;
    private final TransactionTemplate transactionTemplate;

    public ReplyService(
            EmailProperties emailProperties,
            EmailSender emailSender,
            InboundAddressService inboundAddressService,
            ProposedActionRepository proposedActionRepository,
            OrganizationRepository organizationRepository,
            InboundMessageRepository inboundMessageRepository,
            OutboundMessageRepository outboundMessageRepository,
            WorkItemRepository workItemRepository,
            WorkItemEventService workItemEventService,
            TransactionTemplate transactionTemplate
    ) {
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
        this.inboundAddressService = inboundAddressService;
        this.proposedActionRepository = proposedActionRepository;
        this.organizationRepository = organizationRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.outboundMessageRepository = outboundMessageRepository;
        this.workItemRepository = workItemRepository;
        this.workItemEventService = workItemEventService;
        this.transactionTemplate = transactionTemplate;
    }

    private record PreparedReply(
            UUID workItemId,
            ProposedAction action,
            OutgoingEmail email
    ) {
    }

    public ProposedAction sendReply(UUID organizationId, UUID proposedActionId, UUID actorUserId) {

        requireSendingEnabled();

        // Marking the action EXECUTED first (optimistic locking) makes a
        // double click fail instead of sending the email twice.
        PreparedReply prepared = transactionTemplate.execute(status ->
                prepare(organizationId, proposedActionId)
        );

        String providerMessageId;

        try {
            providerMessageId = send(prepared.email());
        } catch (EmailSendException e) {
            transactionTemplate.executeWithoutResult(status -> {
                ProposedAction action = proposedActionRepository.findById(proposedActionId).orElseThrow();
                action.setStatus(ProposedActionStatus.APPROVED);
                proposedActionRepository.save(action);
            });

            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_SEND_FAILED", e.getMessage());
        }

        return transactionTemplate.execute(status -> {
            recordSent(organizationId, prepared.workItemId(), proposedActionId,
                    prepared.email(), providerMessageId, actorUserId);

            return proposedActionRepository.findById(proposedActionId).orElseThrow();
        });
    }

    /**
     * Sends a message written by a person (no suggestion behind it), e.g.
     * answering the sender while the request's job is in progress. Clears
     * the request's new-email alert.
     */
    public WorkItem sendMessage(UUID organizationId, UUID workItemId, String message, UUID actorUserId) {

        // No plan check: finishing existing requests stays allowed
        requireSendingEnabled();

        Instant startedAt = Instant.now();

        OutgoingEmail email = transactionTemplate.execute(status -> {
            WorkItem workItem = workItemRepository.findByIdAndOrganizationId(workItemId, organizationId)
                    .orElseThrow(() -> new NotFoundException("Work item not found: " + workItemId));

            WorkItemService.requireCanWrite(workItem);

            return buildEmail(organizationId, workItem, message.trim());
        });

        String providerMessageId;

        try {
            providerMessageId = send(email);
        } catch (EmailSendException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_SEND_FAILED", e.getMessage());
        }

        // Recorded on its own so the sent email is never lost from the thread
        transactionTemplate.executeWithoutResult(status ->
                recordSent(organizationId, workItemId, null, email, providerMessageId, actorUserId)
        );

        try {
            return transactionTemplate.execute(status -> {
                WorkItem workItem = workItemRepository.findById(workItemId).orElseThrow();

                // An email that arrived while this one was sending stays flagged
                if (workItem.getUnreadReplyAt() != null && workItem.getUnreadReplyAt().isBefore(startedAt)) {
                    workItem.setUnreadReplyAt(null);
                }

                // Restarts "no answer in 3+ days" after a reminder
                workItem.setUpdatedAt(Instant.now());

                return workItemRepository.save(workItem);
            });
        } catch (OptimisticLockingFailureException e) {
            // Changed at the same moment; the alert stays and can be dismissed
            return workItemRepository.findById(workItemId).orElseThrow();
        }
    }

    /**
     * The company's copy must never stop the reply itself: if the email is
     * refused with the copy (e.g. the copy address bounced before), it is
     * sent again without it. Only a clear refusal is retried, since after a
     * timeout the first email may already have gone out.
     */
    private String send(OutgoingEmail email) {
        try {
            return emailSender.send(email);
        } catch (EmailSendException e) {
            if (email.bcc() == null || !e.isRejected()) {
                throw e;
            }

            log.warn("Reply refused with the company copy; sending it without the copy: {}", e.getMessage());

            return emailSender.send(new OutgoingEmail(
                    email.from(),
                    email.replyTo(),
                    email.to(),
                    email.subject(),
                    email.textBody(),
                    email.inReplyTo(),
                    email.references()
            ));
        }
    }

    private void requireSendingEnabled() {
        if (!emailProperties.isSendingEnabled()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_SENDING_DISABLED",
                    "Sending email isn't set up yet. Copy the reply and send it from your own email."
            );
        }
    }

    private void recordSent(
            UUID organizationId,
            UUID workItemId,
            UUID proposedActionId,
            OutgoingEmail email,
            String providerMessageId,
            UUID actorUserId
    ) {
        OutboundMessage outbound = new OutboundMessage();

        outbound.setOrganizationId(organizationId);
        outbound.setWorkItemId(workItemId);
        outbound.setProposedActionId(proposedActionId);
        outbound.setToEmail(email.to());
        outbound.setSubject(email.subject());
        outbound.setBody(email.textBody());
        outbound.setProviderMessageId(providerMessageId);
        outbound.setSentByUserId(actorUserId);

        outboundMessageRepository.save(outbound);

        workItemRepository.resetSuggestions(workItemId);

        workItemEventService.record(
                organizationId,
                workItemId,
                WorkItemEventType.REPLY_SENT,
                "Reply sent to " + email.to(),
                actorUserId
        );
    }

    private PreparedReply prepare(UUID organizationId, UUID proposedActionId) {
        ProposedAction action = proposedActionRepository
                .findByIdAndWorkItemOrganizationId(proposedActionId, organizationId)
                .orElseThrow(() -> new NotFoundException("Proposed action not found: " + proposedActionId));

        if (action.getStatus() != ProposedActionStatus.APPROVED) {
            throw new ConflictException(
                    action.getStatus() == ProposedActionStatus.EXECUTED
                            ? "This reply was already sent."
                            : "Approve the reply before sending it."
            );
        }

        if (action.getActionType() == ActionType.NO_ACTION) {
            throw new ConflictException("There's no reply to send.");
        }

        String body = action.getFinalResponse() != null
                ? action.getFinalResponse()
                : action.getDraftedResponse();

        OutgoingEmail email = buildEmail(organizationId, action.getWorkItem(), body);

        action.setStatus(ProposedActionStatus.EXECUTED);
        proposedActionRepository.saveAndFlush(action);

        return new PreparedReply(action.getWorkItem().getId(), action, email);
    }

    /** The email to the sender, threaded with the request's earlier emails. */
    private OutgoingEmail buildEmail(UUID organizationId, WorkItem workItem, String body) {

        if (workItem.getSource() != WorkItemSource.EMAIL || workItem.getSenderEmail() == null) {
            throw new ConflictException("This request didn't come in by email, so there's no one to reply to.");
        }

        if (body == null || body.isBlank()) {
            throw new ConflictException("The reply is empty. Add a message before sending.");
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        String token = inboundAddressService.getOrCreateToken(organization);

        List<String> threadIds = inboundMessageRepository
                .findByWorkItemIdOrderByReceivedAtAsc(workItem.getId())
                .stream()
                .map(InboundMessage::getMessageIdHeader)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        String inReplyTo = threadIds.isEmpty() ? null : threadIds.get(threadIds.size() - 1);
        String references = threadIds.isEmpty() ? null : threadIds.stream().collect(Collectors.joining(" "));

        // The customer's own copy; pointless if they are writing to themselves
        String copy = organization.getReplyCopyEmail();
        String bcc = copy != null && !copy.equalsIgnoreCase(workItem.getSenderEmail()) ? copy : null;

        return new OutgoingEmail(
                "\"" + displayName(organization.getName()) + " via Clerova\" <"
                        + emailProperties.forwardingAddress(token) + ">",
                emailProperties.replyAddress(token, workItem.getId()),
                workItem.getSenderEmail(),
                replySubject(workItem.getSubject()),
                withSignOff(body, displayName(organization.getName())),
                inReplyTo,
                references,
                bcc
        );
    }

    static String replySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Re: Your request";
        }

        return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
    }

    /**
     * Ends the reply with the company's name, as the drafts leave it out.
     * Skipped when the reply already ends with that name.
     */
    static String withSignOff(String body, String companyName) {
        String trimmed = body.stripTrailing();

        if (companyName == null || companyName.isBlank()) {
            return trimmed;
        }

        String[] lines = trimmed.split("\\R");
        String lastLine = lines[lines.length - 1];

        if (lastLine.toLowerCase(Locale.ROOT).contains(companyName.toLowerCase(Locale.ROOT))) {
            return trimmed;
        }

        // "Thanks," is followed by the name on the next line
        return trimmed + (lastLine.strip().endsWith(",") ? "\n" : "\n\n") + companyName;
    }

    private static String displayName(String name) {
        return name.replaceAll("[\"\\r\\n<>]", "").trim();
    }
}

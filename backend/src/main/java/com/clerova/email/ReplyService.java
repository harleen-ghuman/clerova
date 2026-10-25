package com.clerova.email;

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
import com.clerova.service.WorkItemEventService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sends an approved response to the person who emailed the request. The
 * reply comes from the organization's Clerova address, and its reply-to
 * routes answers back to the same work item.
 */
@Service
public class ReplyService {

    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final InboundAddressService inboundAddressService;
    private final ProposedActionRepository proposedActionRepository;
    private final OrganizationRepository organizationRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final OutboundMessageRepository outboundMessageRepository;
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

        if (!emailProperties.isSendingEnabled()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EMAIL_SENDING_DISABLED",
                    "Sending email is not set up yet. Copy the response and send it yourself."
            );
        }

        // Marking the action EXECUTED first (optimistic locking) makes a
        // double click fail instead of sending the email twice.
        PreparedReply prepared = transactionTemplate.execute(status ->
                prepare(organizationId, proposedActionId)
        );

        String providerMessageId;

        try {
            providerMessageId = emailSender.send(prepared.email());
        } catch (EmailSendException e) {
            transactionTemplate.executeWithoutResult(status -> {
                ProposedAction action = proposedActionRepository.findById(proposedActionId).orElseThrow();
                action.setStatus(ProposedActionStatus.APPROVED);
                proposedActionRepository.save(action);
            });

            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_SEND_FAILED", e.getMessage());
        }

        return transactionTemplate.execute(status -> {
            OutboundMessage outbound = new OutboundMessage();

            outbound.setOrganizationId(organizationId);
            outbound.setWorkItemId(prepared.workItemId());
            outbound.setProposedActionId(proposedActionId);
            outbound.setToEmail(prepared.email().to());
            outbound.setSubject(prepared.email().subject());
            outbound.setBody(prepared.email().textBody());
            outbound.setProviderMessageId(providerMessageId);
            outbound.setSentByUserId(actorUserId);

            outboundMessageRepository.save(outbound);

            workItemEventService.record(
                    organizationId,
                    prepared.workItemId(),
                    WorkItemEventType.REPLY_SENT,
                    "Reply sent to " + prepared.email().to(),
                    actorUserId
            );

            return proposedActionRepository.findById(proposedActionId).orElseThrow();
        });
    }

    private PreparedReply prepare(UUID organizationId, UUID proposedActionId) {
        ProposedAction action = proposedActionRepository
                .findByIdAndWorkItemOrganizationId(proposedActionId, organizationId)
                .orElseThrow(() -> new NotFoundException("Proposed action not found: " + proposedActionId));

        if (action.getStatus() != ProposedActionStatus.APPROVED) {
            throw new IllegalStateException(
                    action.getStatus() == ProposedActionStatus.EXECUTED
                            ? "This reply was already sent"
                            : "Only approved responses can be sent"
            );
        }

        if (action.getActionType() == ActionType.NO_ACTION) {
            throw new IllegalStateException("There is no response to send for this action");
        }

        WorkItem workItem = action.getWorkItem();

        if (workItem.getSource() != WorkItemSource.EMAIL || workItem.getSenderEmail() == null) {
            throw new IllegalStateException("This request did not come in by email");
        }

        String body = action.getFinalResponse() != null
                ? action.getFinalResponse()
                : action.getDraftedResponse();

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("The approved response is empty");
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

        OutgoingEmail email = new OutgoingEmail(
                "\"" + displayName(organization.getName()) + " via Clerova\" <"
                        + emailProperties.forwardingAddress(token) + ">",
                emailProperties.replyAddress(token, workItem.getId()),
                workItem.getSenderEmail(),
                replySubject(workItem.getSubject()),
                body,
                inReplyTo,
                references
        );

        action.setStatus(ProposedActionStatus.EXECUTED);
        proposedActionRepository.saveAndFlush(action);

        return new PreparedReply(workItem.getId(), action, email);
    }

    static String replySubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "Re: Your request";
        }

        return subject.regionMatches(true, 0, "Re:", 0, 3) ? subject : "Re: " + subject;
    }

    private static String displayName(String name) {
        return name.replaceAll("[\"\\r\\n<>]", "").trim();
    }
}

package com.clerova.email;

import com.clerova.billing.BillingProperties;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.Organization;
import com.clerova.domain.WorkItem;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Sends the company a copy of a resident's email that came straight to
 * Clerova, usually a reply to an email sent from Clerova, so their own
 * inbox has the whole conversation. Emails forwarded from their inbox are
 * already there and aren't copied.
 */
@Service
public class ReplyCopyService {

    private static final Logger log = LoggerFactory.getLogger(ReplyCopyService.class);

    private static final int MAX_MESSAGE_CHARS = 20_000;

    private final OrganizationRepository organizationRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final InboundAddressService inboundAddressService;
    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final BillingProperties billingProperties;

    public ReplyCopyService(
            OrganizationRepository organizationRepository,
            InboundMessageRepository inboundMessageRepository,
            InboundAddressService inboundAddressService,
            EmailProperties emailProperties,
            EmailSender emailSender,
            BillingProperties billingProperties
    ) {
        this.organizationRepository = organizationRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.inboundAddressService = inboundAddressService;
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
        this.billingProperties = billingProperties;
    }

    /** Best effort: the email is already handled, so a failed copy is only logged. */
    public void copyIncoming(InboundMessage message, WorkItem workItem) {
        // Only replies to a Clerova email carry the request in the address
        if (!emailProperties.isSendingEnabled()
                || WorkItemMailboxHash.parse(message.getMailboxHash()).isEmpty()) {
            return;
        }

        try {
            Organization organization = organizationRepository
                    .findById(message.getOrganizationId())
                    .orElseThrow();
            String copyTo = organization.getReplyCopyEmail();

            if (copyTo == null || copyTo.equalsIgnoreCase(message.getFromEmail())) {
                return;
            }

            String token = inboundAddressService.getOrCreateToken(organization);
            String messageId = message.getMessageIdHeader();
            String references = message.getReferences() == null || message.getReferences().isBlank()
                    ? messageId
                    : messageId == null ? message.getReferences() : message.getReferences() + " " + messageId;

            emailSender.send(new OutgoingEmail(
                    "\"" + displayName(sender(message)) + " via Clerova\" <"
                            + emailProperties.forwardingAddress(token) + ">",
                    message.getFromEmail(),
                    copyTo,
                    subject(message, workItem),
                    body(message),
                    messageId,
                    references
            ));
        } catch (RuntimeException e) {
            log.warn("Could not copy inbound email {} to the company: {}", message.getId(), e.getMessage());
        }
    }

    String body(InboundMessage message) {
        String text = message.getContentText() == null ? "" : message.getContentText().trim();

        if (text.length() > MAX_MESSAGE_CHARS) {
            text = text.substring(0, MAX_MESSAGE_CHARS) + "...";
        }

        List<String> attachments = inboundMessageRepository.findAttachmentNames(message.getId());

        return sender(message) + " <" + message.getFromEmail() + "> replied to an email you sent through Clerova.\n"
                + "To keep the conversation in one place, answer in Clerova: " + billingProperties.getAppUrl() + "\n\n"
                + "----------\n\n"
                + (text.isEmpty() ? "(No text)" : text)
                + (attachments.isEmpty() ? "" : "\n\nAttachments (open the request in Clerova to see them): "
                        + String.join(", ", attachments));
    }

    private static String subject(InboundMessage message, WorkItem workItem) {
        String subject = message.getSubject() != null && !message.getSubject().isBlank()
                ? message.getSubject()
                : workItem.getSubject();

        return subject == null || subject.isBlank()
                ? "New email from " + sender(message)
                : subject.replaceAll("[\\r\\n]+", " ");
    }

    private static String sender(InboundMessage message) {
        return message.getFromName() != null && !message.getFromName().isBlank()
                ? message.getFromName().trim()
                : message.getFromEmail();
    }

    private static String displayName(String name) {
        return name.replaceAll("[\"\\r\\n<>]", "").trim();
    }
}

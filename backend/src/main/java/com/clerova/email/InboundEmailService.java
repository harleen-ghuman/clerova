package com.clerova.email;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.Organization;
import com.clerova.repository.InboundMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Stores inbound emails for background processing. Receiving is kept fast
 * so the email provider's webhook does not time out and retry.
 */
@Service
public class InboundEmailService {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);

    private static final int MAX_BODY_LENGTH = 100_000;

    private final EmailProperties emailProperties;
    private final InboundAddressService inboundAddressService;
    private final InboundMessageRepository inboundMessageRepository;

    public InboundEmailService(
            EmailProperties emailProperties,
            InboundAddressService inboundAddressService,
            InboundMessageRepository inboundMessageRepository
    ) {
        this.emailProperties = emailProperties;
        this.inboundAddressService = inboundAddressService;
        this.inboundMessageRepository = inboundMessageRepository;
    }

    public enum ReceiveResult {
        STORED,
        DUPLICATE,
        UNKNOWN_RECIPIENT
    }

    public ReceiveResult receive(ParsedInboundEmail email) {
        Optional<Recipient> recipient = findRecipient(email);

        if (recipient.isEmpty()) {
            log.warn("Inbound email {} has no known Clerova recipient", email.providerMessageId());
            return ReceiveResult.UNKNOWN_RECIPIENT;
        }

        Organization organization = recipient.get().organization();

        String dedupeKey = email.messageId() != null && !email.messageId().isBlank()
                ? email.messageId().trim()
                : "provider:" + email.providerMessageId();

        if (inboundMessageRepository.existsByOrganizationIdAndDedupeKey(organization.getId(), dedupeKey)) {
            return ReceiveResult.DUPLICATE;
        }

        InboundMessage message = new InboundMessage();

        message.setOrganizationId(organization.getId());
        message.setDedupeKey(truncate(dedupeKey, 998));
        message.setProviderMessageId(email.providerMessageId());
        message.setMessageIdHeader(truncate(trimToNull(email.messageId()), 998));
        message.setInReplyTo(truncate(trimToNull(email.inReplyTo()), 998));
        message.setReferences(email.references());
        message.setMailboxHash(recipient.get().mailboxHash());
        message.setFromEmail(truncate(trimToNull(email.fromEmail()), 320));
        message.setFromName(truncate(trimToNull(email.fromName()), 255));
        message.setToAddresses(String.join(", ", email.recipients()));
        message.setSubject(truncate(email.subject(), 1000));
        message.setTextBody(truncate(email.textBody(), MAX_BODY_LENGTH));
        message.setReplyText(truncate(email.replyText(), MAX_BODY_LENGTH));
        message.setAutomated(email.automated() || isFromClerovaOrBounce(email.fromEmail()));
        // Our own receive time: the sender's Date header can be wrong or
        // spoofed, and ordering must match when Clerova saw each message.
        message.setReceivedAt(Instant.now());

        for (ParsedInboundEmail.Attachment parsed : email.attachments()) {
            InboundAttachment attachment = new InboundAttachment();

            attachment.setFileName(truncate(parsed.fileName(), 500));
            attachment.setContentType(truncate(parsed.contentType(), 255));
            attachment.setSizeBytes(parsed.sizeBytes());

            message.addAttachment(attachment);
        }

        try {
            inboundMessageRepository.save(message);
        } catch (DataIntegrityViolationException e) {
            // The same email arrived twice at the same moment
            return ReceiveResult.DUPLICATE;
        }

        return ReceiveResult.STORED;
    }

    private record Recipient(Organization organization, String mailboxHash) {
    }

    private Optional<Recipient> findRecipient(ParsedInboundEmail email) {
        String domain = "@" + emailProperties.getInboundDomain();

        for (String address : email.recipients()) {
            String normalized = address.trim().toLowerCase(Locale.ROOT);

            if (!normalized.endsWith(domain)) {
                continue;
            }

            String localPart = normalized.substring(0, normalized.length() - domain.length());
            int plus = localPart.indexOf('+');

            String token = plus >= 0 ? localPart.substring(0, plus) : localPart;
            String hash = plus >= 0 ? localPart.substring(plus + 1) : null;

            Optional<Organization> organization = inboundAddressService.findByToken(token);

            if (organization.isPresent()) {
                return Optional.of(new Recipient(organization.get(), hash));
            }
        }

        return Optional.empty();
    }

    /**
     * Clerova's own emails (replies, usage warnings) and bounce notices. A
     * customer who forwards their whole inbox would otherwise loop them back
     * in as new requests. Marked automated, so "Process anyway" still works.
     */
    boolean isFromClerovaOrBounce(String fromEmail) {
        String from = fromEmail == null ? "" : fromEmail.trim().toLowerCase(Locale.ROOT);
        int at = from.lastIndexOf('@');

        if (at < 0) {
            return false;
        }

        String local = from.substring(0, at);
        String domain = from.substring(at + 1);
        String inboundDomain = emailProperties.getInboundDomain().toLowerCase(Locale.ROOT);

        boolean ownNoReply = inboundDomain.endsWith("." + domain)
                && (local.startsWith("noreply") || local.startsWith("no-reply")
                        || local.startsWith("notifications"));

        return domain.equals(inboundDomain)
                || ownNoReply
                || local.equals("mailer-daemon")
                || local.equals("postmaster");
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int maxLength) {
        return value != null && value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}

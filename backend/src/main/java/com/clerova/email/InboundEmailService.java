package com.clerova.email;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.Organization;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores inbound emails for background processing. Receiving is kept fast
 * so the email provider's webhook does not time out and retry.
 */
@Service
public class InboundEmailService {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailService.class);

    private static final int MAX_BODY_LENGTH = 100_000;

    /** Larger files keep only their name; the sender's original email still has them. */
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final long MAX_EMAIL_BYTES = 25L * 1024 * 1024;

    private final EmailProperties emailProperties;
    private final InboundAddressService inboundAddressService;
    private final InboundMessageRepository inboundMessageRepository;
    private final FileStorage fileStorage;

    public InboundEmailService(
            EmailProperties emailProperties,
            InboundAddressService inboundAddressService,
            InboundMessageRepository inboundMessageRepository,
            FileStorage fileStorage
    ) {
        this.emailProperties = emailProperties;
        this.inboundAddressService = inboundAddressService;
        this.inboundMessageRepository = inboundMessageRepository;
        this.fileStorage = fileStorage;
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
        // Replies go where the email asks: a portal's no-reply sender with
        // the resident in Reply-To becomes the resident. Clerova's own mail
        // and copies keep their sender, so they are still recognized.
        boolean useReplyTo = email.replyToEmail() != null
                && !email.replyToEmail().equalsIgnoreCase(String.valueOf(email.fromEmail()).trim())
                && !isFromClerovaOrBounce(email.fromEmail())
                && !isFromClerovaOrBounce(email.replyToEmail());

        message.setFromEmail(truncate(trimToNull(useReplyTo ? email.replyToEmail() : email.fromEmail()), 320));
        message.setFromName(truncate(trimToNull(useReplyTo ? email.replyToName() : email.fromName()), 255));
        message.setToAddresses(String.join(", ", email.recipients()));
        message.setSubject(truncate(email.subject(), 1000));
        message.setTextBody(truncate(email.textBody(), MAX_BODY_LENGTH));
        message.setReplyText(truncate(email.replyText(), MAX_BODY_LENGTH));
        message.setAutomated(email.automated() || isFromClerovaOrBounce(email.fromEmail()));
        // Our own receive time: the sender's Date header can be wrong or
        // spoofed, and ordering must match when Clerova saw each message.
        message.setReceivedAt(Instant.now());

        long keptBytes = 0;

        for (ParsedInboundEmail.Attachment parsed : email.attachments()) {
            InboundAttachment attachment = new InboundAttachment();
            // Clearly too-large files aren't decoded at all (base64 is 4 characters
            // per 3 bytes, plus line breaks); the exact size is checked below
            byte[] content = parsed.content() != null
                    && (long) parsed.content().length() * 3 / 4 <= MAX_FILE_BYTES + MAX_FILE_BYTES / 20
                    ? decode(parsed.content())
                    : null;

            attachment.setFileName(truncate(parsed.fileName(), 500));
            attachment.setContentType(truncate(parsed.contentType(), 255));
            attachment.setSizeBytes(parsed.sizeBytes() != null || content == null
                    ? parsed.sizeBytes()
                    : Long.valueOf(content.length));

            if (content != null
                    && content.length <= MAX_FILE_BYTES
                    && keptBytes + content.length <= MAX_EMAIL_BYTES) {
                String path = keep(organization, parsed, content);

                if (path != null) {
                    attachment.setStoragePath(path);
                    keptBytes += content.length;
                }
            }

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

    /**
     * Saves one file, trying twice. If storage is down the email is still
     * kept with the file's name, since losing the email would be worse.
     *
     * @return the storage path, or null if the file wasn't kept
     */
    private String keep(Organization organization, ParsedInboundEmail.Attachment parsed, byte[] content) {
        if (!fileStorage.isEnabled()) {
            return null;
        }

        String path = organization.getId() + "/" + UUID.randomUUID() + "/" + safeFileName(parsed.fileName());
        String contentType = parsed.contentType() != null && !parsed.contentType().isBlank()
                ? parsed.contentType().trim()
                : "application/octet-stream";

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                fileStorage.put(path, content, contentType);
                return path;
            } catch (FileStorageException e) {
                log.warn("Could not keep attachment {} (attempt {}): {}", parsed.fileName(), attempt, e.getMessage());
            }
        }

        log.error("Kept only the name of attachment {} on inbound email {}", parsed.fileName(), parsed);
        return null;
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }

        try {
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Letters, digits, dots, dashes and underscores; the real name stays in the database. */
    static String safeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replaceAll(".*[/\\\\]", "");
        name = name.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+", "");

        if (name.length() > 100) {
            name = name.substring(name.length() - 100);
        }

        return name.isBlank() ? "file" : name;
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

package com.clerova.trucking;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import com.clerova.domain.Organization;
import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSendException;
import com.clerova.email.EmailSender;
import com.clerova.email.InboundAddressService;
import com.clerova.email.OutgoingEmail;
import com.clerova.email.ReplyService;
import com.clerova.exception.ApiException;
import com.clerova.exception.ConflictException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Loads for the app, always within one organization. */
@Service
public class LoadService {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);

    static final int MAX_NOTE = 500;
    static final int MAX_REPLY = 10_000;

    private final LoadRepository loadRepository;
    private final LoadReplyRepository loadReplyRepository;
    private final BrokerRepository brokerRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final OrganizationRepository organizationRepository;
    private final InboundAddressService inboundAddressService;
    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final LoadDocumentRepository loadDocumentRepository;

    public LoadService(
            LoadRepository loadRepository,
            LoadReplyRepository loadReplyRepository,
            BrokerRepository brokerRepository,
            InboundMessageRepository inboundMessageRepository,
            OrganizationRepository organizationRepository,
            InboundAddressService inboundAddressService,
            EmailProperties emailProperties,
            EmailSender emailSender,
            LoadDocumentRepository loadDocumentRepository
    ) {
        this.loadDocumentRepository = loadDocumentRepository;
        this.loadRepository = loadRepository;
        this.loadReplyRepository = loadReplyRepository;
        this.brokerRepository = brokerRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.organizationRepository = organizationRepository;
        this.inboundAddressService = inboundAddressService;
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
    }

    @Transactional(readOnly = true)
    public List<LoadResponses.LoadSummary> list(UUID organizationId) {
        List<Load> loads = loadRepository.findForOrganization(organizationId);
        Map<UUID, Integer> drafts = new HashMap<>();

        if (!loads.isEmpty()) {
            for (Object[] row : loadReplyRepository.countByLoad(
                    loads.stream().map(Load::getId).toList(), ReplyStatus.DRAFT)) {
                drafts.put((UUID) row[0], ((Number) row[1]).intValue());
            }
        }

        return loads.stream()
                .map(load -> LoadResponses.summary(load, drafts.getOrDefault(load.getId(), 0)))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadResponses.LoadDetail get(UUID organizationId, UUID loadId) {
        return detail(require(organizationId, loadId));
    }

    /** Marks one thing to check as done, like "It's fine" on a flag. */
    @Transactional
    public LoadResponses.LoadDetail resolveFlag(UUID organizationId, UUID loadId, UUID flagId) {
        Load load = require(organizationId, loadId);
        LoadFlag flag = load.getFlags().stream()
                .filter(candidate -> candidate.getId().equals(flagId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Flag not found"));

        if (flag.getResolvedAt() == null) {
            flag.setResolvedAt(Instant.now());
        }

        return detail(load);
    }

    /** The dispatcher moves a load along, or types the latest update. */
    @Transactional
    public LoadResponses.LoadDetail update(UUID organizationId, UUID loadId, LoadStatus status, String statusNote) {
        Load load = require(organizationId, loadId);

        if (status != null) {
            load.setStatus(status);
        }

        if (statusNote != null) {
            String note = statusNote.strip();

            if (note.length() > MAX_NOTE) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NOTE_TOO_LONG",
                        "Keep the update under " + MAX_NOTE + " characters.");
            }

            load.setStatusNote(note.isEmpty() ? null : note);
        }

        return detail(loadRepository.save(load));
    }

    /** Sends the drafted reply, as edited, to the broker. */
    @Transactional
    public LoadResponses.LoadDetail sendReply(UUID organizationId, UUID loadId, UUID replyId, String body) {
        Load load = require(organizationId, loadId);
        LoadReply reply = loadReplyRepository.findByIdAndLoadId(replyId, loadId)
                .orElseThrow(() -> new NotFoundException("Reply not found"));

        if (reply.getStatus() != ReplyStatus.DRAFT) {
            throw new ConflictException(reply.getStatus() == ReplyStatus.SENT
                    ? "This reply was already sent."
                    : "This draft was replaced by a newer one. Refresh to see it.");
        }

        String text = body == null ? "" : body.strip();

        if (text.isEmpty()) {
            throw new ConflictException("The reply is empty. Add a message before sending.");
        }

        if (text.length() > MAX_REPLY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPLY_TOO_LONG", "The reply is too long.");
        }

        if (!emailProperties.isSendingEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_SENDING_DISABLED",
                    "Sending email isn't set up yet. Copy the reply and send it from your own email.");
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        String token = inboundAddressService.getOrCreateToken(organization);
        InboundMessage answered = reply.getInboundMessageId() == null ? null
                : inboundMessageRepository.findById(reply.getInboundMessageId()).orElse(null);
        String inReplyTo = answered != null ? answered.getMessageIdHeader() : null;
        String references = answered == null ? null
                : ((answered.getReferences() == null ? "" : answered.getReferences() + " ")
                        + (inReplyTo == null ? "" : inReplyTo)).strip();

        OutgoingEmail email = new OutgoingEmail(
                "\"" + organization.getName().replaceAll("[\"\\r\\n<>]", "").trim() + " via Clerova\" <"
                        + emailProperties.forwardingAddress(token) + ">",
                emailProperties.loadReplyAddress(token, loadId),
                reply.getToEmail(),
                reply.getSubject() != null ? reply.getSubject() : ReplyService.replySubject("Load " + orEmpty(load.getLoadNumber())),
                ReplyService.withSignOff(text, organization.getName()),
                inReplyTo,
                references == null || references.isEmpty() ? null : references
        );

        String providerId;

        try {
            providerId = emailSender.send(email);
        } catch (EmailSendException e) {
            log.warn("Couldn't send the reply on load {}: {}", loadId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_NOT_SENT",
                    "The email couldn't be sent. Please try again in a minute.");
        }

        reply.setBody(email.textBody());
        reply.setStatus(ReplyStatus.SENT);
        reply.setSentAt(Instant.now());
        reply.setProviderMessageId(providerId);

        return detail(load);
    }

    @Transactional
    public LoadResponses.LoadDetail dismissReply(UUID organizationId, UUID loadId, UUID replyId) {
        Load load = require(organizationId, loadId);
        LoadReply reply = loadReplyRepository.findByIdAndLoadId(replyId, loadId)
                .orElseThrow(() -> new NotFoundException("Reply not found"));

        if (reply.getStatus() == ReplyStatus.DRAFT) {
            reply.setStatus(ReplyStatus.DISMISSED);
        }

        return detail(load);
    }

    @Transactional(readOnly = true)
    public List<LoadResponses.BrokerSummary> brokers(UUID organizationId) {
        Map<UUID, List<Load>> loadsByBroker = loadRepository.findForOrganization(organizationId).stream()
                .filter(load -> load.getBroker() != null)
                .collect(Collectors.groupingBy(load -> load.getBroker().getId()));

        return brokerRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .map(broker -> {
                    List<Load> loads = loadsByBroker.getOrDefault(broker.getId(), List.of());
                    int open = (int) loads.stream()
                            .filter(load -> TruckingIntakeService.OPEN.contains(load.getStatus())).count();
                    return new LoadResponses.BrokerSummary(LoadResponses.BrokerInfo.of(broker), loads.size(), open);
                })
                .toList();
    }

    private LoadResponses.LoadDetail detail(Load load) {
        LoadReply draft = null;
        List<LoadResponses.ConversationEntry> conversation = new ArrayList<>();
        List<LoadResponses.DocumentInfo> documents = new ArrayList<>();

        for (InboundMessage message : inboundMessageRepository.findByLoadIdOrderByReceivedAtAsc(load.getId())) {
            String from = message.getFromName() != null && !message.getFromName().isBlank()
                    ? message.getFromName() + " <" + message.getFromEmail() + ">"
                    : message.getFromEmail();
            List<LoadResponses.FileInfo> files = new ArrayList<>();

            for (InboundAttachment attachment : message.getAttachments()) {
                files.add(new LoadResponses.FileInfo(attachment.getId(), attachment.getFileName(),
                        attachment.getContentType(), attachment.getSizeBytes(), attachment.getStoragePath() != null));
            }

            for (InboundAttachment attachment : message.getAttachments()) {
                if (isDocument(attachment)) {
                    documents.add(new LoadResponses.DocumentInfo(attachment.getId(), LoadResponses.DocumentInfo.EMAIL,
                            attachment.getDocumentKind() == null ? null : DocumentKind.parse(attachment.getDocumentKind()),
                            attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes(),
                            attachment.getStoragePath() != null, message.getReceivedAt()));
                }
            }

            conversation.add(new LoadResponses.ConversationEntry(message.getId(), true, from, null,
                    message.getSubject(), message.getTextBody(), message.getReceivedAt(), files));
        }

        for (LoadReply reply : loadReplyRepository.findByLoadIdOrderByCreatedAtAsc(load.getId())) {
            if (reply.getStatus() == ReplyStatus.SENT) {
                conversation.add(new LoadResponses.ConversationEntry(reply.getId(), false, null, reply.getToEmail(),
                        reply.getSubject(), reply.getBody(), reply.getSentAt(), List.of()));
            } else if (reply.getStatus() == ReplyStatus.DRAFT) {
                draft = reply;
            }
        }

        conversation.sort(Comparator.comparing(LoadResponses.ConversationEntry::at,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        loadDocumentRepository.findByLoadIdOrderByCreatedAtAsc(load.getId())
                .forEach(document -> documents.add(LoadResponses.DocumentInfo.of(document)));
        documents.sort(Comparator.comparing(LoadResponses.DocumentInfo::at,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        return LoadResponses.detail(load, draft == null ? null : LoadResponses.ReplyInfo.of(draft), conversation,
                documents);
    }

    /** PDFs and photos big enough to be a document (not a logo). */
    static boolean isDocument(InboundAttachment attachment) {
        return PdfText.isPdf(attachment.getFileName(), attachment.getContentType())
                || (DocumentImages.isImage(attachment.getFileName(), attachment.getContentType())
                && attachment.getSizeBytes() != null
                && attachment.getSizeBytes() >= TruckingIntakeService.MIN_PHOTO_BYTES);
    }

    public static final String FILED_BY_HAND_REASON = "Filed under this load by you.";

    /**
     * Files an email under a load by hand: one Clerova couldn't match, or
     * one it put on the wrong load. An email that made a load stays put.
     */
    @Transactional
    public LoadResponses.LoadDetail fileEmail(UUID organizationId, UUID loadId, UUID messageId) {
        Load load = require(organizationId, loadId);
        InboundMessage message = inboundMessageRepository.findByIdAndOrganizationId(messageId, organizationId)
                .orElseThrow(() -> new NotFoundException("Email not found"));

        if (load.getStatus() == LoadStatus.CANCELLED) {
            throw new ConflictException("This load is cancelled. Pick another load.");
        }

        if (message.getStatus() == InboundMessageStatus.PROCESSED && message.getLoadId() != null) {
            throw new ConflictException("This email's rate con made its own load, so it can't be moved.");
        }

        if (message.getStatus() != InboundMessageStatus.IGNORED && message.getStatus() != InboundMessageStatus.ATTACHED) {
            throw new ConflictException("Clerova is still reading this email. Try again in a minute.");
        }

        message.setLoadId(load.getId());
        message.setStatus(InboundMessageStatus.ATTACHED);
        message.setStatusReason(FILED_BY_HAND_REASON);
        inboundMessageRepository.save(message);

        return detail(load);
    }

    private Load require(UUID organizationId, UUID loadId) {
        return loadRepository.findByIdAndOrganizationId(loadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Load not found"));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

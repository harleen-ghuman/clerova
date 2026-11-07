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
import java.util.Set;
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
    private final TruckingIntakeService intakeService;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final LoadChargeRepository chargeRepository;
    private final InvoiceService invoiceService;

    public LoadService(
            LoadRepository loadRepository,
            LoadReplyRepository loadReplyRepository,
            BrokerRepository brokerRepository,
            InboundMessageRepository inboundMessageRepository,
            OrganizationRepository organizationRepository,
            InboundAddressService inboundAddressService,
            EmailProperties emailProperties,
            EmailSender emailSender,
            LoadDocumentRepository loadDocumentRepository,
            TruckingIntakeService intakeService,
            org.springframework.transaction.support.TransactionTemplate transactionTemplate,
            LoadChargeRepository chargeRepository,
            InvoiceService invoiceService
    ) {
        this.chargeRepository = chargeRepository;
        this.invoiceService = invoiceService;
        this.intakeService = intakeService;
        this.transactionTemplate = transactionTemplate;
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

        if (status != null && status != load.getStatus()) {
            requireStep(load, status);
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

    /**
     * The moves a dispatcher can make by hand: one step forward or back along
     * Booked, In transit, Delivered; cancel before delivery; reopen a
     * cancelled load. Invoiced and Paid follow the invoice, not this.
     */
    static final Map<LoadStatus, Set<LoadStatus>> STEPS = Map.of(
            LoadStatus.BOOKED, Set.of(LoadStatus.IN_TRANSIT, LoadStatus.CANCELLED),
            LoadStatus.IN_TRANSIT, Set.of(LoadStatus.BOOKED, LoadStatus.DELIVERED, LoadStatus.CANCELLED),
            LoadStatus.DELIVERED, Set.of(LoadStatus.IN_TRANSIT),
            LoadStatus.CANCELLED, Set.of(LoadStatus.BOOKED),
            LoadStatus.INVOICED, Set.of(),
            LoadStatus.PAID, Set.of());

    private void requireStep(Load load, LoadStatus to) {
        if (!STEPS.getOrDefault(load.getStatus(), Set.of()).contains(to)) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_STEP",
                    load.getStatus() == LoadStatus.INVOICED || load.getStatus() == LoadStatus.PAID
                            ? "This load is invoiced. Void the invoice first to change it."
                            : "A load goes Booked, In transit, Delivered, one step at a time.");
        }

        if (load.getStatus() == LoadStatus.BOOKED && to == LoadStatus.IN_TRANSIT) {
            requireChecksDone(load);
        }

        if (load.getStatus() == LoadStatus.CANCELLED && to == LoadStatus.BOOKED
                && invoiceService.hasInvoice(load.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_STEP",
                    "This load has an invoice. Void it first to reopen the load.");
        }

        if (load.getStatus() == LoadStatus.DELIVERED && to == LoadStatus.IN_TRANSIT
                && invoiceService.hasInvoice(load.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_STEP",
                    "This load has an invoice. Void it first to move the load back.");
        }
    }

    /** Every rate con check has to be done (sorted or noted) before the truck rolls. */
    static void requireChecksDone(Load load) {
        long open = load.getFlags().stream().filter(flag -> flag.getResolvedAt() == null).count();

        if (open > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CHECKS_OPEN",
                    "Go through the rate con check first: " + open + (open == 1 ? " item is" : " items are")
                            + " still open. Mark each one done, then the load can move to In transit.");
        }
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

        invoiceService.requireEmailAllowance(organizationId);

        InboundMessage answered = reply.getInboundMessageId() == null ? null
                : inboundMessageRepository.findById(reply.getInboundMessageId()).orElse(null);
        Sent sent = sendToBroker(organizationId, load, reply.getToEmail(),
                reply.getSubject() != null ? reply.getSubject() : ReplyService.replySubject("Load " + orEmpty(load.getLoadNumber())),
                text, answered);
        OutgoingEmail email = sent.email();
        String providerId = sent.providerId();

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

    private record Sent(OutgoingEmail email, String providerId) {
    }

    /**
     * Replies to any email in a carrier's Inbox, from the workspace's Clerova
     * address, in the same thread. An email on a load gets the load's reply
     * address and shows under the load's Emails; others come back to the Inbox.
     */
    @Transactional
    public com.clerova.dto.InboundMessageResponse replyToEmail(UUID organizationId, InboundMessage message,
                                                              String to, String body) {
        String address = to == null || to.isBlank() ? message.getFromEmail() : to.strip();

        if (!InvoiceService.isEmail(address)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter the email address to reply to.");
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

        invoiceService.requireEmailAllowance(organizationId);
        String original = message.getSubject() == null ? "" : message.getSubject().strip();
        String subject = clip((original.toLowerCase(java.util.Locale.ROOT).startsWith("re:") ? original : "Re: " + original)
                .replaceAll("[\\r\\n]+", " "), 200);
        Load load = message.getLoadId() == null ? null
                : loadRepository.findByIdAndOrganizationId(message.getLoadId(), organizationId).orElse(null);
        Sent sent = sendToBroker(organizationId, load, address, subject, text, message);

        if (load != null) {
            LoadReply record = new LoadReply();
            record.setLoadId(load.getId());
            record.setToEmail(address);
            record.setSubject(subject);
            record.setKind(ReplyKind.OTHER);
            record.setSummary(clip(subject, 500));
            record.setBody(sent.email().textBody());
            record.setStatus(ReplyStatus.SENT);
            record.setSentAt(Instant.now());
            record.setProviderMessageId(sent.providerId());
            loadReplyRepository.save(record);
        } else {
            invoiceService.noteOtherSend(organizationId);
        }

        inboundMessageRepository.markReplied(message.getId(), Instant.now());
        return com.clerova.dto.InboundMessageResponse.from(inboundMessageRepository.findById(message.getId()).orElseThrow());
    }

    /** Sends an email about a load from the workspace's Clerova address; replies come back to the load. */
    private Sent sendToBroker(UUID organizationId, Load load, String to, String subject, String text,
                              InboundMessage answered) {
        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        String token = inboundAddressService.getOrCreateToken(organization);
        String inReplyTo = answered != null ? answered.getMessageIdHeader() : null;
        String references = answered == null ? null
                : ((answered.getReferences() == null ? "" : answered.getReferences() + " ")
                        + (inReplyTo == null ? "" : inReplyTo)).strip();

        OutgoingEmail email = new OutgoingEmail(
                "\"" + organization.getName().replaceAll("[\"\\r\\n<>]", "").trim() + " via Clerova\" <"
                        + emailProperties.forwardingAddress(token) + ">",
                load != null ? emailProperties.loadReplyAddress(token, load.getId()) : emailProperties.forwardingAddress(token),
                to,
                subject,
                ReplyService.withSignOff(text, organization.getName()),
                inReplyTo,
                references == null || references.isEmpty() ? null : references
        );

        try {
            return new Sent(email, emailSender.send(email));
        } catch (EmailSendException e) {
            log.warn("Couldn't send an email on load {}: {}", load == null ? "(none)" : load.getId(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_NOT_SENT",
                    "The email couldn't be sent. Please try again in a minute.");
        }
    }

    // ------------------------------------------------------------ detention

    /** Logs when the truck got to a stop and left it; null clears a time. */
    @Transactional
    public LoadResponses.LoadDetail setStopTimes(UUID organizationId, UUID loadId, UUID stopId,
                                                 Instant arrivedAt, Instant leftAt) {
        Load load = require(organizationId, loadId);
        LoadStop stop = load.getStops().stream()
                .filter(candidate -> candidate.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Stop not found"));
        Instant latest = Instant.now().plus(java.time.Duration.ofMinutes(10));

        if (leftAt != null && arrivedAt == null) {
            throw invalidTimes("Log when the truck arrived first.");
        }

        if ((arrivedAt != null && arrivedAt.isAfter(latest)) || (leftAt != null && leftAt.isAfter(latest))) {
            throw invalidTimes("That time is in the future.");
        }

        if (arrivedAt != null && arrivedAt.isBefore(load.getCreatedAt().minus(java.time.Duration.ofDays(30)))) {
            throw invalidTimes("That time is long before this load was booked. Check the date.");
        }

        if (leftAt != null && leftAt.isBefore(arrivedAt)) {
            throw invalidTimes("The truck can't leave before it arrived. Check the times.");
        }

        if (leftAt != null && java.time.Duration.between(arrivedAt, leftAt).toHours() > 72) {
            throw invalidTimes("That's more than 3 days at one stop. Check the dates.");
        }

        // No skipped steps: the load is picked up (in transit) before a later stop gets times
        if (arrivedAt != null && stop != load.getStops().getFirst() && load.getStatus() == LoadStatus.BOOKED) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_STEP",
                    "Mark the load picked up first, or log the pickup times.");
        }

        // Leaving the pickup puts the load in transit, so the rate con check comes first
        if (leftAt != null && load.getStatus() == LoadStatus.BOOKED) {
            requireChecksDone(load);
        }

        stop.setArrivedAt(arrivedAt);
        stop.setLeftAt(leftAt);

        // Leaving a pickup means the truck is loaded; leaving the last stop means delivered
        if (leftAt != null && stop.getKind() == StopKind.PICKUP && load.getStatus() == LoadStatus.BOOKED) {
            load.setStatus(LoadStatus.IN_TRANSIT);
        }

        if (leftAt != null && stop == load.getStops().getLast() && stop.getKind() == StopKind.DELIVERY
                && (load.getStatus() == LoadStatus.BOOKED || load.getStatus() == LoadStatus.IN_TRANSIT)) {
            load.setStatus(LoadStatus.DELIVERED);
        }

        loadRepository.save(load);
        syncDetention(load, stop);
        return detail(load);
    }

    /**
     * Keeps the stop's detention charge in step with its times: made once the
     * truck left with time past the free time, updated or removed while it's
     * still waiting to be claimed. A claimed charge is left alone.
     */
    private void syncDetention(Load load, LoadStop stop) {
        LoadCharge charge = chargeRepository.findByLoadIdOrderByCreatedAtAsc(load.getId()).stream()
                .filter(candidate -> candidate.getKind() == ChargeKind.DETENTION && stop.getId().equals(candidate.getStopId()))
                .findFirst()
                .orElse(null);

        // A claimed charge, or one already on a sent invoice, stays as it is
        if ((charge != null && charge.getStatus() != ChargeStatus.TO_CLAIM) || invoiceService.invoiceSent(load.getId())) {
            return;
        }

        Detention.Result detention = stop.getLeftAt() == null ? null : Detention.at(stop, load, Instant.now());

        if (detention == null || detention.billableMinutes() == 0 || detention.amountCents() == null) {
            if (charge != null) {
                chargeRepository.delete(charge);
            }

            return;
        }

        if (charge == null) {
            charge = new LoadCharge();
            charge.setLoadId(load.getId());
            charge.setStopId(stop.getId());
            charge.setKind(ChargeKind.DETENTION);
        }

        charge.setAmountCents(detention.amountCents());
        charge.setDescription(clip(placeName(stop.getPlace()) + ", " + hours(detention.minutesOnSite())
                + " on site, " + hours(detention.freeMinutes()) + " free", 255));
        chargeRepository.save(charge);
    }

    @Transactional
    public LoadResponses.LoadDetail addCharge(UUID organizationId, UUID loadId, ChargeKind kind, String description,
                                              Long amountCents, ChargeStatus status) {
        Load load = require(organizationId, loadId);
        requireChargesOpen(load);

        if (kind == null) {
            throw invalidCharge("Pick what the charge is for.");
        }

        LoadCharge charge = new LoadCharge();
        charge.setLoadId(load.getId());
        charge.setKind(kind);
        charge.setDescription(chargeDescription(description));
        charge.setAmountCents(chargeAmount(amountCents));
        charge.setStatus(status != null ? status : ChargeStatus.TO_CLAIM);
        chargeRepository.save(charge);
        return detail(load);
    }

    @Transactional
    public LoadResponses.LoadDetail updateCharge(UUID organizationId, UUID loadId, UUID chargeId, String description,
                                                 Long amountCents, ChargeStatus status) {
        Load load = require(organizationId, loadId);
        requireChargesOpen(load);
        LoadCharge charge = chargeRepository.findByIdAndLoadId(chargeId, loadId)
                .orElseThrow(() -> new NotFoundException("Charge not found"));

        if (description != null) {
            charge.setDescription(chargeDescription(description));
        }

        if (amountCents != null) {
            charge.setAmountCents(chargeAmount(amountCents));
        }

        if (status != null) {
            if (status == ChargeStatus.CLAIMED && charge.getClaimedAt() == null) {
                charge.setClaimedAt(Instant.now());
            }

            charge.setStatus(status);
        }

        chargeRepository.save(charge);
        return detail(load);
    }

    @Transactional
    public LoadResponses.LoadDetail deleteCharge(UUID organizationId, UUID loadId, UUID chargeId) {
        Load load = require(organizationId, loadId);
        requireChargesOpen(load);
        chargeRepository.findByIdAndLoadId(chargeId, loadId).ifPresent(chargeRepository::delete);
        return detail(load);
    }

    /**
     * Emails the broker from the detention panel: an arrival notice, a
     * "detention started" heads-up, or the claim. A claim marks its charge claimed.
     */
    @Transactional
    public LoadResponses.LoadDetail sendMessage(UUID organizationId, UUID loadId, String to, String subject,
                                                String body, UUID chargeId) {
        return sendMessage(organizationId, loadId, to, subject, body, chargeId, null);
    }

    @Transactional
    public LoadResponses.LoadDetail sendMessage(UUID organizationId, UUID loadId, String to, String subject,
                                                String body, UUID chargeId, UUID stopId) {
        Load load = require(organizationId, loadId);
        String address = to == null || to.isBlank()
                ? (load.getBroker() != null ? load.getBroker().getEmail() : null)
                : to.strip();

        if (!InvoiceService.isEmail(address)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter the broker's email address.");
        }

        String text = body == null ? "" : body.strip();

        if (text.isEmpty()) {
            throw new ConflictException("The message is empty. Add a message before sending.");
        }

        if (text.length() > MAX_REPLY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPLY_TOO_LONG", "The message is too long.");
        }

        String title = subject == null || subject.isBlank()
                ? "Load " + orEmpty(load.getLoadNumber())
                : clip(subject.strip().replaceAll("[\\r\\n]+", " "), 200);
        LoadCharge charge = chargeId == null ? null : chargeRepository.findByIdAndLoadId(chargeId, loadId)
                .orElseThrow(() -> new NotFoundException("Charge not found"));
        // Only a stop of this load can be linked
        UUID stop = stopId == null ? null : load.getStops().stream()
                .filter(candidate -> candidate.getId().equals(stopId))
                .map(LoadStop::getId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Stop not found"));

        if (!emailProperties.isSendingEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_SENDING_DISABLED",
                    "Sending email isn't set up yet. Copy the message and send it from your own email.");
        }

        invoiceService.requireEmailAllowance(organizationId);
        Sent sent = sendToBroker(organizationId, load, address, title, text, null);

        LoadReply record = new LoadReply();
        record.setLoadId(load.getId());
        record.setToEmail(address);
        record.setSubject(title);
        record.setKind(ReplyKind.DETENTION);
        record.setSummary(clip(title, 500));
        record.setBody(sent.email().textBody());
        record.setStatus(ReplyStatus.SENT);
        record.setSentAt(Instant.now());
        record.setProviderMessageId(sent.providerId());
        record.setStopId(stop);
        record.setChargeId(charge == null ? null : charge.getId());
        loadReplyRepository.save(record);

        if (charge != null && charge.getStatus() == ChargeStatus.TO_CLAIM) {
            charge.setStatus(ChargeStatus.CLAIMED);
            charge.setClaimedAt(Instant.now());
            chargeRepository.save(charge);
        }

        return detail(load);
    }

    static final long MAX_CHARGE_CENTS = 10_000_000L;

    private void requireChargesOpen(Load load) {
        if (invoiceService.invoiceSent(load.getId())) {
            throw new ConflictException("The invoice was already sent, so its charges can't change. Void the invoice first.");
        }
    }

    private static long chargeAmount(Long cents) {
        if (cents == null || cents <= 0 || cents > MAX_CHARGE_CENTS) {
            throw invalidCharge("Enter the amount, like 75.00.");
        }

        return cents;
    }

    private static String chargeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String text = description.strip();

        if (text.length() > 255) {
            throw invalidCharge("Keep the note under 255 characters.");
        }

        return text;
    }

    private static ApiException invalidCharge(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHARGE", message);
    }

    private static ApiException invalidTimes(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STOP_TIMES", message);
    }

    /** The facility name from a stop's place: its first line. */
    static String placeName(String place) {
        if (place == null || place.isBlank()) {
            return "the stop";
        }

        String first = place.strip().split("\\R")[0].strip();
        int comma = first.indexOf(',');
        return comma > 3 ? first.substring(0, comma).strip() : first;
    }

    /** 135 minutes as "2h 15m", 120 as "2h". */
    static String hours(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        return h == 0 ? m + "m" : m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
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

        java.util.Map<UUID, List<LoadResponses.SentEmail>> stopEmails = new java.util.HashMap<>();
        java.util.Map<UUID, LoadResponses.SentEmail> claimEmails = new java.util.HashMap<>();

        for (LoadReply reply : loadReplyRepository.findByLoadIdOrderByCreatedAtAsc(load.getId())) {
            if (reply.getStatus() == ReplyStatus.SENT) {
                LoadResponses.SentEmail sent = new LoadResponses.SentEmail(reply.getToEmail(), reply.getSubject(), reply.getSentAt());

                if (reply.getStopId() != null) {
                    stopEmails.computeIfAbsent(reply.getStopId(), id -> new ArrayList<>()).add(sent);
                }

                if (reply.getChargeId() != null) {
                    claimEmails.put(reply.getChargeId(), sent);
                }

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

        List<LoadResponses.ChargeInfo> charges = chargeRepository.findByLoadIdOrderByCreatedAtAsc(load.getId()).stream()
                .map(charge -> LoadResponses.ChargeInfo.of(charge, claimEmails.get(charge.getId())))
                .toList();

        return LoadResponses.detail(load, draft == null ? null : LoadResponses.ReplyInfo.of(draft), conversation,
                documents, charges, invoiceService.forLoad(load), stopEmails);
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
     * Sets a broker's MC by hand (the rate con didn't print it, or printed
     * the wrong one), checks it with FMCSA and redoes the broker checks on
     * the broker's open loads.
     */
    public LoadResponses.BrokerInfo setBrokerMc(UUID organizationId, UUID brokerId, String value) {
        String mcNumber = TruckingIntakeService.mcNumber(value);

        if (mcNumber == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MC_INVALID", "Enter the MC number, like 555609.");
        }

        Broker found = brokerRepository.findById(brokerId)
                .filter(broker -> broker.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> new NotFoundException("Broker not found"));

        // FMCSA can be slow: ask before the transaction
        BrokerCheck check = intakeService.brokerCheck(organizationId, mcNumber);

        return transactionTemplate.execute(status -> {
            Broker broker = brokerRepository.findById(found.getId()).orElseThrow();
            broker.setMcNumber(mcNumber);
            broker.setFmcsaStatus(check == null ? null : check.status());
            broker.setFmcsaName(check == null ? null
                    : check.legalName() != null ? check.legalName() : check.dbaName());
            broker.setFmcsaCheckedAt(check == null ? null : Instant.now());
            brokerRepository.save(broker);

            for (Load load : loadRepository.findForOrganization(organizationId)) {
                if (load.getBroker() == null || !load.getBroker().getId().equals(broker.getId())
                        || !TruckingIntakeService.OPEN.contains(load.getStatus())) {
                    continue;
                }

                List<LoadFlag> kept = new ArrayList<>(load.getFlags().stream()
                        .filter(flag -> !RateConChecks.BROKER_CODES.contains(flag.getCode()))
                        .toList());
                kept.addAll(RateConChecks.brokerFlags(load, mcNumber, check));
                load.replaceFlags(kept);
                loadRepository.save(load);
            }

            return LoadResponses.BrokerInfo.of(broker);
        });
    }

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

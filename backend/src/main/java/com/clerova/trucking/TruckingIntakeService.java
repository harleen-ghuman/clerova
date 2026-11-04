package com.clerova.trucking;

import com.clerova.config.AiRateLimitInterceptor;
import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import com.clerova.domain.Industry;
import com.clerova.repository.InboundAttachmentRepository;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import com.clerova.service.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Emails in a trucking workspace: a broker's rate confirmation becomes a
 * load with its stops, terms and the things to check before signing.
 */
@Service
public class TruckingIntakeService {

    private static final Logger log = LoggerFactory.getLogger(TruckingIntakeService.class);

    public static final String NOT_A_RATE_CON_REASON =
            "Not a rate con. Clerova reads rate cons for now; other trucking emails come next.";
    public static final String SCANNED_REASON =
            "The PDF looks like a scan, so Clerova couldn't read its text. Check this rate con yourself.";
    public static final String REVISED_REASON = "A new copy of this load's rate con. The load was updated.";

    /** FMCSA results are reused for a day. */
    static final Duration FMCSA_CACHE = Duration.ofDays(1);

    public static final String NOT_MATCHED_REASON =
            "Clerova couldn't tell which load this email is about.";

    /** @param draftReply a broker's email on a load, which may need an answer */
    public record Result(InboundMessageStatus status, String reason, UUID loadId, boolean draftReply) {

        Result(InboundMessageStatus status, String reason, UUID loadId) {
            this(status, reason, loadId, false);
        }

        static Result attached(Load load) {
            return new Result(InboundMessageStatus.ATTACHED, null, load.getId(), true);
        }
    }

    private final OrganizationRepository organizationRepository;
    private final InboundAttachmentRepository attachmentRepository;
    private final BrokerRepository brokerRepository;
    private final LoadRepository loadRepository;
    private final TruckingAiService truckingAiService;
    private final FmcsaClient fmcsaClient;
    private final AiUsageService aiUsageService;
    private final TransactionTemplate transactionTemplate;
    private final InboundMessageRepository inboundMessageRepository;
    private final LoadReplyRepository loadReplyRepository;
    private final AiRateLimitInterceptor aiRateLimiter;

    public TruckingIntakeService(
            OrganizationRepository organizationRepository,
            InboundAttachmentRepository attachmentRepository,
            BrokerRepository brokerRepository,
            LoadRepository loadRepository,
            TruckingAiService truckingAiService,
            FmcsaClient fmcsaClient,
            AiUsageService aiUsageService,
            TransactionTemplate transactionTemplate,
            InboundMessageRepository inboundMessageRepository,
            LoadReplyRepository loadReplyRepository,
            AiRateLimitInterceptor aiRateLimiter
    ) {
        this.inboundMessageRepository = inboundMessageRepository;
        this.loadReplyRepository = loadReplyRepository;
        this.aiRateLimiter = aiRateLimiter;
        this.organizationRepository = organizationRepository;
        this.attachmentRepository = attachmentRepository;
        this.brokerRepository = brokerRepository;
        this.loadRepository = loadRepository;
        this.truckingAiService = truckingAiService;
        this.fmcsaClient = fmcsaClient;
        this.aiUsageService = aiUsageService;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean isTruckingWorkspace(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .map(organization -> organization.getIndustry() == Industry.TRUCKING)
                .orElse(false);
    }

    /**
     * Files a trucking email: a rate con becomes (or updates) a load, and
     * any other email is matched to its load, if Clerova can tell which.
     * Throws the quota and plan errors the email processor already handles
     * (the email then waits).
     */
    public Result handle(InboundMessage message) {
        UUID organizationId = message.getOrganizationId();
        Optional<Load> threadLoad = threadLoad(message);
        List<InboundAttachment> pdfs = attachmentRepository.findByInboundMessageId(message.getId()).stream()
                .filter(attachment -> attachment.getExtractedText() != null)
                .toList();

        if (!pdfs.isEmpty()) {
            // The document with the most text is the rate con; the rest are often terms or logos
            InboundAttachment document = pdfs.stream()
                    .max(Comparator.comparingInt(attachment -> attachment.getExtractedText().length()))
                    .orElseThrow();

            if (document.getExtractedText().isBlank()) {
                // A scanned BOL or POD on a known load is still filed under it
                return threadLoad.or(() -> matchLoad(message))
                        .map(Result::attached)
                        .orElse(new Result(InboundMessageStatus.IGNORED, SCANNED_REASON, null));
            }

            aiUsageService.requireQuota(organizationId);
            RateConResult read = truckingAiService.readRateCon(emailText(message), document.getExtractedText());

            if (read.isRateConfirmation) {
                String mcNumber = mcNumber(read.brokerMcNumber);
                BrokerCheck check = cachedCheck(organizationId, mcNumber)
                        .or(() -> mcNumber == null ? Optional.empty() : fmcsaClient.check(mcNumber))
                        .orElse(null);

                return transactionTemplate.execute(status -> save(message, document, read, mcNumber, check));
            }
        }

        return threadLoad.or(() -> matchLoad(message))
                .map(Result::attached)
                .orElse(new Result(InboundMessageStatus.IGNORED, NOT_MATCHED_REASON, null));
    }

    /** A reply to an email about a load: by the load's reply address, or the email headers. */
    Optional<Load> threadLoad(InboundMessage message) {
        UUID organizationId = message.getOrganizationId();
        Optional<UUID> byHash = LoadMailboxHash.parse(message.getMailboxHash());

        if (byHash.isPresent()) {
            return loadRepository.findByIdAndOrganizationId(byHash.get(), organizationId);
        }

        List<String> referenced = new ArrayList<>();

        if (message.getInReplyTo() != null && !message.getInReplyTo().isBlank()) {
            referenced.add(message.getInReplyTo().trim());
        }

        if (message.getReferences() != null) {
            for (String id : message.getReferences().split("\\s+")) {
                if (!id.isBlank()) {
                    referenced.add(id);
                }
            }
        }

        if (referenced.isEmpty()) {
            return Optional.empty();
        }

        return inboundMessageRepository
                .findByOrganizationIdAndMessageIdHeaderInAndLoadIdIsNotNull(organizationId, referenced).stream()
                .map(InboundMessage::getLoadId)
                .distinct()
                .map(id -> loadRepository.findByIdAndOrganizationId(id, organizationId))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * A new email about a load: it names exactly one open load's number, or
     * it's from a broker who has exactly one load in progress.
     */
    Optional<Load> matchLoad(InboundMessage message) {
        List<Load> open = loadRepository.findForOrganization(message.getOrganizationId()).stream()
                .filter(load -> OPEN.contains(load.getStatus()))
                .toList();
        String text = (message.getSubject() == null ? "" : message.getSubject()) + "\n"
                + (message.getTextBody() == null ? "" : message.getTextBody());

        List<Load> named = open.stream()
                .filter(load -> load.getLoadNumber() != null && load.getLoadNumber().length() >= 3
                        && Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(load.getLoadNumber()) + "(?![A-Za-z0-9])",
                        Pattern.CASE_INSENSITIVE).matcher(text).find())
                .toList();

        if (named.size() == 1) {
            return Optional.of(named.getFirst());
        }

        if (!named.isEmpty()) {
            return Optional.empty();
        }

        String sender = message.getFromEmail() == null ? null : message.getFromEmail().trim().toLowerCase(Locale.ROOT);

        if (sender == null || !sender.contains("@")) {
            return Optional.empty();
        }

        String domain = sender.substring(sender.indexOf('@') + 1);
        List<Load> fromBroker = open.stream()
                .filter(load -> load.getBroker() != null && load.getBroker().getEmail() != null)
                .filter(load -> {
                    String brokerEmail = load.getBroker().getEmail().toLowerCase(Locale.ROOT);
                    return brokerEmail.equals(sender)
                            || (!FREE_MAIL.contains(domain) && brokerEmail.endsWith("@" + domain));
                })
                .filter(load -> load.getStatus() != LoadStatus.DELIVERED)
                .toList();

        return fromBroker.size() == 1 ? Optional.of(fromBroker.getFirst()) : Optional.empty();
    }

    /** Loads a broker may still write about. */
    static final Set<LoadStatus> OPEN = Set.of(LoadStatus.BOOKED, LoadStatus.IN_TRANSIT, LoadStatus.DELIVERED, LoadStatus.INVOICED);

    /** Shared mail domains don't identify a broker's company. */
    static final Set<String> FREE_MAIL = Set.of(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "aol.com", "icloud.com", "live.com", "msn.com");

    /** More emails than this on one load within an hour skip the AI (a loop or a flood). */
    static final int BURST_LIMIT = 10;

    /**
     * Drafts the answer to a broker's email on a load, when it needs one.
     * Best effort: replies don't count toward the plan, and a failure just
     * leaves the email for the dispatcher to answer.
     */
    public void draftReply(InboundMessage message) {
        if (message.getLoadId() == null || message.getFromEmail() == null || message.getFromEmail().isBlank()) {
            return;
        }

        UUID loadId = message.getLoadId();

        try {
            aiUsageService.requireAccess(message.getOrganizationId());
        } catch (RuntimeException e) {
            return;
        }

        if (inboundMessageRepository.countByLoadIdAndReceivedAtAfter(loadId, Instant.now().minus(Duration.ofHours(1))) > BURST_LIMIT
                || !aiRateLimiter.tryAcquireBackground()) {
            log.warn("Skipping the AI reply for load {} (busy)", loadId);
            return;
        }

        String[] context = transactionTemplate.execute(status -> {
            Load load = loadRepository.findById(loadId).orElseThrow();
            return new String[] {facts(load), conversation(load, message.getId())};
        });

        BrokerReplyResult result = truckingAiService.draftBrokerReply(context[0], context[1], emailText(message));

        if (!result.needsReply || result.draft == null || result.draft.isBlank()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            loadReplyRepository.dismissDrafts(loadId);

            LoadReply reply = new LoadReply();
            reply.setLoadId(loadId);
            reply.setInboundMessageId(message.getId());
            reply.setToEmail(message.getFromEmail().trim());
            reply.setSubject(clean(com.clerova.email.ReplyService.replySubject(message.getSubject()), 1000));
            reply.setKind(result.kind != null ? result.kind : ReplyKind.OTHER);
            reply.setSummary(clean(result.summary, 500));
            reply.setBody(result.draft.strip());
            loadReplyRepository.save(reply);
        });
    }

    /** What the carrier's records say about the load, for the AI. */
    static String facts(Load load) {
        StringBuilder facts = new StringBuilder();
        facts.append("Load number: ").append(orNone(load.getLoadNumber())).append('\n');
        facts.append("Broker: ").append(load.getBroker() != null ? load.getBroker().getName() : "(unknown)").append('\n');
        facts.append("Status: ").append(switch (load.getStatus()) {
            case BOOKED -> "booked, not picked up yet";
            case IN_TRANSIT -> "picked up, on the way to delivery";
            case DELIVERED -> "delivered";
            case INVOICED -> "delivered and invoiced";
            case PAID -> "paid";
            case CANCELLED -> "cancelled";
        }).append('\n');
        facts.append("Dispatcher's latest update: ").append(orNone(load.getStatusNote())).append('\n');
        facts.append("Equipment: ").append(orNone(load.getEquipment())).append('\n');

        for (LoadStop stop : load.getStops()) {
            facts.append(stop.getKind() == StopKind.PICKUP ? "Pickup: " : "Delivery: ")
                    .append(orNone(stop.getPlace())).append(", appointment ").append(orNone(stop.getAppointment()))
                    .append('\n');
        }

        return facts.toString();
    }

    private String conversation(Load load, UUID exceptMessageId) {
        record Entry(Instant at, String text) {
        }

        List<Entry> entries = new ArrayList<>();

        for (InboundMessage earlier : inboundMessageRepository.findByLoadIdOrderByReceivedAtAsc(load.getId())) {
            if (!earlier.getId().equals(exceptMessageId)) {
                entries.add(new Entry(earlier.getReceivedAt(), "From " + earlier.getFromEmail() + ": "
                        + clean(earlier.getContentText(), 4000)));
            }
        }

        for (LoadReply reply : loadReplyRepository.findByLoadIdOrderByCreatedAtAsc(load.getId())) {
            if (reply.getStatus() == ReplyStatus.SENT) {
                entries.add(new Entry(reply.getSentAt(), "From the carrier: " + clean(reply.getBody(), 4000)));
            }
        }

        entries.sort(Comparator.comparing(Entry::at, Comparator.nullsFirst(Comparator.naturalOrder())));
        StringBuilder text = new StringBuilder();
        entries.forEach(entry -> text.append(entry.text()).append("\n\n"));
        return text.toString();
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "(not known)" : value;
    }

    private Result save(InboundMessage message, InboundAttachment document, RateConResult read,
                        String mcNumber, BrokerCheck check) {
        UUID organizationId = message.getOrganizationId();
        Broker broker = broker(organizationId, read, mcNumber, check);
        String loadNumber = clean(read.loadNumber, 100);

        Optional<Load> existing = broker != null && loadNumber != null
                ? loadRepository.findFirstByOrganizationIdAndBrokerAndLoadNumber(organizationId, broker, loadNumber)
                : Optional.empty();

        Load load = existing.orElseGet(Load::new);
        Map<String, Instant> resolved = new HashMap<>();

        for (LoadFlag flag : load.getFlags()) {
            if (flag.getResolvedAt() != null) {
                resolved.put(flag.getCode(), flag.getResolvedAt());
            }
        }

        load.setOrganizationId(organizationId);
        load.setBroker(broker);
        load.setLoadNumber(loadNumber);
        load.setEquipment(clean(read.equipment, 100));
        load.setRateCents(Money.cents(read.lineHaulRate));
        load.setAgreedRateCents(Money.cents(read.rateInEmail));
        load.setDetentionFreeMinutes(Money.minutesFromHours(read.detentionFreeHours));
        load.setDetentionRateCents(Money.cents(read.detentionRatePerHour));
        load.setDetentionRule(clean(read.detentionCondition, 2000));
        load.setTonuCents(Money.cents(read.tonuAmount));
        load.setPaymentTerms(clean(read.paymentTerms, 2000));
        load.setPaperworkRule(clean(read.paperworkDeadline, 2000));
        load.setRateConFile(clean(document.getFileName(), 500));
        load.replaceStops(stops(read));

        List<LoadFlag> flags = RateConChecks.flags(read, load, mcNumber, check);

        // A flag someone already checked stays checked on a new copy of the rate con
        for (LoadFlag flag : flags) {
            flag.setResolvedAt(resolved.get(flag.getCode()));
        }

        load.replaceFlags(flags);

        if (existing.isEmpty()) {
            // Only a new load counts toward the plan; a revised rate con doesn't
            aiUsageService.consumeRequest(organizationId);
        }

        Load saved = loadRepository.save(load);

        return existing.isPresent()
                ? new Result(InboundMessageStatus.ATTACHED, REVISED_REASON, saved.getId())
                : new Result(InboundMessageStatus.PROCESSED, null, saved.getId());
    }

    private Optional<BrokerCheck> cachedCheck(UUID organizationId, String mcNumber) {
        if (mcNumber == null) {
            return Optional.empty();
        }

        return brokerRepository.findFirstByOrganizationIdAndMcNumber(organizationId, mcNumber)
                .filter(broker -> broker.getFmcsaStatus() != null && broker.getFmcsaCheckedAt() != null
                        && broker.getFmcsaCheckedAt().isAfter(Instant.now().minus(FMCSA_CACHE)))
                .map(broker -> new BrokerCheck(broker.getFmcsaStatus(), broker.getFmcsaName(), null));
    }

    private Broker broker(UUID organizationId, RateConResult read, String mcNumber, BrokerCheck check) {
        String name = clean(read.brokerName, 255);

        if (name == null && mcNumber == null) {
            return null;
        }

        Broker broker = (mcNumber != null
                ? brokerRepository.findFirstByOrganizationIdAndMcNumber(organizationId, mcNumber)
                : brokerRepository.findFirstByOrganizationIdAndMcNumberIsNullAndNameIgnoreCase(organizationId, name))
                .orElseGet(Broker::new);

        broker.setOrganizationId(organizationId);

        if (broker.getName() == null || name != null) {
            broker.setName(name != null ? name : "MC " + mcNumber);
        }

        broker.setMcNumber(mcNumber);

        if (clean(read.brokerEmail, 320) != null) {
            broker.setEmail(clean(read.brokerEmail, 320).toLowerCase(Locale.ROOT));
        }

        if (clean(read.brokerPhone, 50) != null) {
            broker.setPhone(clean(read.brokerPhone, 50));
        }

        if (check != null) {
            broker.setFmcsaStatus(check.status());
            broker.setFmcsaName(clean(check.legalName() != null ? check.legalName() : check.dbaName(), 255));

            if (broker.getFmcsaCheckedAt() == null
                    || broker.getFmcsaCheckedAt().isBefore(Instant.now().minus(FMCSA_CACHE))) {
                broker.setFmcsaCheckedAt(Instant.now());
            }
        }

        return brokerRepository.save(broker);
    }

    private static List<LoadStop> stops(RateConResult read) {
        List<LoadStop> stops = new ArrayList<>();

        if (read.stops == null) {
            return stops;
        }

        for (RateConResult.Stop source : read.stops) {
            if (source == null || (clean(source.place, 5000) == null && clean(source.appointment, 500) == null)) {
                continue;
            }

            LoadStop stop = new LoadStop();
            stop.setPosition(stops.size());
            stop.setKind("PICKUP".equalsIgnoreCase(clean(source.kind, 20)) ? StopKind.PICKUP : StopKind.DELIVERY);
            stop.setPlace(clean(source.place, 5000));
            stop.setAppointment(clean(source.appointment, 500));
            stops.add(stop);
        }

        return stops;
    }

    /** "MC-845210" or "MC# 845210" to "845210"; null when there are no digits. */
    static String mcNumber(String value) {
        if (value == null) {
            return null;
        }

        String digits = value.replaceAll("\\D", "").replaceFirst("^0+(?=\\d)", "");
        return digits.isEmpty() || digits.length() > 8 ? null : digits;
    }

    private static String emailText(InboundMessage message) {
        String subject = message.getSubject() != null ? "Subject: " + message.getSubject() + "\n\n" : "";
        String body = message.getTextBody() != null ? message.getTextBody() : "";
        return subject + body;
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}

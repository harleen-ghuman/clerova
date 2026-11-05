package com.clerova.trucking;

import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
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
            "Clerova couldn't read the attached file. Check it yourself, or upload it to the load.";
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
    private final FileStorage fileStorage;
    private final LoadDocumentRepository loadDocumentRepository;

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
            AiRateLimitInterceptor aiRateLimiter,
            FileStorage fileStorage,
            LoadDocumentRepository loadDocumentRepository
    ) {
        this.loadDocumentRepository = loadDocumentRepository;
        this.fileStorage = fileStorage;
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
        Optional<InboundAttachment> document = mainDocument(attachmentRepository.findByInboundMessageId(message.getId()));

        if (document.isPresent()) {
            InboundAttachment file = document.get();
            String text = file.getExtractedText() == null ? "" : file.getExtractedText();
            List<String> pages = pageImages(file.getStoragePath(), file.getFileName(), file.getContentType());

            if (text.isBlank() && pages.isEmpty()) {
                // Nothing to read (the file wasn't kept); a BOL or POD on a known load is still filed under it
                return threadLoad.or(() -> matchLoad(message))
                        .map(Result::attached)
                        .orElse(new Result(InboundMessageStatus.IGNORED, SCANNED_REASON, null));
            }

            aiUsageService.requireQuota(organizationId);
            RateConResult read = truckingAiService.readRateCon(emailText(message), text, pages);
            DocumentKind kind = read.isRateConfirmation ? DocumentKind.RATE_CON : DocumentKind.parse(read.documentKind);
            file.setDocumentKind(kind.name());
            file.setReferenceNumbers(referencesText(read.referenceNumbers));
            attachmentRepository.save(file);

            if (read.isRateConfirmation) {
                String mcNumber = brokerMc(read);
                BrokerCheck check = brokerCheck(organizationId, mcNumber);

                SavedLoad saved = transactionTemplate.execute(
                        status -> saveLoad(organizationId, file.getFileName(), read, mcNumber, check));

                // A new load, or a new copy that may carry numbers the old one didn't
                fileWaitingPaperwork(organizationId, saved.load().getId());

                return saved.created()
                        ? new Result(InboundMessageStatus.PROCESSED, null, saved.load().getId())
                        : new Result(InboundMessageStatus.ATTACHED, REVISED_REASON, saved.load().getId());
            }

            Optional<Load> byReference = threadLoad.isPresent()
                    ? threadLoad
                    : loadByReference(organizationId, read.referenceNumbers);

            if (byReference.isPresent()) {
                return Result.attached(byReference.get());
            }
        }

        Optional<Load> matched = threadLoad.or(() -> matchLoad(message));

        if (matched.isPresent()) {
            return Result.attached(matched.get());
        }

        // "Your rate confirmation is ready": a link to the broker's site, no file
        if (document.isEmpty() && mentionsRateCon(message)) {
            return new Result(InboundMessageStatus.IGNORED, NO_RATE_CON_ATTACHED_REASON, null);
        }

        return new Result(InboundMessageStatus.IGNORED, NOT_MATCHED_REASON, null);
    }

    public static final String NO_RATE_CON_ATTACHED_REASON =
            "No rate con attached. Download it from the broker's site and upload it on the Loads board.";

    private static final Pattern RATE_CON_WORDS = Pattern.compile(
            "\\brate\\s*con(firmation)?s?\\b|\\bload\\s+(confirmation|tender)\\b", Pattern.CASE_INSENSITIVE);

    static boolean mentionsRateCon(InboundMessage message) {
        return RATE_CON_WORDS.matcher(message.getSubject() == null ? "" : message.getSubject()).find();
    }

    /** Small pictures are logos and signatures, not documents. */
    static final long MIN_PHOTO_BYTES = 50 * 1024;

    /**
     * The email's document: the PDF with the most text (others are often
     * terms or logos), else the biggest PDF (a scan), else the biggest photo.
     */
    static Optional<InboundAttachment> mainDocument(List<InboundAttachment> attachments) {
        List<InboundAttachment> pdfs = attachments.stream()
                .filter(attachment -> PdfText.isPdf(attachment.getFileName(), attachment.getContentType()))
                .toList();
        Comparator<InboundAttachment> bySize = Comparator.comparingLong(
                attachment -> attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes());

        if (!pdfs.isEmpty()) {
            Optional<InboundAttachment> withText = pdfs.stream()
                    .filter(attachment -> attachment.getExtractedText() != null && !attachment.getExtractedText().isBlank())
                    .max(Comparator.comparingInt(attachment -> attachment.getExtractedText().length()));
            return withText.isPresent() ? withText : pdfs.stream().max(bySize);
        }

        return attachments.stream()
                .filter(attachment -> DocumentImages.isImage(attachment.getFileName(), attachment.getContentType()))
                .filter(attachment -> attachment.getSizeBytes() != null && attachment.getSizeBytes() >= MIN_PHOTO_BYTES)
                .max(bySize);
    }

    /** The kept file as pictures for the AI; empty when it wasn't kept or can't be read. */
    List<String> pageImages(String storagePath, String fileName, String contentType) {
        if (storagePath == null || !fileStorage.isEnabled()) {
            return List.of();
        }

        try {
            return DocumentImages.dataUrls(fileStorage.get(storagePath), fileName, contentType);
        } catch (FileStorageException e) {
            log.warn("Could not fetch {} to read it: {}", fileName, e.getMessage());
            return List.of();
        }
    }

    /**
     * The one load whose number is printed on a document (a BOL or POD
     * usually carries the broker's load number). Empty when none or several.
     */
    public Optional<Load> loadByReference(UUID organizationId, List<String> references) {
        List<String> wanted = references == null ? List.of() : references.stream()
                .map(TruckingIntakeService::reference)
                .filter(reference -> reference.length() >= 4)
                .toList();

        if (wanted.isEmpty()) {
            return Optional.empty();
        }

        List<Load> matches = loadRepository.findForOrganization(organizationId).stream()
                .filter(load -> load.getStatus() != LoadStatus.CANCELLED)
                .filter(load -> loadReferences(load).stream()
                        .anyMatch(number -> wanted.stream().anyMatch(reference -> sameReference(number, reference))))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    /** The load number and the other numbers on its rate con (PO, pickup...). */
    static List<String> loadReferences(Load load) {
        List<String> numbers = new ArrayList<>();

        if (load.getLoadNumber() != null && reference(load.getLoadNumber()).length() >= 4) {
            numbers.add(reference(load.getLoadNumber()));
        }

        if (load.getReferenceNumbers() != null) {
            for (String number : load.getReferenceNumbers().split(" ")) {
                // Other numbers must be longer: short ones repeat across loads
                if (number.length() >= 5 && !numbers.contains(number)) {
                    numbers.add(number);
                }
            }
        }

        return numbers;
    }

    /** Numbers as stored: normalized, distinct, space-separated, or null when none. */
    static String referencesText(List<String> references) {
        if (references == null) {
            return null;
        }

        StringBuilder text = new StringBuilder();

        references.stream()
                .map(TruckingIntakeService::reference)
                .filter(reference -> reference.length() >= 4 && reference.length() <= 40)
                .distinct()
                .forEach(reference -> {
                    if (text.length() + reference.length() + 1 <= 1000) {
                        text.append(text.isEmpty() ? "" : " ").append(reference);
                    }
                });

        return text.isEmpty() ? null : text.toString();
    }

    static List<String> splitReferences(String text) {
        return text == null || text.isBlank() ? List.of() : List.of(text.split(" "));
    }

    public static final String PAPERWORK_FILED_REASON = "Filed under its load when the rate con came in.";

    /**
     * Paperwork that came before its rate con (a BOL forwarded first) waits
     * as "couldn't tell which load". When the load is made, file any whose
     * numbers point to it, and only to it. Best effort.
     */
    public void fileWaitingPaperwork(UUID organizationId, UUID loadId) {
        try {
            Instant since = Instant.now().minus(Duration.ofDays(30));

            for (InboundMessage waiting : inboundMessageRepository.findByOrganizationIdAndStatusAndStatusReasonAndReceivedAtAfter(
                    organizationId, InboundMessageStatus.IGNORED, NOT_MATCHED_REASON, since)) {
                List<String> numbers = attachmentRepository.findByInboundMessageId(waiting.getId()).stream()
                        .flatMap(attachment -> splitReferences(attachment.getReferenceNumbers()).stream())
                        .toList();

                if (loadByReference(organizationId, numbers).map(Load::getId).filter(loadId::equals).isPresent()) {
                    waiting.setLoadId(loadId);
                    waiting.setStatus(InboundMessageStatus.ATTACHED);
                    waiting.setStatusReason(PAPERWORK_FILED_REASON);
                    inboundMessageRepository.save(waiting);
                }
            }

            for (LoadDocument document : loadDocumentRepository.findByOrganizationIdAndLoadIdIsNullAndCreatedAtAfter(organizationId, since)) {
                if (loadByReference(organizationId, splitReferences(document.getReferenceNumbers()))
                        .map(Load::getId).filter(loadId::equals).isPresent()) {
                    document.setLoadId(loadId);
                    loadDocumentRepository.save(document);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not file waiting paperwork for load {}: {}", loadId, e.toString());
        }
    }

    /** A broker with no MC, by name: "Acme Freight, Inc." is the same as "Acme Freight". */
    private Optional<Broker> brokerWithoutMc(UUID organizationId, String name) {
        String wanted = RateConChecks.normalize(name);

        if (wanted.isEmpty()) {
            return Optional.empty();
        }

        return brokerRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
                .filter(broker -> broker.getMcNumber() == null && RateConChecks.normalize(broker.getName()).equals(wanted))
                .findFirst();
    }

    /**
     * The same load filed under another record of the same broker, as when
     * a new copy of the rate con is read with a corrected MC number.
     */
    private Optional<Load> sameLoadOtherBrokerRecord(UUID organizationId, Broker broker, String loadNumber) {
        // "CrossCountry Freight Solutions, Inc." and "CrossCountry Freight Solutions" are one broker
        String name = RateConChecks.normalize(broker.getName());

        if (name.isEmpty()) {
            return Optional.empty();
        }

        return loadRepository.findForOrganization(organizationId).stream()
                .filter(load -> loadNumber.equalsIgnoreCase(load.getLoadNumber()))
                .filter(load -> load.getBroker() != null
                        && RateConChecks.normalize(load.getBroker().getName()).equals(name))
                .findFirst();
    }

    /** The broker's MC, unless it's really the carrier's own (printed in the carrier section). */
    static String brokerMc(RateConResult read) {
        String broker = mcNumber(read.brokerMcNumber);
        String carrier = mcNumber(read.carrierMcNumber);
        return broker != null && broker.equals(carrier) ? null : broker;
    }

    /**
     * "#3466426-1" and "3466426" are the same load, and so is "Load 88213"
     * for 88213 (a label in front of the number). A short number must match
     * exactly.
     */
    static boolean sameReference(String loadNumber, String reference) {
        return sameNumber(loadNumber, reference)
                || sameNumber(withoutLabel(loadNumber), withoutLabel(reference));
    }

    /** "LOAD88213" to "88213"; "LX5521" stays, since it has no digits after a word alone. */
    private static String withoutLabel(String reference) {
        return reference.replaceFirst("^(LOAD|ORDER|PO|BOL|PU|PICKUP|REF|CONF|CONFIRMATION|NO|NUMBER|SHIPMENT|PRO)+(?=\\d)", "");
    }

    private static boolean sameNumber(String loadNumber, String reference) {
        if (loadNumber.length() < 4 || reference.length() < 4) {
            return false;
        }

        if (loadNumber.equals(reference)) {
            return true;
        }

        String shorter = loadNumber.length() < reference.length() ? loadNumber : reference;
        String longer = shorter == loadNumber ? reference : loadNumber;
        return shorter.length() >= 6 && longer.startsWith(shorter) && longer.length() - shorter.length() <= 2;
    }

    static String reference(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
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

        // "Ely to Hanford": the email names one open load's pickup and delivery towns
        // (stops load lazily, so read them inside a transaction)
        List<UUID> byLane = transactionTemplate.execute(status ->
                loadRepository.findForOrganization(message.getOrganizationId()).stream()
                        .filter(load -> OPEN.contains(load.getStatus()))
                        .filter(load -> namesLane(text, load))
                        .map(Load::getId)
                        .toList());

        if (byLane != null && byLane.size() == 1) {
            return open.stream().filter(load -> load.getId().equals(byLane.getFirst())).findFirst();
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

    /** From the broker's own address, or someone else at the broker's company (not a free mail domain). */
    static boolean isFromBroker(InboundMessage message, Load load) {
        if (load.getBroker() == null || load.getBroker().getEmail() == null || message.getFromEmail() == null) {
            return false;
        }

        String sender = message.getFromEmail().trim().toLowerCase(Locale.ROOT);
        String broker = load.getBroker().getEmail().trim().toLowerCase(Locale.ROOT);

        if (sender.equals(broker)) {
            return true;
        }

        int at = sender.indexOf('@');
        String domain = at < 0 ? "" : sender.substring(at + 1);
        return !domain.isEmpty() && !FREE_MAIL.contains(domain) && broker.endsWith("@" + domain);
    }

    private static final Pattern TOWN = Pattern.compile("([A-Za-z][A-Za-z .'-]*?)\\s*,\\s*([A-Z]{2})\\b");

    /** "Silver Pine Ranch, HC 33 Box 3389-4, Ely, NV 89301" to "Ely". */
    static String town(String place) {
        if (place == null) {
            return null;
        }

        java.util.regex.Matcher matcher = TOWN.matcher(place);
        String town = null;

        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            int comma = candidate.lastIndexOf(',');
            town = comma >= 0 ? candidate.substring(comma + 1).trim() : candidate;
        }

        return town == null || town.length() < 3 ? null : town;
    }

    /** The text names both the first pickup's town and the last delivery's town, as whole words. */
    static boolean namesLane(String text, Load load) {
        List<LoadStop> stops = load.getStops();

        if (stops.size() < 2 || text == null) {
            return false;
        }

        String from = town(stops.getFirst().getPlace());
        String to = town(stops.getLast().getPlace());

        if (from == null || to == null || from.equalsIgnoreCase(to)) {
            return false;
        }

        return mentions(text, from) && mentions(text, to);
    }

    private static boolean mentions(String text, String word) {
        return Pattern.compile("(?<![A-Za-z])" + Pattern.quote(word) + "(?![A-Za-z])", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
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

        // Only the broker gets a drafted answer: not the dispatcher forwarding paperwork
        // from their own inbox, a driver, or anyone else on the load
        boolean fromBroker = transactionTemplate.execute(status -> loadRepository.findById(loadId)
                .map(load -> isFromBroker(message, load))
                .orElse(false));

        if (!fromBroker && threadLoad(message).isEmpty()) {
            return;
        }

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

    /** A load made or updated from a rate con; created is false for a new copy of a known load. */
    public record SavedLoad(Load load, boolean created) {
    }

    /**
     * Reads the FMCSA status for a broker, reusing a recent check. Call it
     * outside a transaction: it may wait on FMCSA.
     */
    public BrokerCheck brokerCheck(UUID organizationId, String mcNumber) {
        return cachedCheck(organizationId, mcNumber)
                .or(() -> mcNumber == null ? Optional.empty() : fmcsaClient.check(mcNumber))
                .orElse(null);
    }

    /** Makes the load from a rate con, or updates it for a new copy. Run in a transaction. */
    public SavedLoad saveLoad(UUID organizationId, String fileName, RateConResult read,
                              String mcNumber, BrokerCheck check) {
        Broker broker = broker(organizationId, read, mcNumber, check);
        String loadNumber = clean(read.loadNumber, 100);

        Optional<Load> existing = broker != null && loadNumber != null
                ? loadRepository.findFirstByOrganizationIdAndBrokerAndLoadNumber(organizationId, broker, loadNumber)
                        .or(() -> sameLoadOtherBrokerRecord(organizationId, broker, loadNumber))
                : Optional.empty();

        Load load = existing.orElseGet(Load::new);
        Broker previousBroker = load.getBroker();
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
        load.setDetentionMaxCents(Money.cents(read.detentionMaxAmount));
        load.setQuickPayPercent(Money.percent(read.quickPayFeePercent));
        String invoiceEmail = clean(read.invoiceEmail, 320);
        load.setInvoiceEmail(invoiceEmail != null && InvoiceService.isEmail(invoiceEmail) ? invoiceEmail : null);
        load.setPaymentTerms(clean(read.paymentTerms, 2000));
        load.setPaperworkRule(clean(read.paperworkDeadline, 2000));
        load.setRateConFile(clean(fileName, 500));
        List<String> numbers = new ArrayList<>();
        numbers.add(loadNumber == null ? "" : loadNumber);
        if (read.referenceNumbers != null) {
            numbers.addAll(read.referenceNumbers);
        }
        load.setReferenceNumbers(referencesText(numbers));
        List<LoadStop> newStops = stops(read);
        List<LoadStop> current = load.getStops();
        boolean sameStops = current.size() == newStops.size() && !current.isEmpty();

        for (int i = 0; sameStops && i < current.size(); i++) {
            sameStops = current.get(i).getKind() == newStops.get(i).getKind();
        }

        if (sameStops) {
            // A new copy of the rate con keeps each stop, with the times and charges already logged there
            for (int i = 0; i < current.size(); i++) {
                current.get(i).setPlace(newStops.get(i).getPlace());
                current.get(i).setAppointment(newStops.get(i).getAppointment());
            }
        } else {
            load.replaceStops(newStops);
        }

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

        // A corrected MC can move the load to a new broker record; drop the old one if it's now empty
        if (previousBroker != null && (broker == null || !previousBroker.getId().equals(broker.getId()))
                && loadRepository.countByBroker(previousBroker) == 0) {
            brokerRepository.delete(previousBroker);
        }

        return new SavedLoad(saved, existing.isEmpty());
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
                : brokerWithoutMc(organizationId, name))
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

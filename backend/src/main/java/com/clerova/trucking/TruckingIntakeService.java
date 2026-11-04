package com.clerova.trucking;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.InboundMessageStatus;
import com.clerova.domain.Industry;
import com.clerova.repository.InboundAttachmentRepository;
import com.clerova.repository.OrganizationRepository;
import com.clerova.service.AiUsageService;
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
import java.util.UUID;

/**
 * Emails in a trucking workspace: a broker's rate confirmation becomes a
 * load with its stops, terms and the things to check before signing.
 */
@Service
public class TruckingIntakeService {

    public static final String NOT_A_RATE_CON_REASON =
            "Not a rate con. Clerova reads rate cons for now; other trucking emails come next.";
    public static final String SCANNED_REASON =
            "The PDF looks like a scan, so Clerova couldn't read its text. Check this rate con yourself.";
    public static final String REVISED_REASON = "A new copy of this load's rate con. The load was updated.";

    /** FMCSA results are reused for a day. */
    static final Duration FMCSA_CACHE = Duration.ofDays(1);

    public record Result(InboundMessageStatus status, String reason, UUID loadId) {
    }

    private final OrganizationRepository organizationRepository;
    private final InboundAttachmentRepository attachmentRepository;
    private final BrokerRepository brokerRepository;
    private final LoadRepository loadRepository;
    private final TruckingAiService truckingAiService;
    private final FmcsaClient fmcsaClient;
    private final AiUsageService aiUsageService;
    private final TransactionTemplate transactionTemplate;

    public TruckingIntakeService(
            OrganizationRepository organizationRepository,
            InboundAttachmentRepository attachmentRepository,
            BrokerRepository brokerRepository,
            LoadRepository loadRepository,
            TruckingAiService truckingAiService,
            FmcsaClient fmcsaClient,
            AiUsageService aiUsageService,
            TransactionTemplate transactionTemplate
    ) {
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
     * Reads the email's rate con, if it has one. Throws the quota and plan
     * errors the email processor already handles (the email then waits).
     */
    public Result handle(InboundMessage message) {
        List<InboundAttachment> pdfs = attachmentRepository.findByInboundMessageId(message.getId()).stream()
                .filter(attachment -> attachment.getExtractedText() != null)
                .toList();

        if (pdfs.isEmpty()) {
            return new Result(InboundMessageStatus.IGNORED, NOT_A_RATE_CON_REASON, null);
        }

        // The document with the most text is the rate con; the rest are often terms or logos
        InboundAttachment document = pdfs.stream()
                .max(Comparator.comparingInt(attachment -> attachment.getExtractedText().length()))
                .orElseThrow();

        if (document.getExtractedText().isBlank()) {
            return new Result(InboundMessageStatus.IGNORED, SCANNED_REASON, null);
        }

        UUID organizationId = message.getOrganizationId();
        aiUsageService.requireQuota(organizationId);

        RateConResult read = truckingAiService.readRateCon(emailText(message), document.getExtractedText());

        if (!read.isRateConfirmation) {
            return new Result(InboundMessageStatus.IGNORED, NOT_A_RATE_CON_REASON, null);
        }

        String mcNumber = mcNumber(read.brokerMcNumber);
        BrokerCheck check = cachedCheck(organizationId, mcNumber)
                .or(() -> mcNumber == null ? Optional.empty() : fmcsaClient.check(mcNumber))
                .orElse(null);

        return transactionTemplate.execute(status -> save(message, document, read, mcNumber, check));
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

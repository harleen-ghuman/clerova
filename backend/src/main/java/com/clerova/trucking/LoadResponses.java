package com.clerova.trucking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What the app gets for loads. Amounts are in cents. */
public final class LoadResponses {

    private LoadResponses() {
    }

    public record BrokerInfo(
            UUID id, String name, String mcNumber, String email, String phone,
            FmcsaStatus fmcsaStatus, String fmcsaName, Instant fmcsaCheckedAt
    ) {
        static BrokerInfo of(Broker broker) {
            return broker == null ? null : new BrokerInfo(
                    broker.getId(), broker.getName(), broker.getMcNumber(), broker.getEmail(), broker.getPhone(),
                    broker.getFmcsaStatus(), broker.getFmcsaName(), broker.getFmcsaCheckedAt());
        }
    }

    public record StopInfo(
            UUID id, StopKind kind, String place, String appointment, Instant arrivedAt, Instant leftAt,
            DetentionInfo detention
    ) {
    }

    /** Detention so far at a stop (or in all, once the truck left). */
    public record DetentionInfo(
            long minutesOnSite, int freeMinutes, long billableMinutes, Long amountCents, Instant freeEndsAt
    ) {
    }

    public record ChargeInfo(
            UUID id, ChargeKind kind, String label, String description, long amountCents, ChargeStatus status,
            UUID stopId, Instant claimedAt
    ) {
        static ChargeInfo of(LoadCharge charge) {
            return new ChargeInfo(charge.getId(), charge.getKind(), charge.getKind().label(), charge.getDescription(),
                    charge.getAmountCents(), charge.getStatus(), charge.getStopId(), charge.getClaimedAt());
        }
    }

    public record InvoiceLineInfo(String description, long amountCents) {
    }

    /** A document that goes in the invoice packet. */
    public record PacketDocument(String name, DocumentKind kind) {
    }

    public record InvoiceInfo(
            UUID id, UUID loadId, String number, InvoiceStatus status, Invoice.SendTo sendTo, String billTo,
            String remitTo, List<InvoiceLineInfo> lines, long totalCents, boolean quickPay, Long quickPayFeeCents,
            Invoice.SentVia sentVia, String sentEmail, Instant sentAt, java.time.LocalDate dueDate,
            Instant paidAt, Long paidCents, Instant createdAt,
            String loadNumber, String brokerName, String origin, String destination,
            /** Filled in for one invoice: what the packet holds and what it's missing. */
            List<PacketDocument> packet, List<String> missingPaperwork, String suggestedEmail,
            boolean factoring
    ) {
    }

    public record FlagInfo(
            UUID id, String code, FlagLevel level, String title, String detail, String sourceText, Instant resolvedAt
    ) {
    }

    public record LoadSummary(
            UUID id, String loadNumber, LoadStatus status, String brokerName,
            String origin, String destination, String pickupAppointment, String deliveryAppointment,
            Long rateCents, int openFlags, int fixFlags, int draftReplies, String statusNote,
            Instant createdAt, Instant updatedAt
    ) {
    }

    /** One email on a load, in or out, oldest first in the conversation. */
    public record ConversationEntry(
            UUID id, boolean incoming, String from, String to, String subject, String body,
            Instant at, List<FileInfo> files
    ) {
    }

    public record FileInfo(UUID id, String name, String contentType, Long sizeBytes, boolean stored) {
    }

    /**
     * A document on a load: a file from an email filed under it, or one
     * uploaded by hand. kind is null when Clerova didn't read the file.
     */
    public record DocumentInfo(
            UUID id, String source, DocumentKind kind, String name, String contentType, Long sizeBytes,
            boolean stored, Instant at
    ) {
        public static final String EMAIL = "EMAIL";
        public static final String UPLOAD = "UPLOAD";

        public static DocumentInfo of(LoadDocument document) {
            return new DocumentInfo(document.getId(), UPLOAD, document.getKind(), document.getFileName(),
                    document.getContentType(), document.getSizeBytes(), true, document.getCreatedAt());
        }
    }

    /** An uploaded document, filed or not. */
    public record UploadedDocument(
            UUID id, UUID loadId, DocumentKind kind, String name, String contentType,
            long sizeBytes, Instant at, boolean newLoad
    ) {
    }

    public record ReplyInfo(
            UUID id, String toEmail, String subject, ReplyKind kind, String summary, String body,
            ReplyStatus status, Instant createdAt, Instant sentAt
    ) {
        static ReplyInfo of(LoadReply reply) {
            return new ReplyInfo(reply.getId(), reply.getToEmail(), reply.getSubject(), reply.getKind(),
                    reply.getSummary(), reply.getBody(), reply.getStatus(), reply.getCreatedAt(), reply.getSentAt());
        }
    }

    public record BrokerSummary(BrokerInfo broker, int loads, int openLoads) {
    }

    public record LoadDetail(
            UUID id, String loadNumber, LoadStatus status, BrokerInfo broker, String equipment,
            Long rateCents, Long agreedRateCents, Integer detentionFreeMinutes, Long detentionRateCents,
            String detentionRule, Long tonuCents, String paymentTerms, String paperworkRule, String rateConFile,
            Long detentionMaxCents, java.math.BigDecimal quickPayPercent, String invoiceEmail,
            List<StopInfo> stops, List<FlagInfo> flags, String statusNote, ReplyInfo draft,
            List<ConversationEntry> conversation, List<DocumentInfo> documents,
            List<ChargeInfo> charges, InvoiceInfo invoice, Instant createdAt, Instant updatedAt
    ) {
    }

    static LoadSummary summary(Load load, int draftReplies) {
        List<LoadStop> stops = load.getStops();

        return new LoadSummary(
                load.getId(),
                load.getLoadNumber(),
                load.getStatus(),
                load.getBroker() != null ? load.getBroker().getName() : null,
                stops.isEmpty() ? null : stops.getFirst().getPlace(),
                stops.size() < 2 ? null : stops.getLast().getPlace(),
                stops.isEmpty() ? null : stops.getFirst().getAppointment(),
                stops.size() < 2 ? null : stops.getLast().getAppointment(),
                load.getRateCents(),
                (int) load.getFlags().stream().filter(flag -> flag.getResolvedAt() == null).count(),
                (int) load.getFlags().stream()
                        .filter(flag -> flag.getResolvedAt() == null && flag.getLevel() == FlagLevel.FIX).count(),
                draftReplies,
                load.getStatusNote(),
                load.getCreatedAt(),
                load.getUpdatedAt()
        );
    }

    static LoadDetail detail(Load load, ReplyInfo draft, List<ConversationEntry> conversation,
                             List<DocumentInfo> documents, List<ChargeInfo> charges, InvoiceInfo invoice) {
        Instant now = Instant.now();

        return new LoadDetail(
                load.getId(), load.getLoadNumber(), load.getStatus(), BrokerInfo.of(load.getBroker()),
                load.getEquipment(), load.getRateCents(), load.getAgreedRateCents(),
                load.getDetentionFreeMinutes(), load.getDetentionRateCents(), load.getDetentionRule(),
                load.getTonuCents(), load.getPaymentTerms(), load.getPaperworkRule(), load.getRateConFile(),
                load.getDetentionMaxCents(), load.getQuickPayPercent(), load.getInvoiceEmail(),
                load.getStops().stream().map(s -> {
                    Detention.Result detention = Detention.at(s, load, now);
                    return new StopInfo(s.getId(), s.getKind(), s.getPlace(), s.getAppointment(),
                            s.getArrivedAt(), s.getLeftAt(), detention == null ? null : new DetentionInfo(
                                    detention.minutesOnSite(), detention.freeMinutes(), detention.billableMinutes(),
                                    detention.amountCents(), detention.freeEndsAt()));
                }).toList(),
                load.getFlags().stream().map(f -> new FlagInfo(
                        f.getId(), f.getCode(), f.getLevel(), f.getTitle(), f.getDetail(), f.getSourceText(),
                        f.getResolvedAt())).toList(),
                load.getStatusNote(), draft, conversation, documents, charges, invoice,
                load.getCreatedAt(), load.getUpdatedAt()
        );
    }
}

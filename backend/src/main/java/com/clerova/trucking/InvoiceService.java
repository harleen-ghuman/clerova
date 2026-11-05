package com.clerova.trucking;

import com.clerova.domain.InboundAttachment;
import com.clerova.domain.InboundMessage;
import com.clerova.domain.Organization;
import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSendException;
import com.clerova.email.EmailSender;
import com.clerova.email.OutgoingEmail;
import com.clerova.exception.ApiException;
import com.clerova.exception.ConflictException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import com.clerova.email.InboundAddressService;
import com.clerova.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Invoices for loads: made from the rate and approved charges, drawn as a
 * PDF, joined with the paperwork into one packet, and sent to the factor or
 * the broker (or downloaded for a factor's website), then marked paid.
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    /** Dates on invoices; Clerova's carriers are in the US, and the first ones in California. */
    static final ZoneId ZONE = ZoneId.of("America/Los_Angeles");

    static final int MAX_LINES = 20;
    static final int MAX_MESSAGE = 5_000;
    static final long MAX_LINE_CENTS = 10_000_000L;

    /** Email services refuse messages over 10 MB; base64 makes files about a third bigger. */
    static final int MAX_EMAILED_PACKET_BYTES = 7 * 1024 * 1024;

    /** Paperwork read into memory for one packet. */
    static final long MAX_PACKET_SOURCE_BYTES = 40L * 1024 * 1024;

    /** In packet order: what factors and brokers ask for. */
    static final List<DocumentKind> PACKET_KINDS = List.of(
            DocumentKind.RATE_CON, DocumentKind.BOL, DocumentKind.POD, DocumentKind.LUMPER_RECEIPT,
            DocumentKind.SCALE_TICKET);

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");
    private static final Pattern NET_DAYS = Pattern.compile("(?i)\\bnet\\s*-?\\s*(\\d{1,3})\\b|\\b(\\d{1,3})\\s*(?:calendar\\s+|business\\s+)?days?\\b");

    private final InvoiceRepository invoiceRepository;
    private final CarrierProfileRepository carrierRepository;
    private final LoadRepository loadRepository;
    private final LoadChargeRepository chargeRepository;
    private final LoadDocumentRepository documentRepository;
    private final LoadReplyRepository replyRepository;
    private final InboundMessageRepository messageRepository;
    private final OrganizationRepository organizationRepository;
    private final InboundAddressService inboundAddressService;
    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final FileStorage fileStorage;

    public InvoiceService(
            InvoiceRepository invoiceRepository,
            CarrierProfileRepository carrierRepository,
            LoadRepository loadRepository,
            LoadChargeRepository chargeRepository,
            LoadDocumentRepository documentRepository,
            LoadReplyRepository replyRepository,
            InboundMessageRepository messageRepository,
            OrganizationRepository organizationRepository,
            InboundAddressService inboundAddressService,
            EmailProperties emailProperties,
            EmailSender emailSender,
            FileStorage fileStorage
    ) {
        this.invoiceRepository = invoiceRepository;
        this.carrierRepository = carrierRepository;
        this.loadRepository = loadRepository;
        this.chargeRepository = chargeRepository;
        this.documentRepository = documentRepository;
        this.replyRepository = replyRepository;
        this.messageRepository = messageRepository;
        this.organizationRepository = organizationRepository;
        this.inboundAddressService = inboundAddressService;
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
        this.fileStorage = fileStorage;
    }

    public record LineRequest(String description, Long amountCents) {
    }

    /** rebuild makes the lines again from the rate and approved charges. */
    public record UpdateRequest(List<LineRequest> lines, Boolean quickPay, Invoice.SendTo sendTo, Boolean rebuild) {
    }

    /** A file to download. */
    public record FileDownload(String fileName, byte[] content) {
    }

    // ---------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public List<LoadResponses.InvoiceInfo> list(UUID organizationId) {
        Map<UUID, Load> loads = loadRepository.findForOrganization(organizationId).stream()
                .collect(Collectors.toMap(Load::getId, Function.identity()));

        return invoiceRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(invoice -> invoice.getStatus() != InvoiceStatus.VOID)
                .map(invoice -> info(invoice, loads.get(invoice.getLoadId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadResponses.InvoiceInfo get(UUID organizationId, UUID invoiceId) {
        Invoice invoice = require(organizationId, invoiceId);
        return info(invoice, loadRepository.findById(invoice.getLoadId()).orElseThrow(), true);
    }

    /** The load's current invoice (not a voided one), with its packet, or null. */
    @Transactional(readOnly = true)
    public LoadResponses.InvoiceInfo forLoad(Load load) {
        return invoiceRepository.findByLoadIdOrderByCreatedAtDesc(load.getId()).stream()
                .filter(invoice -> invoice.getStatus() != InvoiceStatus.VOID)
                .findFirst()
                .map(invoice -> info(invoice, load, true))
                .orElse(null);
    }

    // --------------------------------------------------------------- creating

    /** Makes the load's invoice from its rate and approved charges; a second tap returns the same one. */
    @Transactional
    public LoadResponses.InvoiceInfo create(UUID organizationId, UUID loadId) {
        Load load = loadRepository.findByIdAndOrganizationId(loadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Load not found"));

        if (load.getStatus() == LoadStatus.CANCELLED) {
            throw new ConflictException("This load is cancelled, so it can't be invoiced.");
        }

        LoadResponses.InvoiceInfo existing = forLoad(load);

        if (existing != null) {
            return existing;
        }

        CarrierProfile carrier = carrierRepository.findById(organizationId).orElseGet(() -> CarrierService.blank(organizationId));
        List<String> missing = CarrierService.missing(carrier);

        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CARRIER_DETAILS_MISSING",
                    "Add " + joinWords(missing) + " in Settings first. They go on every invoice.");
        }

        Invoice invoice = new Invoice();
        invoice.setOrganizationId(organizationId);
        invoice.setLoadId(load.getId());
        invoice.setNumber(nextNumber(organizationId, carrier));
        invoice.setSendTo(carrier.isUsesFactoring() ? Invoice.SendTo.FACTOR : Invoice.SendTo.BROKER);
        invoice.setBillTo(billTo(load));
        invoice.setRemitTo(remitTo(carrier, invoice.getSendTo()));

        invoice.replaceLines(startingLines(load));
        carrierRepository.save(carrier);
        return info(invoiceRepository.save(invoice), load, true);
    }

    /** Changes a draft: its lines, quick pay, or who it goes to. */
    @Transactional
    public LoadResponses.InvoiceInfo update(UUID organizationId, UUID invoiceId, UpdateRequest request) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();
        requireDraft(invoice);

        if (Boolean.TRUE.equals(request.rebuild())) {
            invoice.replaceLines(startingLines(load));
        } else if (request.lines() != null) {
            if (request.lines().isEmpty() || request.lines().size() > MAX_LINES) {
                throw invalid("An invoice needs 1 to " + MAX_LINES + " lines.");
            }

            List<InvoiceLine> lines = new ArrayList<>();

            for (LineRequest line : request.lines()) {
                String description = line == null || line.description() == null ? "" : line.description().strip();

                if (description.isEmpty() || description.length() > 255) {
                    throw invalid("Give each line a short description.");
                }

                if (line.amountCents() == null || line.amountCents() < 0 || line.amountCents() > MAX_LINE_CENTS) {
                    throw invalid("Enter an amount for \"" + description + "\".");
                }

                lines.add(new InvoiceLine(description, line.amountCents()));
            }

            invoice.replaceLines(lines);
        }

        if (request.sendTo() != null && request.sendTo() != invoice.getSendTo()) {
            CarrierProfile carrier = carrierRepository.findById(organizationId).orElseGet(() -> CarrierService.blank(organizationId));

            if (request.sendTo() == Invoice.SendTo.FACTOR && !carrier.isUsesFactoring()) {
                throw new ConflictException("Turn on factoring in Settings first.");
            }

            invoice.setSendTo(request.sendTo());
            invoice.setRemitTo(remitTo(carrier, request.sendTo()));
        }

        if (request.quickPay() != null) {
            invoice.setQuickPay(request.quickPay());
        }

        if (invoice.isQuickPay() && (invoice.getSendTo() != Invoice.SendTo.BROKER || load.getQuickPayPercent() == null)) {
            invoice.setQuickPay(false);
        }

        invoice.setQuickPayFeeCents(invoice.isQuickPay() ? quickPayFee(invoice.getTotalCents(), load.getQuickPayPercent()) : null);
        return info(invoiceRepository.save(invoice), load, true);
    }

    // ----------------------------------------------------------------- files

    @Transactional(readOnly = true)
    public FileDownload pdf(UUID organizationId, UUID invoiceId) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();
        return new FileDownload("Invoice " + fileSafe(invoice.getNumber()) + ".pdf", invoicePdf(invoice, load));
    }

    @Transactional(readOnly = true)
    public FileDownload packet(UUID organizationId, UUID invoiceId) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();
        return new FileDownload("Invoice " + fileSafe(invoice.getNumber()) + " packet.pdf",
                packetFor(invoice, load).pdf());
    }

    // --------------------------------------------------------------- sending

    /** Emails the packet to the factor or broker, as the dispatcher approved it. */
    @Transactional
    public LoadResponses.InvoiceInfo send(UUID organizationId, UUID invoiceId, String to, String message) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();

        if (invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("This invoice is " + invoice.getStatus().name().toLowerCase(Locale.ROOT)
                    + ", so it can't be sent.");
        }

        String address = to == null ? "" : to.strip();

        if (!isEmail(address)) {
            throw invalid("Enter the email address to send it to.");
        }

        String text = message == null ? "" : message.strip();

        if (text.isEmpty() || text.length() > MAX_MESSAGE) {
            throw invalid("Write a short message to go with the invoice.");
        }

        if (!emailProperties.isSendingEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_SENDING_DISABLED",
                    "Sending email isn't set up yet. Download the packet and send it from your own email.");
        }

        InvoicePdf.Packet packet = packetFor(invoice, load);

        if (packet.pdf().length > MAX_EMAILED_PACKET_BYTES) {
            throw new ApiException(HttpStatus.CONFLICT, "PACKET_TOO_BIG",
                    "The packet is too big to email (over 7 MB). Download it and upload it to the factor's website instead.");
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        CarrierProfile carrier = carrierRepository.findById(organizationId).orElseGet(() -> CarrierService.blank(organizationId));
        String token = inboundAddressService.getOrCreateToken(organization);
        String sender = carrier.getLegalName() != null ? carrier.getLegalName() : organization.getName();
        String subject = "Invoice " + invoice.getNumber()
                + (load.getLoadNumber() != null ? " - Load " + load.getLoadNumber() : "")
                + " - " + sender;

        OutgoingEmail email = new OutgoingEmail(
                "\"" + sender.replaceAll("[\"\\r\\n<>]", "").trim() + " via Clerova\" <"
                        + emailProperties.forwardingAddress(token) + ">",
                carrier.getEmail() != null ? carrier.getEmail() : emailProperties.loadReplyAddress(token, load.getId()),
                address,
                subject,
                text,
                null,
                null,
                null,
                List.of(new OutgoingEmail.Attachment("Invoice " + fileSafe(invoice.getNumber()) + " packet.pdf",
                        "application/pdf", packet.pdf()))
        );

        String providerId;

        try {
            providerId = emailSender.send(email);
        } catch (EmailSendException e) {
            log.warn("Couldn't send invoice {}: {}", invoice.getId(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_NOT_SENT",
                    e.isRejected() ? e.getMessage() : "The email couldn't be sent. Please try again in a minute.");
        }

        LoadReply record = new LoadReply();
        record.setLoadId(load.getId());
        record.setToEmail(address);
        record.setSubject(subject);
        record.setKind(ReplyKind.INVOICE);
        record.setSummary("Invoice " + invoice.getNumber() + " sent");
        record.setBody(text + "\n\n[Attached: Invoice " + invoice.getNumber() + " packet.pdf]");
        record.setStatus(ReplyStatus.SENT);
        record.setSentAt(Instant.now());
        record.setProviderMessageId(providerId);
        replyRepository.save(record);

        markSent(invoice, load, Invoice.SentVia.EMAIL, address);
        return info(invoice, load, true);
    }

    /** The dispatcher uploaded the packet to the factor's website (or sent it some other way). */
    @Transactional
    public LoadResponses.InvoiceInfo markSent(UUID organizationId, UUID invoiceId) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();

        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            markSent(invoice, load, Invoice.SentVia.PORTAL, null);
        } else if (invoice.getStatus() != InvoiceStatus.SENT) {
            throw new ConflictException("This invoice is already " + invoice.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }

        return info(invoice, load, true);
    }

    @Transactional
    public LoadResponses.InvoiceInfo markPaid(UUID organizationId, UUID invoiceId, Long paidCents) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return info(invoice, load, true);
        }

        if (invoice.getStatus() != InvoiceStatus.SENT) {
            throw new ConflictException(invoice.getStatus() == InvoiceStatus.DRAFT
                    ? "Send the invoice before marking it paid."
                    : "This invoice was voided.");
        }

        long expected = invoice.getTotalCents() - (invoice.getQuickPayFeeCents() == null ? 0 : invoice.getQuickPayFeeCents());

        if (paidCents != null && (paidCents < 0 || paidCents > MAX_LINE_CENTS * MAX_LINES)) {
            throw invalid("Enter the amount you were paid.");
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        invoice.setPaidCents(paidCents != null ? paidCents : expected);
        load.setStatus(LoadStatus.PAID);
        loadRepository.save(load);
        return info(invoiceRepository.save(invoice), load, true);
    }

    /** Undoes "paid", for a tap by mistake. */
    @Transactional
    public LoadResponses.InvoiceInfo markUnpaid(UUID organizationId, UUID invoiceId) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setPaidAt(null);
            invoice.setPaidCents(null);

            if (load.getStatus() == LoadStatus.PAID) {
                load.setStatus(LoadStatus.INVOICED);
                loadRepository.save(load);
            }
        }

        return info(invoiceRepository.save(invoice), load, true);
    }

    /** Cancels the invoice; its number isn't reused. The load can be invoiced again. */
    @Transactional
    public LoadResponses.InvoiceInfo voidInvoice(UUID organizationId, UUID invoiceId) {
        Invoice invoice = require(organizationId, invoiceId);
        Load load = loadRepository.findById(invoice.getLoadId()).orElseThrow();

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ConflictException("This invoice is paid. Mark it unpaid first if that was a mistake.");
        }

        invoice.setStatus(InvoiceStatus.VOID);

        if (load.getStatus() == LoadStatus.INVOICED) {
            load.setStatus(LoadStatus.DELIVERED);
            loadRepository.save(load);
        }

        return info(invoiceRepository.save(invoice), load, false);
    }

    // ---------------------------------------------------------------- export

    /** One month of invoices (by the date sent) as a spreadsheet file, for the bookkeeper. */
    @Transactional(readOnly = true)
    public FileDownload export(UUID organizationId, String month) {
        YearMonth period;

        try {
            period = YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw invalid("Pick a month.");
        }

        Map<UUID, Load> loads = loadRepository.findForOrganization(organizationId).stream()
                .collect(Collectors.toMap(Load::getId, Function.identity()));
        StringBuilder csv = new StringBuilder(
                "Invoice,Invoice date,Load,Broker,From,To,Sent to,Status,Lines,Total,Quick pay fee,Due date,Paid date,Amount paid\r\n");
        long total = 0;
        long paid = 0;

        List<Invoice> invoices = invoiceRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).reversed();

        for (Invoice invoice : invoices) {
            if (invoice.getStatus() == InvoiceStatus.VOID || invoice.getStatus() == InvoiceStatus.DRAFT
                    || invoice.getSentAt() == null) {
                continue;
            }

            LocalDate date = LocalDate.ofInstant(invoice.getSentAt(), ZONE);

            if (!YearMonth.from(date).equals(period)) {
                continue;
            }

            Load load = loads.get(invoice.getLoadId());
            List<LoadStop> stops = load == null ? List.of() : load.getStops();
            String lines = invoice.getLines().stream()
                    .map(line -> line.getDescription() + " " + Money.dollars(line.getAmountCents()))
                    .collect(Collectors.joining("; "));

            row(csv,
                    invoice.getNumber(),
                    date.toString(),
                    load == null ? "" : orEmpty(load.getLoadNumber()),
                    load == null || load.getBroker() == null ? "" : load.getBroker().getName(),
                    stops.isEmpty() ? "" : town(stops.getFirst().getPlace()),
                    stops.size() < 2 ? "" : town(stops.getLast().getPlace()),
                    invoice.getSendTo() == Invoice.SendTo.FACTOR ? "Factor" : "Broker",
                    invoice.getStatus() == InvoiceStatus.PAID ? "Paid" : "Waiting on payment",
                    lines,
                    amount(invoice.getTotalCents()),
                    invoice.getQuickPayFeeCents() == null ? "" : amount(invoice.getQuickPayFeeCents()),
                    invoice.getDueDate() == null ? "" : invoice.getDueDate().toString(),
                    invoice.getPaidAt() == null ? "" : LocalDate.ofInstant(invoice.getPaidAt(), ZONE).toString(),
                    invoice.getPaidCents() == null ? "" : amount(invoice.getPaidCents()));
            total += invoice.getTotalCents();
            paid += invoice.getPaidCents() == null ? 0 : invoice.getPaidCents();
        }

        row(csv, "Total", "", "", "", "", "", "", "", "", amount(total), "", "", "", amount(paid));
        return new FileDownload("Clerova invoices " + period + ".csv",
                ("﻿" + csv).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // --------------------------------------------------------------- helpers

    private void markSent(Invoice invoice, Load load, Invoice.SentVia via, String email) {
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            invoice.setSentAt(Instant.now());
            invoice.setDueDate(dueDate(invoice, load, LocalDate.now(ZONE)));
        }

        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setSentVia(via);
        invoice.setSentEmail(email);
        invoiceRepository.save(invoice);

        if (load.getStatus() != LoadStatus.PAID && load.getStatus() != LoadStatus.INVOICED) {
            load.setStatus(LoadStatus.INVOICED);
            loadRepository.save(load);
        }
    }

    /** When the broker should pay: from the terms when billed to the broker; a factor pays on its own schedule. */
    static LocalDate dueDate(Invoice invoice, Load load, LocalDate sent) {
        if (invoice.getSendTo() != Invoice.SendTo.BROKER || invoice.isQuickPay()) {
            return null;
        }

        Integer days = netDays(load.getPaymentTerms());
        return days == null ? null : sent.plusDays(days);
    }

    /** "Net 30" or "paid in 30 days" to 30; null when the terms don't say. */
    static Integer netDays(String terms) {
        if (terms == null) {
            return null;
        }

        Matcher matcher = NET_DAYS.matcher(terms);

        if (!matcher.find()) {
            return null;
        }

        int days = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        return days >= 1 && days <= 120 ? days : null;
    }

    static long quickPayFee(long totalCents, BigDecimal percent) {
        return BigDecimal.valueOf(totalCents).multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
    }

    private String nextNumber(UUID organizationId, CarrierProfile carrier) {
        for (int attempt = 0; attempt < 1000; attempt++) {
            String number = carrier.takeInvoiceNumber();

            if (!invoiceRepository.existsByOrganizationIdAndNumber(organizationId, number)) {
                return number;
            }
        }

        throw new ConflictException("Change the next invoice number in Settings: the next ones are taken.");
    }

    private static String billTo(Load load) {
        List<String> lines = new ArrayList<>();

        if (load.getBroker() != null) {
            lines.add(load.getBroker().getName());

            if (load.getBroker().getMcNumber() != null) {
                lines.add("MC " + load.getBroker().getMcNumber());
            }
        }

        String email = load.getInvoiceEmail() != null ? load.getInvoiceEmail()
                : load.getBroker() != null ? load.getBroker().getEmail() : null;

        if (email != null) {
            lines.add(email);
        }

        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static String remitTo(CarrierProfile carrier, Invoice.SendTo sendTo) {
        return sendTo == Invoice.SendTo.FACTOR
                ? carrier.getFactorName() + (carrier.getFactorAddress() != null ? "\n" + carrier.getFactorAddress() : "")
                : carrier.getLegalName() + (carrier.getAddress() != null ? "\n" + carrier.getAddress() : "");
    }

    /** The line haul, then each approved charge. */
    private List<InvoiceLine> startingLines(Load load) {
        List<InvoiceLine> lines = new ArrayList<>();
        lines.add(new InvoiceLine("Line haul" + (load.getLoadNumber() != null ? ", load " + load.getLoadNumber() : ""),
                load.getRateCents() != null ? load.getRateCents() : 0));

        for (LoadCharge charge : chargeRepository.findByLoadIdOrderByCreatedAtAsc(load.getId())) {
            if (charge.getStatus() == ChargeStatus.APPROVED) {
                lines.add(new InvoiceLine(chargeLine(charge), charge.getAmountCents()));
            }
        }

        return lines;
    }

    /** True once the load's invoice went out: its charges are then fixed. */
    @Transactional(readOnly = true)
    public boolean invoiceSent(UUID loadId) {
        return invoiceRepository.findByLoadIdOrderByCreatedAtDesc(loadId).stream()
                .anyMatch(invoice -> invoice.getStatus() == InvoiceStatus.SENT || invoice.getStatus() == InvoiceStatus.PAID);
    }

    static String chargeLine(LoadCharge charge) {
        return charge.getDescription() != null && !charge.getDescription().isBlank()
                ? charge.getKind().label() + ": " + charge.getDescription()
                : charge.getKind().label();
    }

    private byte[] invoicePdf(Invoice invoice, Load load) {
        CarrierProfile carrier = carrierRepository.findById(invoice.getOrganizationId())
                .orElseGet(() -> CarrierService.blank(invoice.getOrganizationId()));
        List<String[]> stops = new ArrayList<>();

        for (LoadStop stop : load.getStops()) {
            String when = stop.getLeftAt() != null
                    ? (stop.getKind() == StopKind.PICKUP ? "Picked up " : "Delivered ") + InvoicePdf.safe(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
                            .format(LocalDate.ofInstant(stop.getLeftAt(), ZONE)))
                    : stop.getAppointment();
            stops.add(new String[] {
                    stop.getKind() == StopKind.PICKUP ? "Pickup" : "Delivery",
                    orEmpty(stop.getPlace()) + (when != null ? "\n" + when : "")
            });
        }

        LocalDate date = invoice.getSentAt() != null ? LocalDate.ofInstant(invoice.getSentAt(), ZONE) : LocalDate.now(ZONE);
        LocalDate due = invoice.getDueDate() != null ? invoice.getDueDate() : dueDate(invoice, load, date);
        String references = load.getReferenceNumbers() == null ? null
                : java.util.Arrays.stream(load.getReferenceNumbers().split("\\s+"))
                        .filter(reference -> !reference.isBlank()
                                && !reference.equalsIgnoreCase(orEmpty(load.getLoadNumber()).replaceAll("[^A-Za-z0-9]", "")))
                        .limit(8)
                        .collect(Collectors.joining(", "));

        return InvoicePdf.invoice(new InvoicePdf.Content(
                invoice.getNumber(), date, due, load.getPaymentTerms(),
                carrier.getLegalName(), carrier.getAddress(), carrier.getPhone(), carrier.getEmail(),
                carrier.getMcNumber(), carrier.getDotNumber(),
                invoice.getBillTo(), invoice.getRemitTo(),
                invoice.getSendTo() == Invoice.SendTo.FACTOR ? carrier.getFactorName() : null,
                load.getLoadNumber(), references == null || references.isBlank() ? null : references, stops,
                invoice.getLines(), invoice.getTotalCents(), invoice.getQuickPayFeeCents(),
                load.getQuickPayPercent() == null ? null : load.getQuickPayPercent().stripTrailingZeros().toPlainString()));
    }

    /** A document of the load that can go in a packet. */
    private record Paper(String name, String contentType, long size, DocumentKind kind, String storagePath,
                         Instant at) {
    }

    /** The load's paperwork for the packet, in packet order, each file once. */
    private List<Paper> papers(Load load) {
        List<Paper> papers = new ArrayList<>();

        for (InboundMessage message : messageRepository.findByLoadIdOrderByReceivedAtAsc(load.getId())) {
            for (InboundAttachment attachment : message.getAttachments()) {
                if (!LoadService.isDocument(attachment) || attachment.getStoragePath() == null) {
                    continue;
                }

                DocumentKind kind = attachment.getDocumentKind() != null
                        ? DocumentKind.parse(attachment.getDocumentKind())
                        : attachment.getFileName() != null && attachment.getFileName().equals(load.getRateConFile())
                                ? DocumentKind.RATE_CON : null;
                papers.add(new Paper(attachment.getFileName(), attachment.getContentType(),
                        attachment.getSizeBytes() == null ? 0 : attachment.getSizeBytes(), kind,
                        attachment.getStoragePath(), message.getReceivedAt()));
            }
        }

        for (LoadDocument document : documentRepository.findByLoadIdOrderByCreatedAtAsc(load.getId())) {
            papers.add(new Paper(document.getFileName(), document.getContentType(), document.getSizeBytes(),
                    document.getKind(), document.getStoragePath(), document.getCreatedAt()));
        }

        Set<String> seen = new HashSet<>();
        List<Paper> kept = new ArrayList<>();

        for (DocumentKind kind : PACKET_KINDS) {
            for (Paper paper : papers) {
                if (paper.kind() == kind && seen.add(paper.name() + "|" + paper.size() + "|" + kind)) {
                    kept.add(paper);
                }
            }

            // Only the latest rate con: a revised one replaces the first
            if (kind == DocumentKind.RATE_CON && kept.size() > 1) {
                Paper latest = kept.getLast();
                kept.clear();
                kept.add(latest);
            }
        }

        return kept;
    }

    private InvoicePdf.Packet packetFor(Invoice invoice, Load load) {
        List<InvoicePdf.PacketFile> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        long read = 0;

        for (Paper paper : papers(load)) {
            if (read + paper.size() > MAX_PACKET_SOURCE_BYTES) {
                skipped.add(paper.name());
                continue;
            }

            try {
                byte[] content = fileStorage.get(paper.storagePath());
                read += content.length;
                files.add(new InvoicePdf.PacketFile(orEmpty(paper.name()), paper.contentType(), content));
            } catch (RuntimeException e) {
                log.warn("Couldn't read {} for invoice {}: {}", paper.name(), invoice.getId(), e.getMessage());
                skipped.add(paper.name());
            }
        }

        InvoicePdf.Packet packet = InvoicePdf.packet(invoicePdf(invoice, load), files);

        if (!skipped.isEmpty() || !packet.skipped().isEmpty()) {
            log.info("Invoice {} packet left out {} file(s)", invoice.getId(), skipped.size() + packet.skipped().size());
        }

        return packet;
    }

    private LoadResponses.InvoiceInfo info(Invoice invoice, Load load, boolean withPacket) {
        List<LoadResponses.PacketDocument> packet = null;
        List<String> missing = null;
        String suggested = null;
        CarrierProfile carrier = carrierRepository.findById(invoice.getOrganizationId()).orElse(null);

        if (withPacket && load != null) {
            List<Paper> papers = papers(load);
            packet = papers.stream().map(paper -> new LoadResponses.PacketDocument(paper.name(), paper.kind())).toList();
            Set<DocumentKind> kinds = papers.stream().map(Paper::kind)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentKind.class)));
            missing = new ArrayList<>();

            if (!kinds.contains(DocumentKind.RATE_CON)) {
                missing.add("rate con");
            }

            if (!kinds.contains(DocumentKind.BOL) && !kinds.contains(DocumentKind.POD)) {
                missing.add("signed BOL or POD");
            }

            boolean lumper = chargeRepository.findByLoadIdOrderByCreatedAtAsc(load.getId()).stream()
                    .anyMatch(charge -> charge.getKind() == ChargeKind.LUMPER && charge.getStatus() == ChargeStatus.APPROVED);

            if (lumper && !kinds.contains(DocumentKind.LUMPER_RECEIPT)) {
                missing.add("lumper receipt");
            }

            if (invoice.getSendTo() == Invoice.SendTo.FACTOR) {
                suggested = carrier == null ? null : carrier.getFactorEmail();
            } else {
                suggested = load.getInvoiceEmail() != null ? load.getInvoiceEmail()
                        : load.getBroker() != null ? load.getBroker().getEmail() : null;
            }
        }

        List<LoadStop> stops = load == null ? List.of() : load.getStops();

        return new LoadResponses.InvoiceInfo(
                invoice.getId(), invoice.getLoadId(), invoice.getNumber(), invoice.getStatus(), invoice.getSendTo(),
                invoice.getBillTo(), invoice.getRemitTo(),
                invoice.getLines().stream()
                        .map(line -> new LoadResponses.InvoiceLineInfo(line.getDescription(), line.getAmountCents()))
                        .toList(),
                invoice.getTotalCents(), invoice.isQuickPay(), invoice.getQuickPayFeeCents(), invoice.getSentVia(),
                invoice.getSentEmail(), invoice.getSentAt(), invoice.getDueDate(), invoice.getPaidAt(),
                invoice.getPaidCents(), invoice.getCreatedAt(),
                load == null ? null : load.getLoadNumber(),
                load == null || load.getBroker() == null ? null : load.getBroker().getName(),
                stops.isEmpty() ? null : stops.getFirst().getPlace(),
                stops.size() < 2 ? null : stops.getLast().getPlace(),
                packet, missing, suggested, carrier != null && carrier.isUsesFactoring());
    }

    private Invoice require(UUID organizationId, UUID invoiceId) {
        return invoiceRepository.findByIdAndOrganizationId(invoiceId, organizationId)
                .orElseThrow(() -> new NotFoundException("Invoice not found"));
    }

    private static void requireDraft(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new ConflictException("This invoice was already sent, so it can't be changed. Void it and make a new one.");
        }
    }

    static boolean isEmail(String value) {
        return value != null && value.length() <= 320 && EMAIL.matcher(value).matches();
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVOICE", message);
    }

    private static String joinWords(List<String> words) {
        if (words.size() == 1) {
            return words.getFirst();
        }

        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.getLast();
    }

    private static String fileSafe(String value) {
        return value.replaceAll("[^A-Za-z0-9 ._-]", "-");
    }

    private static String amount(long cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }

    /** The town and state from a stop's address, like "Phoenix, AZ", or the first line. */
    private static String town(String place) {
        if (place == null) {
            return "";
        }

        Matcher matcher = Pattern.compile("([A-Za-z .'-]+),\\s*([A-Z]{2})\\b").matcher(place);
        String last = null;

        while (matcher.find()) {
            last = matcher.group(1).strip().replaceAll("^.*\\d\\s*", "") + ", " + matcher.group(2);
        }

        return last != null ? last : place.split("\n")[0];
    }

    private static void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                csv.append(',');
            }

            String cell = cells[i] == null ? "" : cells[i].replace("\r", " ").replace("\n", " ");

            // A cell that starts like a formula is read as text by spreadsheets
            if (!cell.isEmpty() && "=+-@".indexOf(cell.charAt(0)) >= 0) {
                cell = "'" + cell;
            }

            csv.append('"').append(cell.replace("\"", "\"\"")).append('"');
        }

        csv.append("\r\n");
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}

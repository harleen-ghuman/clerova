package com.clerova.trucking;

import com.clerova.domain.Organization;
import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSendException;
import com.clerova.email.EmailSender;
import com.clerova.email.InboundAddressService;
import com.clerova.email.OutgoingEmail;
import com.clerova.exception.ApiException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.OrganizationRepository;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The carrier packet: the carrier's own paperwork (authority, insurance,
 * W-9, notice of assignment...) kept once, then sent to any broker as one
 * PDF with a cover page listing the company's details.
 */
@Service
public class CarrierPacketService {

    private static final Logger log = LoggerFactory.getLogger(CarrierPacketService.class);

    static final int MAX_DOCUMENTS = 20;
    static final int MAX_UPLOADS_PER_HOUR = 30;

    /** One document, as the app shows it. */
    public record DocumentInfo(UUID id, CarrierDocument.Kind kind, String kindLabel, String name, String contentType,
                               long sizeBytes, LocalDate expiresOn, boolean expired, Instant at) {
    }

    /** The file and its name and type, for viewing or downloading. */
    public record File(byte[] content, String fileName, String contentType) {
    }

    private final CarrierDocumentRepository documentRepository;
    private final CarrierProfileRepository carrierRepository;
    private final OrganizationRepository organizationRepository;
    private final FileStorage fileStorage;
    private final TruckingIntakeService intakeService;
    private final InboundAddressService inboundAddressService;
    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final InvoiceService invoiceService;

    public CarrierPacketService(CarrierDocumentRepository documentRepository, CarrierProfileRepository carrierRepository,
                                OrganizationRepository organizationRepository, FileStorage fileStorage,
                                TruckingIntakeService intakeService, InboundAddressService inboundAddressService,
                                EmailProperties emailProperties, EmailSender emailSender, InvoiceService invoiceService) {
        this.documentRepository = documentRepository;
        this.carrierRepository = carrierRepository;
        this.organizationRepository = organizationRepository;
        this.fileStorage = fileStorage;
        this.intakeService = intakeService;
        this.inboundAddressService = inboundAddressService;
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
        this.invoiceService = invoiceService;
    }

    public List<DocumentInfo> list(UUID organizationId) {
        requireTrucking(organizationId);
        return ordered(organizationId).stream().map(CarrierPacketService::info).toList();
    }

    /** Keeps a document for the packet; PDFs and photos up to 10 MB. */
    public DocumentInfo upload(UUID organizationId, UUID userId, CarrierDocument.Kind kind, LocalDate expiresOn,
                               LoadDocumentService.Upload upload) {
        requireTrucking(organizationId);
        String fileName = LoadDocumentService.cleanName(upload.fileName());
        String contentType = LoadDocumentService.contentType(fileName, upload.contentType());

        if (upload.content() == null || upload.content().length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "That file is empty. Pick another one.");
        }

        if (upload.content().length > LoadDocumentService.MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_BIG", "Files can be up to 10 MB.");
        }

        if (contentType == null || !LoadDocumentService.looksLike(contentType, upload.content())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE", "Upload a PDF or a photo (JPG, PNG or WebP).");
        }

        if (!fileStorage.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_OFF", "Uploading files isn't turned on yet.");
        }

        if (documentRepository.countByOrganizationId(organizationId) >= MAX_DOCUMENTS) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_DOCUMENTS",
                    "The carrier packet holds up to " + MAX_DOCUMENTS + " documents. Remove an old one first.");
        }

        if (documentRepository.countByOrganizationIdAndCreatedAtAfter(organizationId, Instant.now().minus(Duration.ofHours(1)))
                >= MAX_UPLOADS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_UPLOADS",
                    "That's a lot of uploads in one hour. Please try again a little later.");
        }

        String path = organizationId + "/carrier/" + UUID.randomUUID() + "/" + LoadDocumentService.safeFileName(fileName);

        try {
            fileStorage.put(path, upload.content(), contentType);
        } catch (FileStorageException e) {
            log.warn("Could not keep carrier document {}: {}", fileName, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FILE_NOT_SAVED",
                    "Couldn't save the file right now. Please try again in a minute.");
        }

        CarrierDocument document = new CarrierDocument();
        document.setOrganizationId(organizationId);
        document.setKind(kind == null ? CarrierDocument.Kind.OTHER : kind);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setSizeBytes(upload.content().length);
        document.setStoragePath(path);
        document.setExpiresOn(expiresOn);
        document.setUploadedBy(userId);
        return info(documentRepository.save(document));
    }

    /** Changes what a document is, or when it expires (null clears the date). */
    public DocumentInfo update(UUID organizationId, UUID id, CarrierDocument.Kind kind, LocalDate expiresOn, boolean clearExpiry) {
        CarrierDocument document = require(organizationId, id);

        if (kind != null) {
            document.setKind(kind);
        }

        if (expiresOn != null || clearExpiry) {
            document.setExpiresOn(expiresOn);
        }

        return info(documentRepository.save(document));
    }

    public void delete(UUID organizationId, UUID id) {
        CarrierDocument document = require(organizationId, id);
        documentRepository.delete(document);

        try {
            fileStorage.delete(document.getStoragePath());
        } catch (FileStorageException e) {
            log.warn("Could not delete the file of carrier document {}: {}", id, e.getMessage());
        }
    }

    public File file(UUID organizationId, UUID id) {
        CarrierDocument document = require(organizationId, id);
        return new File(read(document), document.getFileName(), document.getContentType());
    }

    /** The whole packet as one PDF: a cover page with the company's details, then each document. */
    public File packet(UUID organizationId) {
        requireTrucking(organizationId);
        List<CarrierDocument> documents = ordered(organizationId);

        if (documents.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "PACKET_EMPTY",
                    "Add your documents first, like your W-9, MC authority and insurance certificate.");
        }

        CarrierProfile carrier = carrierRepository.findById(organizationId).orElseGet(() -> CarrierService.blank(organizationId));
        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        String company = carrier.getLegalName() != null ? carrier.getLegalName() : organization.getName();
        List<InvoicePdf.PacketFile> files = new ArrayList<>();

        for (CarrierDocument document : documents) {
            files.add(new InvoicePdf.PacketFile(document.getFileName(), document.getContentType(), read(document)));
        }

        InvoicePdf.Packet packet = InvoicePdf.packet(cover(company, carrier, documents), files);
        return new File(packet.pdf(), "Carrier packet - " + InvoiceService.fileSafe(company) + ".pdf", "application/pdf");
    }

    /** Emails the packet to a broker from the workspace's Clerova address. */
    public void send(UUID organizationId, String to, String message) {
        String address = to == null ? "" : to.strip();

        if (!InvoiceService.isEmail(address)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "Enter the broker's email address.");
        }

        if (!emailProperties.isSendingEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_SENDING_DISABLED",
                    "Sending email isn't set up yet. Download the packet and attach it to your own email.");
        }

        invoiceService.requireEmailAllowance(organizationId);
        File packet = packet(organizationId);

        if (packet.content().length > InvoiceService.MAX_EMAILED_PACKET_BYTES) {
            throw new ApiException(HttpStatus.CONFLICT, "PACKET_TOO_BIG",
                    "The packet is too big to email (over 7 MB). Download it and upload it to the broker's website instead.");
        }

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        CarrierProfile carrier = carrierRepository.findById(organizationId).orElseGet(() -> CarrierService.blank(organizationId));
        String sender = carrier.getLegalName() != null ? carrier.getLegalName() : organization.getName();
        String token = inboundAddressService.getOrCreateToken(organization);
        String text = message == null || message.isBlank()
                ? "Hi,\n\nAttached is our carrier packet. Let us know if you need anything else to set us up.\n\nThanks,"
                : message.strip();

        if (text.length() > 5000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG", "The message is too long.");
        }

        OutgoingEmail email = new OutgoingEmail(
                "\"" + sender.replaceAll("[\"\\r\\n<>]", "").trim() + " via Clerova\" <" + emailProperties.forwardingAddress(token) + ">",
                carrier.getEmail() != null ? carrier.getEmail() : emailProperties.forwardingAddress(token),
                address,
                "Carrier packet - " + sender + (carrier.getMcNumber() != null ? " (MC " + carrier.getMcNumber() + ")" : ""),
                com.clerova.email.ReplyService.withSignOff(text, sender),
                null,
                null,
                null,
                List.of(new OutgoingEmail.Attachment(packet.fileName(), "application/pdf", packet.content())));

        try {
            emailSender.send(email);
        } catch (EmailSendException e) {
            log.warn("Couldn't send the carrier packet for {}: {}", organizationId, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_NOT_SENT",
                    e.isRejected() ? e.getMessage() : "The email couldn't be sent. Please try again in a minute.");
        }
    }

    // --------------------------------------------------------------- helpers

    private void requireTrucking(UUID organizationId) {
        if (!intakeService.isTruckingWorkspace(organizationId)) {
            throw new NotFoundException("The carrier packet is for trucking workspaces");
        }
    }

    private CarrierDocument require(UUID organizationId, UUID id) {
        requireTrucking(organizationId);
        return documentRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    /** In the order brokers expect: authority, insurance, W-9, notice of assignment, then the rest. */
    private List<CarrierDocument> ordered(UUID organizationId) {
        return documentRepository.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .sorted(Comparator.comparing(CarrierDocument::getKind))
                .toList();
    }

    private byte[] read(CarrierDocument document) {
        try {
            return fileStorage.get(document.getStoragePath());
        } catch (FileStorageException e) {
            log.warn("Could not read carrier document {}: {}", document.getId(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FILE_UNAVAILABLE",
                    "Couldn't open a document right now. Please try again in a minute.");
        }
    }

    private static DocumentInfo info(CarrierDocument document) {
        return new DocumentInfo(document.getId(), document.getKind(), document.getKind().label(), document.getFileName(),
                document.getContentType(), document.getSizeBytes(), document.getExpiresOn(),
                document.getExpiresOn() != null && document.getExpiresOn().isBefore(LocalDate.now()), document.getCreatedAt());
    }

    /** A plain first page: who the carrier is and what's in the packet. */
    static byte[] cover(String company, CarrierProfile carrier, List<CarrierDocument> documents) {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            pdf.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float y = page.getMediaBox().getHeight() - 72;

            try (PDPageContentStream out = new PDPageContentStream(pdf, page)) {
                y = line(out, bold, 22, 72, y, "Carrier packet");
                y = line(out, bold, 14, 72, y - 10, company);
                List<String> details = new ArrayList<>();

                if (carrier.getMcNumber() != null) details.add("MC " + carrier.getMcNumber());
                if (carrier.getDotNumber() != null) details.add("DOT " + carrier.getDotNumber());

                if (!details.isEmpty()) y = line(out, regular, 11, 72, y, String.join("   ", details));
                if (carrier.getAddress() != null) {
                    for (String part : carrier.getAddress().split("\\R")) {
                        y = line(out, regular, 11, 72, y, part);
                    }
                }
                if (carrier.getPhone() != null) y = line(out, regular, 11, 72, y, "Phone " + carrier.getPhone());
                if (carrier.getEmail() != null) y = line(out, regular, 11, 72, y, carrier.getEmail());

                y = line(out, bold, 12, 72, y - 18, "In this packet");
                DateTimeFormatter date = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
                int number = 1;

                for (CarrierDocument document : documents) {
                    String text = number++ + ". " + document.getKind().label()
                            + (document.getExpiresOn() != null ? " (valid until " + date.format(document.getExpiresOn()) + ")" : "");
                    y = line(out, regular, 11, 84, y, text);
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pdf.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't draw the carrier packet cover", e);
        }
    }

    private static float line(PDPageContentStream out, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.newLineAtOffset(x, y);
        out.showText(InvoicePdf.safe(text.length() > 110 ? text.substring(0, 110) : text));
        out.endText();
        return y - size * 1.5f;
    }
}

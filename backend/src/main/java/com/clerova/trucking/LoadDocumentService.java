package com.clerova.trucking;

import com.clerova.config.AiRateLimitInterceptor;
import com.clerova.exception.ApiException;
import com.clerova.exception.NotFoundException;
import com.clerova.service.AiUsageService;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Documents uploaded by hand: a BOL photographed by the driver, a POD from
 * a broker portal. Clerova reads each one to tell what it is and which load
 * it belongs to; an uploaded rate con makes its load like an emailed one.
 */
@Service
public class LoadDocumentService {

    private static final Logger log = LoggerFactory.getLogger(LoadDocumentService.class);

    static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Generous for a busy dispatcher, but stops a runaway script. */
    static final int MAX_PER_HOUR = 60;

    private final LoadDocumentRepository documentRepository;
    private final LoadRepository loadRepository;
    private final FileStorage fileStorage;
    private final TruckingAiService truckingAiService;
    private final TruckingIntakeService intakeService;
    private final AiUsageService aiUsageService;
    private final AiRateLimitInterceptor aiRateLimiter;
    private final TransactionTemplate transactionTemplate;

    public LoadDocumentService(
            LoadDocumentRepository documentRepository,
            LoadRepository loadRepository,
            FileStorage fileStorage,
            TruckingAiService truckingAiService,
            TruckingIntakeService intakeService,
            AiUsageService aiUsageService,
            AiRateLimitInterceptor aiRateLimiter,
            TransactionTemplate transactionTemplate
    ) {
        this.documentRepository = documentRepository;
        this.loadRepository = loadRepository;
        this.fileStorage = fileStorage;
        this.truckingAiService = truckingAiService;
        this.intakeService = intakeService;
        this.aiUsageService = aiUsageService;
        this.aiRateLimiter = aiRateLimiter;
        this.transactionTemplate = transactionTemplate;
    }

    /** A file the dispatcher picked, as the controller received it. */
    public record Upload(String fileName, String contentType, byte[] content) {
    }

    /**
     * Keeps the file, reads it and files it: under the given load, or under
     * the load it names. A rate con with no load given makes a new load.
     */
    public LoadResponses.UploadedDocument upload(UUID organizationId, UUID userId, UUID loadId, Upload upload) {
        if (!intakeService.isTruckingWorkspace(organizationId)) {
            throw new NotFoundException("Documents are for trucking workspaces");
        }

        Load target = loadId == null ? null : requireLoad(organizationId, loadId);
        String fileName = cleanName(upload.fileName());
        String contentType = contentType(fileName, upload.contentType());

        if (upload.content() == null || upload.content().length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "That file is empty. Pick another one.");
        }

        if (upload.content().length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_BIG", "Files can be up to 10 MB.");
        }

        if (contentType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE",
                    "Upload a PDF or a photo (JPG, PNG or WebP).");
        }

        if (!fileStorage.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_OFF",
                    "Uploading files isn't turned on yet. Email the document to your Clerova address instead.");
        }

        if (documentRepository.countByOrganizationIdAndCreatedAtAfter(
                organizationId, Instant.now().minus(Duration.ofHours(1))) >= MAX_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_UPLOADS",
                    "That's a lot of uploads in one hour. Please try again a little later.");
        }

        String path = organizationId + "/uploads/" + UUID.randomUUID() + "/" + safeFileName(fileName);

        try {
            fileStorage.put(path, upload.content(), contentType);
        } catch (FileStorageException e) {
            log.warn("Could not keep uploaded {}: {}", fileName, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FILE_NOT_SAVED",
                    "Couldn't save the file right now. Please try again in a minute.");
        }

        RateConResult read = read(organizationId, upload.content(), fileName, contentType);
        DocumentKind kind = read == null ? DocumentKind.OTHER
                : read.isRateConfirmation ? DocumentKind.RATE_CON : DocumentKind.parse(read.documentKind);
        boolean newLoad = false;

        if (target == null && read != null) {
            if (read.isRateConfirmation) {
                Optional<TruckingIntakeService.SavedLoad> saved = loadFromRateCon(organizationId, fileName, read);

                if (saved.isPresent()) {
                    target = saved.get().load();
                    newLoad = saved.get().created();
                }
            } else {
                target = intakeService.loadByReference(organizationId, read.referenceNumbers).orElse(null);
            }
        }

        LoadDocument document = new LoadDocument();
        document.setOrganizationId(organizationId);
        document.setLoadId(target == null ? null : target.getId());
        document.setKind(kind);
        document.setFileName(fileName);
        document.setContentType(contentType);
        document.setSizeBytes(upload.content().length);
        document.setStoragePath(path);
        document.setUploadedBy(userId);

        return uploaded(documentRepository.save(document), newLoad);
    }

    /** Moves a document to another load, or changes what it is. */
    public LoadResponses.UploadedDocument update(UUID organizationId, UUID id, UUID loadId, DocumentKind kind) {
        LoadDocument document = require(organizationId, id);

        if (loadId != null) {
            document.setLoadId(requireLoad(organizationId, loadId).getId());
        }

        if (kind != null) {
            document.setKind(kind);
        }

        return uploaded(documentRepository.save(document), false);
    }

    public void delete(UUID organizationId, UUID id) {
        LoadDocument document = require(organizationId, id);
        documentRepository.delete(document);

        try {
            fileStorage.delete(document.getStoragePath());
        } catch (FileStorageException e) {
            // The row is gone, so nobody can reach the file; log it for cleanup
            log.warn("Could not delete the file of document {}: {}", id, e.getMessage());
        }
    }

    public List<LoadResponses.UploadedDocument> unfiled(UUID organizationId) {
        return documentRepository.findByOrganizationIdAndLoadIdIsNullOrderByCreatedAtDesc(organizationId).stream()
                .map(document -> uploaded(document, false))
                .toList();
    }

    /** The file and its name and type, for viewing in the app. */
    public record File(byte[] content, String fileName, String contentType) {
    }

    public File file(UUID organizationId, UUID id) {
        LoadDocument document = require(organizationId, id);

        try {
            return new File(fileStorage.get(document.getStoragePath()), document.getFileName(), document.getContentType());
        } catch (FileStorageException e) {
            log.warn("Could not read document {}: {}", id, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FILE_UNAVAILABLE",
                    "Couldn't open this file right now. Please try again in a minute.");
        }
    }

    /**
     * What the document is and which load it names. Best effort, and not
     * counted toward the plan: without it the file is still kept, as "Other".
     */
    private RateConResult read(UUID organizationId, byte[] content, String fileName, String contentType) {
        try {
            aiUsageService.requireAccess(organizationId);

            if (!aiRateLimiter.tryAcquireBackground()) {
                return null;
            }

            String text = PdfText.isPdf(fileName, contentType) ? PdfText.read(content) : null;
            List<String> pages = DocumentImages.dataUrls(content, fileName, contentType);

            if ((text == null || text.isBlank()) && pages.isEmpty()) {
                return null;
            }

            return truckingAiService.readRateCon(
                    "(Uploaded in Clerova by the dispatcher; there is no email.)",
                    text == null ? "" : text, pages);
        } catch (Exception e) {
            log.warn("Could not read uploaded document {}: {}", fileName, e.toString());
            return null;
        }
    }

    /** A new load counts toward the plan like an emailed rate con; at the limit the file just waits unfiled. */
    private Optional<TruckingIntakeService.SavedLoad> loadFromRateCon(UUID organizationId, String fileName, RateConResult read) {
        try {
            aiUsageService.requireQuota(organizationId);
        } catch (ApiException e) {
            return Optional.empty();
        }

        String mcNumber = TruckingIntakeService.mcNumber(read.brokerMcNumber);
        BrokerCheck check = intakeService.brokerCheck(organizationId, mcNumber);
        return Optional.ofNullable(transactionTemplate.execute(
                status -> intakeService.saveLoad(organizationId, fileName, read, mcNumber, check)));
    }

    private LoadDocument require(UUID organizationId, UUID id) {
        return documentRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    private Load requireLoad(UUID organizationId, UUID loadId) {
        return loadRepository.findByIdAndOrganizationId(loadId, organizationId)
                .orElseThrow(() -> new NotFoundException("Load not found"));
    }

    private static LoadResponses.UploadedDocument uploaded(LoadDocument document, boolean newLoad) {
        return new LoadResponses.UploadedDocument(document.getId(), document.getLoadId(), document.getKind(),
                document.getFileName(), document.getContentType(), document.getSizeBytes(), document.getCreatedAt(),
                newLoad);
    }

    /** PDFs and photos only; the type comes from the name when the browser doesn't say. */
    static String contentType(String fileName, String declared) {
        String type = declared == null ? "" : declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
        String name = fileName.toLowerCase(Locale.ROOT);

        if (type.equals("application/pdf") || (type.isEmpty() || type.equals("application/octet-stream")) && name.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (type.equals("image/jpeg") || type.equals("image/png") || type.equals("image/webp")) {
            return type;
        }

        if (type.isEmpty() || type.equals("application/octet-stream")) {
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return "image/jpeg";
            }

            if (name.endsWith(".png")) {
                return "image/png";
            }

            if (name.endsWith(".webp")) {
                return "image/webp";
            }
        }

        return null;
    }

    static String cleanName(String fileName) {
        String name = fileName == null ? "" : fileName.replaceAll(".*[/\\\\]", "").replaceAll("[\\p{Cntrl}]", "").strip();
        name = name.isEmpty() ? "document" : name;
        return name.length() <= 200 ? name : name.substring(name.length() - 200);
    }

    static String safeFileName(String fileName) {
        String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.length() <= 120 ? safe : safe.substring(safe.length() - 120);
    }
}

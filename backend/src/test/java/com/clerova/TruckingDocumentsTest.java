package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.email.InboundEmailProcessor;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import com.clerova.trucking.BrokerCheck;
import com.clerova.trucking.FmcsaClient;
import com.clerova.trucking.FmcsaStatus;
import com.clerova.trucking.LoadService;
import com.clerova.trucking.RateConResult;
import com.clerova.trucking.TruckingAiService;
import com.clerova.trucking.TruckingIntakeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static com.clerova.WorkItemWorkflowTest.userToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Scanned documents read as pictures, filing emails by hand, and uploads. */
@SpringBootTest(properties = "app.email.postmark-server-token=test-token")
@AutoConfigureMockMvc
class TruckingDocumentsTest {

    private static final String WEBHOOK_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("postmark:webhook-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InboundEmailProcessor processor;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TruckingAiService truckingAiService;

    @MockitoBean
    private FmcsaClient fmcsaClient;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private com.clerova.email.EmailSender emailSender;

    @MockitoBean
    private FileStorage fileStorage;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    private RequestPostProcessor user;
    private String forwardingAddress;

    @BeforeEach
    void setUp() throws Exception {
        when(fileStorage.isEnabled()).thenReturn(true);
        doAnswer(call -> {
            files.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(fileStorage).put(anyString(), any(), anyString());
        when(fileStorage.get(anyString())).thenAnswer(call -> {
            byte[] content = files.get(call.<String>getArgument(0));
            if (content == null) {
                throw new FileStorageException("missing");
            }
            return content;
        });
        doAnswer(call -> files.remove(call.<String>getArgument(0))).when(fileStorage).delete(anyString());

        user = userToken(UUID.randomUUID());
        mockMvc.perform(post("/api/onboarding").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Sierra Run Transport\",\"industry\":\"TRUCKING\"}"))
                .andExpect(status().isCreated());
        forwardingAddress = json(mockMvc.perform(get("/api/email-settings").with(user))).get("forwardingAddress").asText();

        when(fmcsaClient.check(anyString()))
                .thenReturn(Optional.of(new BrokerCheck(FmcsaStatus.ACTIVE, "CRESTLINE FREIGHT LLC", null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aScannedRateConIsReadFromItsPagePictures() throws Exception {
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(rateCon("88213"));

        deliver("<scan@x>", "Signed RC", "", "signed.pdf", scannedPdf());
        processor.processDue();

        ArgumentCaptor<List<String>> pages = ArgumentCaptor.forClass(List.class);
        verify(truckingAiService).readRateCon(anyString(), anyString(), pages.capture());
        assertThat(pages.getValue()).hasSize(1);
        assertThat(pages.getValue().getFirst()).startsWith("data:image/jpeg;base64,");

        JsonNode loads = json(mockMvc.perform(get("/api/loads").with(user)));
        assertThat(loads).hasSize(1);
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loads.get(0).get("id").asText()).with(user)));
        assertThat(load.get("documents")).hasSize(1);
        assertThat(load.get("documents").get(0).get("kind").asText()).isEqualTo("RATE_CON");
        assertThat(load.get("documents").get(0).get("source").asText()).isEqualTo("EMAIL");
    }

    @Test
    void aBolIsFiledUnderTheLoadItsNumberIsPrintedOn() throws Exception {
        String loadId = bookLoad("3466426-1");
        RateConResult bol = new RateConResult();
        bol.documentKind = "BOL";
        bol.referenceNumbers = new ArrayList<>(List.of("PO 7781", "#3466426"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(bol);

        // From the dispatcher's own Gmail, no load number in the email
        deliver("me@gmail.com", "<bol@x>", "Ely to Hanford", "", "bol.pdf", scannedPdf());
        processor.processDue();

        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));
        assertThat(inbox.get(0).get("status").asText()).isEqualTo("ATTACHED");
        assertThat(inbox.get(0).get("loadId").asText()).isEqualTo(loadId);
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(kinds(load)).containsExactlyInAnyOrder("RATE_CON", "BOL");
        // The dispatcher forwarded it from their own inbox: no reply to draft
        verify(truckingAiService, never()).draftBrokerReply(anyString(), anyString(), anyString());
        assertThat(load.get("draft").isNull()).isTrue();
    }

    @Test
    void aBolWhoseSubjectNamesTheLaneFindsItsLoad() throws Exception {
        String loadId = bookLoad("3466426-1");
        RateConResult bol = new RateConResult();
        bol.documentKind = "BOL";
        // Only the shipper's numbers, none of the broker's
        bol.referenceNumbers = new ArrayList<>(List.of("28522", "AOP5S1O"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(bol);

        deliver("me@gmail.com", "<lane@x>", "Fwd: Ely to Hanford", "", "ely to hanford ca.pdf", scannedPdf());
        processor.processDue();

        JsonNode email = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(email.get("status").asText()).isEqualTo("ATTACHED");
        assertThat(email.get("loadId").asText()).isEqualTo(loadId);
    }

    @Test
    void anUnmatchedEmailCanBeFiledUnderALoadByHand() throws Exception {
        String loadId = bookLoad("88213");
        RateConResult unknown = new RateConResult();
        unknown.documentKind = "BOL";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(unknown);

        deliver("me@gmail.com", "<lost@x>", "Paperwork", "", "bol.pdf", scannedPdf());
        processor.processDue();
        JsonNode email = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(email.get("status").asText()).isEqualTo("IGNORED");

        mockMvc.perform(post("/api/loads/{loadId}/emails/{messageId}", loadId, email.get("id").asText()).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation.length()").value(2));

        email = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(email.get("status").asText()).isEqualTo("ATTACHED");
        assertThat(email.get("statusReason").asText()).isEqualTo(LoadService.FILED_BY_HAND_REASON);
        assertThat(email.get("loadId").asText()).isEqualTo(loadId);

        // The rate con email made the load, so it stays where it is
        JsonNode rateConEmail = json(mockMvc.perform(get("/api/inbox").with(user))).get(1);
        mockMvc.perform(post("/api/loads/{loadId}/emails/{messageId}", loadId, rateConEmail.get("id").asText()).with(user))
                .andExpect(status().isConflict());

        // Another workspace can't file into this one
        RequestPostProcessor other = onboardNewUser(mockMvc);
        mockMvc.perform(post("/api/loads/{loadId}/emails/{messageId}", loadId, email.get("id").asText()).with(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUploadOnALoadIsReadKeptAndCanBeChangedOrRemoved() throws Exception {
        String loadId = bookLoad("88213");
        RateConResult pod = new RateConResult();
        pod.documentKind = "POD";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(pod);

        JsonNode uploaded = json(mockMvc.perform(upload("pod.jpg", "image/jpeg", jpeg()).param("loadId", loadId).with(user))
                .andExpect(status().isCreated()));
        assertThat(uploaded.get("kind").asText()).isEqualTo("POD");
        assertThat(uploaded.get("loadId").asText()).isEqualTo(loadId);
        String id = uploaded.get("id").asText();

        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(kinds(load)).contains("POD");

        mockMvc.perform(get("/api/load-documents/{id}/file", id).with(user))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        mockMvc.perform(patch("/api/load-documents/{id}", id).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"LUMPER_RECEIPT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("LUMPER_RECEIPT"));

        RequestPostProcessor other = onboardNewUser(mockMvc);
        mockMvc.perform(get("/api/load-documents/{id}/file", id).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/load-documents/{id}", id).with(other)).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/load-documents/{id}", id).with(user)).andExpect(status().isNoContent());
        verify(fileStorage).delete(anyString());
        assertThat(kinds(json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user))))).doesNotContain("LUMPER_RECEIPT");
    }

    @Test
    void anUploadWithoutALoadIsMatchedOrWaitsToBeFiled() throws Exception {
        String loadId = bookLoad("3466426-1");

        RateConResult bol = new RateConResult();
        bol.documentKind = "BOL";
        bol.referenceNumbers = new ArrayList<>(List.of("3466426-1"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(bol);
        mockMvc.perform(upload("bol.pdf", "application/pdf", scannedPdfBytes()).with(user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").value(loadId))
                .andExpect(jsonPath("$.kind").value("BOL"));

        RateConResult unknown = new RateConResult();
        unknown.documentKind = "SCALE_TICKET";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(unknown);
        String waiting = json(mockMvc.perform(upload("ticket.png", "image/png", photo()).with(user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").isEmpty())).get("id").asText();

        JsonNode unfiled = json(mockMvc.perform(get("/api/load-documents").with(user)));
        assertThat(unfiled).hasSize(1);

        mockMvc.perform(patch("/api/load-documents/{id}", waiting).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loadId\":\"" + loadId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadId").value(loadId));
        assertThat(json(mockMvc.perform(get("/api/load-documents").with(user)))).isEmpty();
    }

    @Test
    void anUploadedRateConMakesItsLoad() throws Exception {
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(rateCon("55021"));

        JsonNode uploaded = json(mockMvc.perform(upload("rc.pdf", "application/pdf", scannedPdfBytes()).with(user))
                .andExpect(status().isCreated()));
        assertThat(uploaded.get("newLoad").asBoolean()).isTrue();
        assertThat(uploaded.get("kind").asText()).isEqualTo("RATE_CON");

        JsonNode loads = json(mockMvc.perform(get("/api/loads").with(user)));
        assertThat(loads).hasSize(1);
        assertThat(loads.get(0).get("id").asText()).isEqualTo(uploaded.get("loadId").asText());
        assertThat(json(mockMvc.perform(get("/api/me").with(user)))
                .get("organization").get("aiRequestsUsedThisMonth").asInt()).isEqualTo(1);
    }

    @Test
    void paperworkThatCameFirstIsFiledWhenItsRateConArrives() throws Exception {
        // The BOL carries the shipper's number, not the broker's load number
        RateConResult bol = new RateConResult();
        bol.documentKind = "BOL";
        bol.referenceNumbers = new ArrayList<>(List.of("ABF 553780", "4/17/25"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(bol);
        deliver("me@gmail.com", "<bol-first@x>", "Ely to Hanford", "", "bol.pdf", scannedPdf());
        processor.processDue();
        assertThat(json(mockMvc.perform(get("/api/inbox").with(user))).get(0).get("status").asText()).isEqualTo("IGNORED");

        RateConResult rateCon = rateCon("3466426-1");
        rateCon.referenceNumbers = new ArrayList<>(List.of("3466426-1", "ABF553780"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(rateCon);
        deliver("<rc-later@x>", "Rate Confirmation 3466426-1", "Attached.", "ratecon.pdf", scannedPdf());
        processor.processDue();

        String loadId = json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText();
        JsonNode bolEmail = json(mockMvc.perform(get("/api/inbox").with(user))).get(1);
        assertThat(bolEmail.get("status").asText()).isEqualTo("ATTACHED");
        assertThat(bolEmail.get("loadId").asText()).isEqualTo(loadId);
        assertThat(bolEmail.get("statusReason").asText()).isEqualTo(TruckingIntakeService.PAPERWORK_FILED_REASON);
        assertThat(kinds(json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user))))).contains("BOL");

        // A later POD with only the shipper's number finds the load too
        RateConResult pod = new RateConResult();
        pod.documentKind = "POD";
        pod.referenceNumbers = new ArrayList<>(List.of("ABF553780"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(pod);
        mockMvc.perform(upload("pod.jpg", "image/jpeg", jpeg()).with(user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loadId").value(loadId));
    }

    @Test
    void theCarriersOwnMcIsNotCheckedAsTheBrokers() throws Exception {
        RateConResult read = rateCon("CCFS15310");
        read.brokerMcNumber = "1667020";
        read.carrierMcNumber = "1667020";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(read);
        deliver("<ccfs@x>", "Rate Confirmation for Shipment CCFS15310", "", "rc.pdf", scannedPdf());
        processor.processDue();

        verify(fmcsaClient, never()).check("1667020");
        String loadId = json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText();
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        List<String> codes = new ArrayList<>();
        load.get("flags").forEach(flag -> codes.add(flag.get("code").asText()));
        assertThat(codes).contains("MC_MISSING").doesNotContain("BROKER_NOT_AUTHORIZED");
    }

    @Test
    void aNewCopyReadWithACorrectedMcUpdatesTheSameLoad() throws Exception {
        RateConResult first = rateCon("CCFS15310");
        first.brokerName = "CrossCountry Freight Solutions";
        first.brokerMcNumber = "1667020";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(first);
        deliver("<v1@x>", "Rate Confirmation for Shipment CCFS15310", "", "rc.pdf", scannedPdf());
        processor.processDue();

        RateConResult corrected = rateCon("CCFS15310");
        // The AI wrote the name a little differently this time
        corrected.brokerName = "CrossCountry Freight Solutions, Inc.";
        corrected.brokerMcNumber = "1667020";
        corrected.carrierMcNumber = "1667020";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(corrected);
        deliver("<v2@x>", "Rate Confirmation for Shipment CCFS15310", "", "rc.pdf", scannedPdf());
        processor.processDue();

        assertThat(json(mockMvc.perform(get("/api/loads").with(user)))).hasSize(1);
        JsonNode brokers = json(mockMvc.perform(get("/api/brokers").with(user)));
        assertThat(brokers).hasSize(1);
        assertThat(brokers.get(0).get("broker").get("mcNumber").isNull()).isTrue();
        assertThat(json(mockMvc.perform(get("/api/inbox").with(user))).get(0).get("statusReason").asText())
                .isEqualTo(TruckingIntakeService.REVISED_REASON);

        // A third read, spelled the other way again: still one load and one broker
        RateConResult again = rateCon("CCFS15310");
        again.brokerName = "CrossCountry Freight Solutions";
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(again);
        deliver("<v3@x>", "Rate Confirmation for Shipment CCFS15310", "", "rc.pdf", scannedPdf());
        processor.processDue();
        assertThat(json(mockMvc.perform(get("/api/loads").with(user)))).hasSize(1);
        assertThat(json(mockMvc.perform(get("/api/brokers").with(user)))).hasSize(1);
    }

    @Test
    void waitingPaperworkIsFiledWhenANewCopyAddsTheNumber() throws Exception {
        // The first copy of the rate con had no shipper number
        String loadId = bookLoad("3466426-1");

        RateConResult bol = new RateConResult();
        bol.documentKind = "BOL";
        bol.referenceNumbers = new ArrayList<>(List.of("ABF553780"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(bol);
        deliver("me@gmail.com", "<bol-wait@x>", "paperwork", "", "bol.pdf", scannedPdf());
        processor.processDue();
        assertThat(json(mockMvc.perform(get("/api/inbox").with(user))).get(0).get("status").asText()).isEqualTo("IGNORED");

        RateConResult copy = rateCon("3466426-1");
        copy.referenceNumbers = new ArrayList<>(List.of("ABF553780"));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(copy);
        deliver("<rc-copy@x>", "Rate Confirmation 3466426-1 (signed)", "", "signed.pdf", scannedPdf());
        processor.processDue();

        JsonNode bolEmail = json(mockMvc.perform(get("/api/inbox").with(user))).get(1);
        assertThat(bolEmail.get("status").asText()).isEqualTo("ATTACHED");
        assertThat(bolEmail.get("loadId").asText()).isEqualTo(loadId);
    }

    @Test
    void anMcAddedByHandIsCheckedAndFixesTheLoadsFlags() throws Exception {
        RateConResult read = rateCon("CCFS15310");
        read.brokerMcNumber = null;
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(read);
        deliver("<nomc@x>", "Rate Confirmation for Shipment CCFS15310", "", "rc.pdf", scannedPdf());
        processor.processDue();

        String loadId = json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText();
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(load.get("flags").toString()).contains("MC_MISSING");
        String brokerId = load.get("broker").get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/brokers/{id}/mc-number", brokerId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content("{\"mcNumber\":\"MC# 845210\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mcNumber").value("845210"))
                .andExpect(jsonPath("$.fmcsaStatus").value("ACTIVE"));

        load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(load.get("flags").toString()).doesNotContain("MC_MISSING");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/brokers/{id}/mc-number", brokerId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content("{\"mcNumber\":\"none\"}"))
                .andExpect(status().isBadRequest());
        RequestPostProcessor other = onboardNewUser(mockMvc);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/brokers/{id}/mc-number", brokerId)
                        .with(other).contentType(MediaType.APPLICATION_JSON).content("{\"mcNumber\":\"845210\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRateConReadyEmailWithoutTheFileSaysToDownloadIt() throws Exception {
        deliver("eber@flockfreight.example", "<flock@x>", "Your Rate Confirmation is ready for Load 9Y-ZDH-T93",
                "View it in the portal.", null, null);
        processor.processDue();
        JsonNode email = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(email.get("statusReason").asText()).isEqualTo(TruckingIntakeService.NO_RATE_CON_ATTACHED_REASON);
        verify(truckingAiService, never()).readRateCon(anyString(), anyString(), anyList());
    }

    @Test
    void uploadsAreCheckedBeforeAnythingIsKept() throws Exception {
        mockMvc.perform(upload("notes.txt", "text/plain", "hello".getBytes()).with(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TYPE"));
        mockMvc.perform(upload("empty.pdf", "application/pdf", new byte[0]).with(user))
                .andExpect(status().isBadRequest());
        // A web page renamed to .pdf is refused: the first bytes decide
        mockMvc.perform(upload("invoice.pdf", "application/pdf", "<html><script>alert(1)</script></html>".getBytes()).with(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("FILE_TYPE"));
        mockMvc.perform(upload("photo.jpg", "image/jpeg", photo()).with(user))
                .andExpect(status().isBadRequest());
        mockMvc.perform(upload("x.pdf", "application/pdf", scannedPdfBytes()).param("loadId", UUID.randomUUID().toString()).with(user))
                .andExpect(status().isNotFound());

        // Property management workspaces have no loads
        RequestPostProcessor manager = onboardNewUser(mockMvc);
        mockMvc.perform(upload("x.pdf", "application/pdf", scannedPdfBytes()).with(manager))
                .andExpect(status().isNotFound());

        when(fileStorage.isEnabled()).thenReturn(false);
        mockMvc.perform(upload("x.pdf", "application/pdf", scannedPdfBytes()).with(user))
                .andExpect(status().isServiceUnavailable());

        assertThat(files).isEmpty();
        verify(truckingAiService, never()).readRateCon(anyString(), anyString(), anyList());
    }

    private String bookLoad(String loadNumber) throws Exception {
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(rateCon(loadNumber));
        deliver("<rc-" + loadNumber + "@x>", "Rate Confirmation " + loadNumber, "Attached.", "ratecon.pdf",
                TruckingIntakeTestPdf.pdf("RATE CONFIRMATION " + loadNumber));
        processor.processDue();
        return json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText();
    }

    private static RateConResult rateCon(String loadNumber) {
        RateConResult read = new RateConResult();
        read.isRateConfirmation = true;
        read.documentKind = "RATE_CON";
        read.brokerName = "Crestline Freight";
        read.brokerMcNumber = "845210";
        read.loadNumber = loadNumber;
        read.lineHaulRate = "1057.50";
        RateConResult.Stop pickup = new RateConResult.Stop();
        pickup.kind = "PICKUP";
        pickup.place = "Ely, NV";
        RateConResult.Stop delivery = new RateConResult.Stop();
        delivery.kind = "DELIVERY";
        delivery.place = "Hanford, CA";
        read.stops = new ArrayList<>(List.of(pickup, delivery));
        return read;
    }

    @Test
    void theCarrierPacketKeepsTheCarriersOwnPaperworkAndMakesOnePdf() throws Exception {
        // Empty to start: nothing to download yet
        assertThat(json(mockMvc.perform(get("/api/carrier/documents").with(user)))).isEmpty();
        mockMvc.perform(get("/api/carrier/packet").with(user)).andExpect(status().isConflict());

        // A W-9, then the authority letter and an insurance certificate that has expired
        mockMvc.perform(multipart("/api/carrier/documents").file(new MockMultipartFile("file", "w9.pdf", "application/pdf", scannedPdfBytes()))
                        .param("kind", "W9").with(user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kindLabel").value("W-9"));
        mockMvc.perform(multipart("/api/carrier/documents").file(new MockMultipartFile("file", "authority.png", "image/png", photo()))
                        .param("kind", "AUTHORITY").with(user))
                .andExpect(status().isCreated());
        String insurance = json(mockMvc.perform(multipart("/api/carrier/documents")
                        .file(new MockMultipartFile("file", "coi.pdf", "application/pdf", scannedPdfBytes()))
                        .param("kind", "INSURANCE").param("expiresOn", "2020-01-31").with(user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expired").value(true))).get("id").asText();

        // Listed in packet order: authority, insurance, W-9
        JsonNode list = json(mockMvc.perform(get("/api/carrier/documents").with(user)));
        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("kind").asText()).isEqualTo("AUTHORITY");
        assertThat(list.get(1).get("kind").asText()).isEqualTo("INSURANCE");
        assertThat(list.get(2).get("kind").asText()).isEqualTo("W9");

        // A new expiry date, then the packet: a cover page and every document, one PDF
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/carrier/documents/{id}", insurance)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content("{\"expiresOn\":\"2099-12-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(false));
        byte[] packet = mockMvc.perform(get("/api/carrier/packet").with(user))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(packet)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            String cover = new org.apache.pdfbox.text.PDFTextStripper() {{ setStartPage(1); setEndPage(1); }}.getText(pdf);
            assertThat(cover).contains("Carrier packet", "Sierra Run Transport", "MC authority letter",
                    "Certificate of insurance (valid until Dec 31, 2099)", "W-9");
        }

        // Emailed to a broker as one PDF, from the Clerova address
        mockMvc.perform(post("/api/carrier/packet/send").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"setup@crestline.example\"}")).andExpect(status().isOk());
        ArgumentCaptor<com.clerova.email.OutgoingEmail> sent = ArgumentCaptor.forClass(com.clerova.email.OutgoingEmail.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("setup@crestline.example");
        assertThat(sent.getValue().subject()).startsWith("Carrier packet - Sierra Run Transport");
        assertThat(sent.getValue().attachments()).hasSize(1);
        assertThat(sent.getValue().attachments().getFirst().contentType()).isEqualTo("application/pdf");
        mockMvc.perform(post("/api/carrier/packet/send").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"not an email\"}")).andExpect(status().isBadRequest());

        // Other kinds of workspaces can't use it
        RequestPostProcessor manager = com.clerova.WorkItemWorkflowTest.onboardNewUser(mockMvc);
        mockMvc.perform(get("/api/carrier/documents").with(manager)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/carrier/documents/{id}/file", insurance).with(manager)).andExpect(status().isNotFound());

        // Removed
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/carrier/documents/{id}", insurance).with(user))
                .andExpect(status().isNoContent());
        assertThat(json(mockMvc.perform(get("/api/carrier/documents").with(user)))).hasSize(2);

        // Too many megabytes for one packet: refused before anything is read
        for (JsonNode kept : json(mockMvc.perform(get("/api/carrier/documents").with(user)))) {
            jdbcTemplate.update("update carrier_documents set size_bytes = 25000000 where id = ?", UUID.fromString(kept.get("id").asText()));
        }
        mockMvc.perform(get("/api/carrier/packet").with(user)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Your documents add up to more than 40 MB")));
    }

    private MockMultipartHttpServletRequestBuilder upload(String name, String type, byte[] content) {
        return multipart("/api/load-documents").file(new MockMultipartFile("file", name, type, content));
    }

    /** A page with a drawing and no text, like a scan. */
    private static byte[] scannedPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(72, 600, 300, 80);
                content.stroke();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private static String scannedPdf() throws Exception {
        return Base64.getEncoder().encodeToString(scannedPdfBytes());
    }

    private static byte[] jpeg() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void aLoadBookedByPhoneIsTypedInByHand() throws Exception {
        String body = """
                {"loadNumber":"TX-4471","brokerName":"Crestline Freight","brokerEmail":"dana@crestline.example",
                 "brokerMc":"MC 845210","rate":"2,150","equipment":"53' Dry Van",
                 "stops":[{"kind":"PICKUP","place":"Fresno, CA","appointment":"Nov 10, 8:00 AM"},
                          {"kind":"DELIVERY","place":"Phoenix, AZ","appointment":""}],
                 "detentionFreeHours":"2","detentionRatePerHour":"50"}""";
        JsonNode load = json(mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()));

        assertThat(load.get("status").asText()).isEqualTo("BOOKED");
        assertThat(load.get("loadNumber").asText()).isEqualTo("TX-4471");
        assertThat(load.get("rateCents").asLong()).isEqualTo(215000L);
        assertThat(load.get("stops").size()).isEqualTo(2);
        assertThat(load.get("broker").get("mcNumber").asText()).isEqualTo("845210");
        // Only the broker is checked, and FMCSA knows this one: nothing to check before pickup
        assertThat(load.get("flags").size()).isZero();

        // The same load can't be typed in twice
        mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Load TX-4471 from this broker is already on Clerova")));

        // What's needed: the broker, a pickup and a delivery; amounts must be amounts
        mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"Crestline\",\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"Fresno\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add where the load picks up and where it delivers."));
        mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"A\"},{\"kind\":\"DELIVERY\",\"place\":\"B\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add the broker's name."));
        mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"Crestline\",\"rate\":\"call me\",\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"A\"},{\"kind\":\"DELIVERY\",\"place\":\"B\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The rate should be an amount, like 2150."));

        // No load number, no MC: still a load, with a note to get the broker's MC
        JsonNode bare = json(mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"Valley Brokerage\",\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"Visalia, CA\"},{\"kind\":\"DELIVERY\",\"place\":\"Reno, NV\"}]}"))
                .andExpect(status().isOk()));
        List<String> codes = new ArrayList<>();
        bare.get("flags").forEach(flag -> codes.add(flag.get("code").asText()));
        assertThat(codes).containsExactly("MC_MISSING");

        // Without a rate the invoice starts at $0, and a $0 invoice can't go out
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/carrier").with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Sierra Run Transport LLC\",\"mcNumber\":\"845001\",\"address\":\"410 W Main St\\nVisalia, CA 93291\","
                                + "\"email\":\"billing@sierrarun.example\",\"invoicePrefix\":\"SR-\",\"nextInvoiceNumber\":1001,\"usesFactoring\":false}"))
                .andExpect(status().isOk());
        JsonNode invoice = json(mockMvc.perform(post("/api/loads/{id}/invoice", bare.get("id").asText()).with(user))
                .andExpect(status().isOk()));
        mockMvc.perform(post("/api/invoices/{id}/mark-sent", invoice.get("id").asText()).with(user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("The invoice total is $0")));

        // Another kind of workspace can't make trucking loads
        mockMvc.perform(post("/api/loads").with(onboardNewUser(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void aLockedRateConSaysSoAndTheLoadIsMadeByHandFromTheEmail() throws Exception {
        byte[] locked;

        try (PDDocument document = new PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy =
                    new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                            "owner", "1234", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            policy.setEncryptionKeyLength(256);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            locked = out.toByteArray();
        }

        deliver("me@gmail.com", "<locked@x>", "Fwd: Signed Rate Con", "", "signed_RateCon.pdf",
                Base64.getEncoder().encodeToString(locked));
        processor.processDue();
        JsonNode email = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(email.get("status").asText()).isEqualTo("IGNORED");
        assertThat(email.get("statusReason").asText()).isEqualTo(TruckingIntakeService.LOCKED_REASON);

        JsonNode load = json(mockMvc.perform(post("/api/loads").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loadNumber\":\"9Y-ZDH-T9\",\"brokerName\":\"Flock Freight\",\"rate\":\"2400\","
                                + "\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"Stockton, CA\"},{\"kind\":\"DELIVERY\",\"place\":\"Tracy, CA\"}],"
                                + "\"fromEmailId\":\"" + email.get("id").asText() + "\"}"))
                .andExpect(status().isOk()));

        assertThat(load.get("documents").size()).isEqualTo(1);
        assertThat(load.get("documents").get(0).get("name").asText()).isEqualTo("signed_RateCon.pdf");
        email = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(email.get("status").asText()).isEqualTo("ATTACHED");
        assertThat(email.get("loadId").asText()).isEqualTo(load.get("id").asText());

        // Another workspace can't make a load from this email
        mockMvc.perform(post("/api/loads").with(onboardNewUser(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"X\",\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"A\"},{\"kind\":\"DELIVERY\",\"place\":\"B\"}],"
                                + "\"fromEmailId\":\"" + email.get("id").asText() + "\"}"))
                .andExpect(status().isNotFound());
    }

    private static byte[] photo() throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void deliver(String messageId, String subject, String body, String fileName, String pdfBase64) throws Exception {
        deliver("dana@crestline.example", messageId, subject, body, fileName, pdfBase64);
    }

    private void deliver(String from, String messageId, String subject, String body, String fileName,
                         String pdfBase64) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", from);
        root.putObject("FromFull").put("Email", from).put("Name", "Dana Ruiz");
        root.putArray("ToFull").addObject().put("Email", "dispatch@sierrarun.example");
        root.put("OriginalRecipient", forwardingAddress);
        root.put("Subject", subject);
        root.put("TextBody", body);
        root.putArray("Headers").addObject().put("Name", "Message-ID").put("Value", messageId);
        var attachments = root.putArray("Attachments");

        if (pdfBase64 != null) {
            attachments.addObject()
                    .put("Name", fileName)
                    .put("ContentType", "application/pdf")
                    .put("Content", pdfBase64);
        }

        mockMvc.perform(post("/api/inbound/postmark")
                        .header("Authorization", WEBHOOK_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isOk());
    }

    private static List<String> kinds(JsonNode load) {
        List<String> kinds = new ArrayList<>();
        load.get("documents").forEach(document -> kinds.add(document.get("kind").asText()));
        return kinds;
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}

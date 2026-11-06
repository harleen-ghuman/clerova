package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.email.EmailSender;
import com.clerova.email.InboundEmailProcessor;
import com.clerova.email.OutgoingEmail;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import com.clerova.trucking.BrokerCheck;
import com.clerova.trucking.FmcsaClient;
import com.clerova.trucking.FmcsaStatus;
import com.clerova.trucking.RateConResult;
import com.clerova.trucking.TruckingAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.clerova.WorkItemWorkflowTest.userToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 3: carrier details, detention, extra pay and invoices. */
@SpringBootTest(properties = "app.email.postmark-server-token=test-token")
@AutoConfigureMockMvc
class TruckingInvoiceTest {

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
    private EmailSender emailSender;

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
        when(emailSender.send(any())).thenReturn("pm-1");

        user = signUp("Sierra Run Transport");
        forwardingAddress = forwardingAddress(user);
        when(fmcsaClient.check(anyString()))
                .thenReturn(Optional.of(new BrokerCheck(FmcsaStatus.ACTIVE, "CRESTLINE FREIGHT LLC", null)));
    }

    // ---------------------------------------------------------------- carrier

    @Test
    void carrierDetailsAreSavedCheckedAndSayWhatInvoicesNeed() throws Exception {
        JsonNode blank = json(mockMvc.perform(get("/api/carrier").with(user)).andExpect(status().isOk()));
        assertThat(blank.get("nextInvoiceNumber").asInt()).isEqualTo(1001);
        assertThat(blank.get("missing")).hasSize(3);

        mockMvc.perform(put("/api/carrier").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content(carrierJson(false).replace("\"845001\"", "\"MC 84A\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter the MC number as digits, like 845210."));

        mockMvc.perform(put("/api/carrier").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content(carrierJson(false).replace("billing@sierrarun.example", "not-an-email")))
                .andExpect(status().isBadRequest());

        JsonNode saved = json(mockMvc.perform(put("/api/carrier").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content(carrierJson(false).replace("\"845001\"", "\"MC-845001\"")))
                .andExpect(status().isOk()));
        assertThat(saved.get("mcNumber").asText()).isEqualTo("845001");
        assertThat(saved.get("missing")).isEmpty();

        // Factoring needs the factor's name and remit-to address
        JsonNode factoring = json(mockMvc.perform(put("/api/carrier").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content(carrierJson(false).replace("\"usesFactoring\":false", "\"usesFactoring\":true")))
                .andExpect(status().isOk()));
        assertThat(factoring.get("missing")).hasSize(2);
    }

    // -------------------------------------------------------------- detention

    @Test
    void stopTimesMoveTheLoadAlongAndMakeTheDetentionCharge() throws Exception {
        String loadId = load("88213");
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        String pickup = load.get("stops").get(0).get("id").asText();
        String delivery = load.get("stops").get(1).get("id").asText();
        Instant arrived = Instant.now().minus(16, ChronoUnit.HOURS);

        // Leaving the pickup means loaded
        times(loadId, pickup, arrived, arrived.plus(1, ChronoUnit.HOURS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.charges.length()").value(0));

        // Waiting at delivery: the time on site counts while the truck is there
        Instant atDelivery = arrived.plus(2, ChronoUnit.HOURS);
        JsonNode waiting = json(times(loadId, delivery, atDelivery, null).andExpect(status().isOk()));
        assertThat(waiting.get("stops").get(1).get("detention").get("minutesOnSite").asLong()).isBetween(839L, 841L);
        assertThat(waiting.get("charges")).isEmpty();

        // Left 3h 40m after arriving: 2h free, $50/hour, 1h 30m billable (whole quarter hours) = $75
        JsonNode left = json(times(loadId, delivery, atDelivery, atDelivery.plus(220, ChronoUnit.MINUTES))
                .andExpect(status().isOk()));
        assertThat(left.get("status").asText()).isEqualTo("DELIVERED");
        assertThat(left.get("stops").get(1).get("detention").get("billableMinutes").asLong()).isEqualTo(90);
        assertThat(left.get("charges")).hasSize(1);
        assertThat(left.get("charges").get(0).get("kind").asText()).isEqualTo("DETENTION");
        assertThat(left.get("charges").get(0).get("amountCents").asLong()).isEqualTo(7500);
        assertThat(left.get("charges").get(0).get("status").asText()).isEqualTo("TO_CLAIM");

        // Fixing the time updates the charge while it's unclaimed; under the free time it goes away
        JsonNode longer = json(times(loadId, delivery, atDelivery, atDelivery.plus(10, ChronoUnit.HOURS)));
        assertThat(longer.get("charges").get(0).get("amountCents").asLong()).isEqualTo(20000); // the $200 cap
        JsonNode shorter = json(times(loadId, delivery, atDelivery, atDelivery.plus(90, ChronoUnit.MINUTES)));
        assertThat(shorter.get("charges")).isEmpty();

        // Times that can't be right are refused
        times(loadId, delivery, atDelivery, atDelivery.minus(1, ChronoUnit.HOURS)).andExpect(status().isBadRequest());
        times(loadId, delivery, null, atDelivery).andExpect(status().isBadRequest());
        times(loadId, delivery, Instant.now().plus(2, ChronoUnit.HOURS), null).andExpect(status().isBadRequest());
    }

    @Test
    void aClaimEmailsTheBrokerAndMarksTheChargeClaimed() throws Exception {
        String loadId = load("88213");
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        String delivery = load.get("stops").get(1).get("id").asText();
        Instant arrived = Instant.now().minus(6, ChronoUnit.HOURS);
        JsonNode left = json(times(loadId, delivery, arrived, arrived.plus(4, ChronoUnit.HOURS)));
        String chargeId = left.get("charges").get(0).get("id").asText();

        JsonNode claimed = json(mockMvc.perform(post("/api/loads/{id}/messages", loadId).with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Load 88213: detention claim\",\"body\":\"Hi,\\n\\nWe're claiming detention.\\n\\nThanks,\",\"chargeId\":\""
                                + chargeId + "\"}"))
                .andExpect(status().isOk()));
        assertThat(claimed.get("charges").get(0).get("status").asText()).isEqualTo("CLAIMED");
        assertThat(claimed.get("conversation").get(claimed.get("conversation").size() - 1).get("subject").asText())
                .isEqualTo("Load 88213: detention claim");

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("dana@crestline.example");
        assertThat(sent.getValue().textBody()).endsWith("Thanks,\nSierra Run Transport");

        // A claimed charge isn't changed by new times
        JsonNode moved = json(times(loadId, delivery, arrived, arrived.plus(5, ChronoUnit.HOURS)));
        assertThat(moved.get("charges").get(0).get("amountCents").asLong()).isEqualTo(left.get("charges").get(0).get("amountCents").asLong());
    }

    @Test
    void emailsFromAStopOrAChargeShowWhereAndWhenTheyWent() throws Exception {
        String loadId = load("88213");
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        String delivery = load.get("stops").get(1).get("id").asText();
        Instant arrived = Instant.now().minus(6, ChronoUnit.HOURS);
        times(loadId, delivery, arrived, null);

        JsonNode noticed = json(mockMvc.perform(post("/api/loads/{id}/messages", loadId).with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Load 88213: arrived\",\"body\":\"Hi, our driver arrived.\",\"stopId\":\"" + delivery + "\"}"))
                .andExpect(status().isOk()));
        JsonNode emails = noticed.get("stops").get(1).get("emails");
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).get("toEmail").asText()).isEqualTo("dana@crestline.example");
        assertThat(emails.get(0).get("subject").asText()).isEqualTo("Load 88213: arrived");
        assertThat(emails.get(0).get("sentAt").isNull()).isFalse();
        assertThat(noticed.get("stops").get(0).get("emails")).isEmpty();

        JsonNode left = json(times(loadId, delivery, arrived, arrived.plus(4, ChronoUnit.HOURS)));
        String chargeId = left.get("charges").get(0).get("id").asText();
        assertThat(left.get("charges").get(0).get("claimEmail").isNull()).isTrue();
        JsonNode claimed = json(mockMvc.perform(post("/api/loads/{id}/messages", loadId).with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Load 88213: detention claim\",\"body\":\"Claiming detention.\",\"chargeId\":\"" + chargeId + "\"}"))
                .andExpect(status().isOk()));
        assertThat(claimed.get("charges").get(0).get("claimEmail").get("toEmail").asText()).isEqualTo("dana@crestline.example");

        // A stop of another load can't be linked
        mockMvc.perform(post("/api/loads/{id}/messages", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"x\",\"stopId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invoiceDatesAndTheMonthlyExportFollowTheCarriersTimeZone() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");
        String invoiceId = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))).get("id").asText();
        mockMvc.perform(post("/api/invoices/{id}/mark-sent", invoiceId).with(user)).andExpect(status().isOk());
        // Sent at 12:30 AM on Nov 1 in New York, which is still Oct 31 in California
        jdbcTemplate.update("update invoices set sent_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.parse("2026-11-01T04:30:00Z")), UUID.fromString(invoiceId));

        mockMvc.perform(put("/api/me/time-zone").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"timeZone\":\"Mars/Base\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/me/time-zone").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeZone\":\"America/New_York\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.timeZone").value("America/New_York"));

        assertThat(export("2026-11")).contains("\"SR-1001\"", "\"2026-11-01\"");
        assertThat(export("2026-10")).doesNotContain("SR-1001");

        mockMvc.perform(put("/api/me/time-zone").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"timeZone\":\"America/Los_Angeles\"}")).andExpect(status().isOk());
        assertThat(export("2026-10")).contains("\"SR-1001\"", "\"2026-10-31\"");
        assertThat(export("2026-11")).doesNotContain("SR-1001");

        // The PDF's invoice date follows too
        byte[] pdf = mockMvc.perform(get("/api/invoices/{id}/pdf", invoiceId).with(user)).andReturn().getResponse().getContentAsByteArray();
        assertThat(pdfText(pdf)).contains("Oct 31, 2026");
    }

    private String export(String month) throws Exception {
        return mockMvc.perform(get("/api/invoices/export").param("month", month).with(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void chargesAreAddedChangedAndRemovedByHand() throws Exception {
        String loadId = load("88213");

        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"LUMPER\",\"amountCents\":0}"))
                .andExpect(status().isBadRequest());

        JsonNode added = json(mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"LUMPER\",\"amountCents\":18500,\"description\":\"Capstone #5521\",\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk()));
        String chargeId = added.get("charges").get(0).get("id").asText();
        assertThat(added.get("charges").get(0).get("label").asText()).isEqualTo("Lumper");

        JsonNode denied = json(mockMvc.perform(patch("/api/loads/{id}/charges/{c}", loadId, chargeId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DENIED\"}")));
        assertThat(denied.get("charges").get(0).get("status").asText()).isEqualTo("DENIED");

        JsonNode removed = json(mockMvc.perform(delete("/api/loads/{id}/charges/{c}", loadId, chargeId).with(user)));
        assertThat(removed.get("charges")).isEmpty();
    }

    // --------------------------------------------------------------- invoices

    @Test
    void anInvoiceNeedsTheCarrierDetailsFirst() throws Exception {
        String loadId = load("88213");

        mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CARRIER_DETAILS_MISSING"))
                .andExpect(jsonPath("$.message").value(
                        "Add your company name, your address and your MC or DOT number in Settings first. They go on every invoice."));
    }

    @Test
    void anInvoiceIsMadeSentToTheBrokerAndPaid() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");
        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"LUMPER\",\"amountCents\":18500,\"status\":\"APPROVED\"}"));
        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"LAYOVER\",\"amountCents\":25000,\"status\":\"CLAIMED\"}"));

        JsonNode invoice = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))
                .andExpect(status().isOk()));
        String invoiceId = invoice.get("id").asText();
        assertThat(invoice.get("number").asText()).isEqualTo("SR-1001");
        assertThat(invoice.get("sendTo").asText()).isEqualTo("BROKER");
        assertThat(invoice.get("lines")).hasSize(2); // line haul + the approved lumper, not the claimed layover
        assertThat(invoice.get("totalCents").asLong()).isEqualTo(105750 + 18500);
        assertThat(invoice.get("remitTo").asText()).startsWith("Sierra Run Transport LLC\n");
        assertThat(invoice.get("missingPaperwork").toString()).contains("signed BOL or POD", "lumper receipt");
        assertThat(invoice.get("suggestedEmail").asText()).isEqualTo("billing@crestline.example");

        // A second tap gives the same invoice
        assertThat(json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))).get("id").asText())
                .isEqualTo(invoiceId);

        // Lines can change on a draft; quick pay takes its fee
        JsonNode edited = json(mockMvc.perform(patch("/api/invoices/{id}", invoiceId).with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"description\":\"Line haul\",\"amountCents\":100000}],\"quickPay\":true}"))
                .andExpect(status().isOk()));
        assertThat(edited.get("totalCents").asLong()).isEqualTo(100000);
        assertThat(edited.get("quickPayFeeCents").asLong()).isEqualTo(2500);
        mockMvc.perform(patch("/api/invoices/{id}", invoiceId).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"description\":\"\",\"amountCents\":100}]}"))
                .andExpect(status().isBadRequest());
        JsonNode rebuilt = json(mockMvc.perform(patch("/api/invoices/{id}", invoiceId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rebuild\":true,\"quickPay\":false}")));
        assertThat(rebuilt.get("lines")).hasSize(2);
        assertThat(rebuilt.get("quickPayFeeCents").isNull()).isTrue();

        // The paperwork goes in the packet
        uploadPod(loadId);
        JsonNode withPod = json(mockMvc.perform(get("/api/invoices/{id}", invoiceId).with(user)));
        assertThat(withPod.get("missingPaperwork").toString()).doesNotContain("BOL");
        byte[] invoicePdf = mockMvc.perform(get("/api/invoices/{id}/pdf", invoiceId).with(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text = pdfText(invoicePdf);
        assertThat(text).contains("INVOICE", "SR-1001", "Sierra Run Transport LLC", "Crestline Freight", "Lumper",
                "$1,242.50", "Net 30");
        byte[] packet = mockMvc.perform(get("/api/invoices/{id}/packet", invoiceId).with(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument document = Loader.loadPDF(packet)) {
            assertThat(document.getNumberOfPages()).isEqualTo(3); // invoice, rate con, POD
        }

        // Sent by email with the packet attached
        JsonNode sent = json(mockMvc.perform(post("/api/invoices/{id}/send", invoiceId).with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"billing@crestline.example\",\"message\":\"Hi,\\n\\nInvoice attached.\\n\\nThanks,\"}"))
                .andExpect(status().isOk()));
        assertThat(sent.get("status").asText()).isEqualTo("SENT");
        assertThat(sent.get("sentVia").asText()).isEqualTo("EMAIL");
        assertThat(LocalDate.parse(sent.get("dueDate").asText()))
                .isBetween(LocalDate.now().plusDays(29), LocalDate.now().plusDays(31));
        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender, atLeastOnce()).send(email.capture());
        OutgoingEmail invoiceEmail = email.getValue();
        assertThat(invoiceEmail.subject()).isEqualTo("Invoice SR-1001 - Load 88213 - Sierra Run Transport LLC");
        assertThat(invoiceEmail.replyTo()).isEqualTo("billing@sierrarun.example");
        assertThat(invoiceEmail.attachments()).hasSize(1);
        assertThat(invoiceEmail.attachments().getFirst().name()).isEqualTo("Invoice SR-1001 packet.pdf");
        assertThat(new String(invoiceEmail.attachments().getFirst().content(), 0, 5, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
        assertThat(json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user))).get("status").asText())
                .isEqualTo("INVOICED");

        // Sent invoices and their charges are fixed
        mockMvc.perform(patch("/api/invoices/{id}", invoiceId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quickPay\":true}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"OTHER\",\"amountCents\":1000}")).andExpect(status().isConflict());

        // Paid, and back if that was a mistake
        JsonNode paid = json(mockMvc.perform(post("/api/invoices/{id}/paid", invoiceId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"paidCents\":124250}")).andExpect(status().isOk()));
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user))).get("status").asText()).isEqualTo("PAID");
        mockMvc.perform(post("/api/invoices/{id}/void", invoiceId).with(user)).andExpect(status().isConflict());
        assertThat(json(mockMvc.perform(post("/api/invoices/{id}/unpaid", invoiceId).with(user))).get("status").asText())
                .isEqualTo("SENT");

        // The month's spreadsheet
        String month = LocalDate.now(java.time.ZoneId.of("America/Los_Angeles")).toString().substring(0, 7);
        String csv = mockMvc.perform(get("/api/invoices/export").param("month", month).with(user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"SR-1001\"", "\"88213\"", "\"Crestline Freight\"", "\"1242.50\"", "\"Waiting on payment\"");
        // Each kind of pay has its own column, and they add up to the total
        List<String> header = csvCells(csv.lines().findFirst().orElseThrow().replace("\uFEFF", ""));
        assertThat(header).containsSubsequence("Status", "Line haul", "Detention", "Lumper", "TONU", "Layover", "Other", "Total");
        List<String> row = csvCells(csv.lines().filter(line -> line.startsWith("\"SR-1001\"")).findFirst().orElseThrow());
        java.math.BigDecimal pay = java.math.BigDecimal.ZERO;

        for (String kind : List.of("Line haul", "Detention", "Lumper", "TONU", "Layover", "Other")) {
            String cell = row.get(header.indexOf(kind));
            pay = cell.isEmpty() ? pay : pay.add(new java.math.BigDecimal(cell));
        }

        assertThat(row.get(header.indexOf("Line haul"))).isNotEmpty();
        assertThat(pay).isEqualByComparingTo(row.get(header.indexOf("Total")));
        mockMvc.perform(get("/api/invoices/export").param("month", "soon").with(user)).andExpect(status().isBadRequest());
    }

    @Test
    void withFactoringTheInvoicePaysTheFactorAndCanBeMarkedUploaded() throws Exception {
        saveCarrier(true);
        String loadId = load("88213");
        JsonNode invoice = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user)));
        String invoiceId = invoice.get("id").asText();
        assertThat(invoice.get("sendTo").asText()).isEqualTo("FACTOR");
        assertThat(invoice.get("factoring").asBoolean()).isTrue();
        assertThat(invoice.get("remitTo").asText()).isEqualTo("Apex Capital\nPO Box 961029, Fort Worth, TX 76161");
        assertThat(invoice.get("suggestedEmail").asText()).isEqualTo("docs@apex.example");

        String text = pdfText(mockMvc.perform(get("/api/invoices/{id}/pdf", invoiceId).with(user))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(text).contains("Notice of assignment", "Pay only Apex Capital");

        // Quick pay is for brokers only
        JsonNode noQuickPay = json(mockMvc.perform(patch("/api/invoices/{id}", invoiceId).with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quickPay\":true}")));
        assertThat(noQuickPay.get("quickPay").asBoolean()).isFalse();

        JsonNode uploaded = json(mockMvc.perform(post("/api/invoices/{id}/mark-sent", invoiceId).with(user))
                .andExpect(status().isOk()));
        assertThat(uploaded.get("status").asText()).isEqualTo("SENT");
        assertThat(uploaded.get("sentVia").asText()).isEqualTo("PORTAL");
        assertThat(uploaded.get("dueDate").isNull()).isTrue(); // the factor pays on its own schedule

        // Voiding frees the load for a new invoice with the next number
        mockMvc.perform(post("/api/invoices/{id}/void", invoiceId).with(user)).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user))).get("invoice").isNull()).isTrue();
        assertThat(json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))).get("number").asText())
                .isEqualTo("SR-1002");
        assertThat(json(mockMvc.perform(get("/api/invoices").with(user)))).hasSize(1);
    }

    @Test
    void anotherWorkspaceCantSeeOrChangeInvoices() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");
        String invoiceId = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))).get("id").asText();
        String stopId = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)))
                .get("stops").get(0).get("id").asText();

        RequestPostProcessor other = signUp("Other Carrier");

        mockMvc.perform(get("/api/invoices/{id}", invoiceId).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/invoices/{id}/packet", invoiceId).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/invoices/{id}/paid", invoiceId).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/invoices/{id}/void", invoiceId).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(other)).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/loads/{id}/stops/{s}/times", loadId, stopId).with(other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"arrivedAt\":null,\"leftAt\":null}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"LUMPER\",\"amountCents\":100}")).andExpect(status().isNotFound());
        assertThat(json(mockMvc.perform(get("/api/invoices").with(other)))).isEmpty();
    }

    @Test
    void anotherWorkspaceCantReachAnyTruckingRecord() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");
        uploadPod(loadId);
        when(truckingAiService.draftBrokerReply(anyString(), anyString(), anyString())).thenAnswer(call -> {
            var result = new com.clerova.trucking.BrokerReplyResult();
            result.needsReply = true;
            result.kind = com.clerova.trucking.ReplyKind.STATUS;
            result.summary = "Asks where the truck is";
            result.draft = "Hi Dana,\n\nOn the way.";
            return result;
        });
        deliverText("<q-88213@x>", "Where is the truck on 88213?");
        processor.processDue();
        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"LUMPER\",\"amountCents\":5000}")).andExpect(status().is2xxSuccessful());

        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        String replyId = load.get("draft").get("id").asText();
        String chargeId = load.get("charges").get(0).get("id").asText();
        String flagId = load.get("flags").isEmpty() ? UUID.randomUUID().toString() : load.get("flags").get(0).get("id").asText();
        String brokerId = load.get("broker").get("id").asText();
        String documentId = load.get("documents").get(0).get("id").asText();
        String messageId = json(mockMvc.perform(get("/api/inbox").with(user))).get(0).get("id").asText();
        String invoiceId = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))).get("id").asText();

        RequestPostProcessor other = signUp("Other Carrier");
        var json = MediaType.APPLICATION_JSON;
        List<org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder<?>> attempts = List.of(
                get("/api/loads/{id}", loadId),
                patch("/api/loads/{id}", loadId).contentType(json).content("{\"status\":\"CANCELLED\"}"),
                post("/api/loads/{id}/flags/{f}/resolve", loadId, flagId),
                post("/api/loads/{id}/replies/{r}/send", loadId, replyId).contentType(json).content("{\"body\":\"x\"}"),
                post("/api/loads/{id}/replies/{r}/dismiss", loadId, replyId),
                post("/api/loads/{id}/messages", loadId).contentType(json)
                        .content("{\"kind\":\"STATUS\",\"subject\":\"x\",\"body\":\"x\"}"),
                patch("/api/loads/{id}/charges/{c}", loadId, chargeId).contentType(json).content("{\"amountCents\":1}"),
                delete("/api/loads/{id}/charges/{c}", loadId, chargeId),
                post("/api/loads/{l}/emails/{m}", loadId, messageId),
                put("/api/brokers/{id}/mc-number", brokerId).contentType(json).content("{\"mcNumber\":\"111111\"}"),
                get("/api/load-documents/{id}/file", documentId),
                patch("/api/load-documents/{id}", documentId).contentType(json).content("{\"kind\":\"BOL\"}"),
                delete("/api/load-documents/{id}", documentId),
                multipart("/api/load-documents").file(new MockMultipartFile("file", "pod.pdf", "application/pdf", pdf("POD")))
                        .param("loadId", loadId),
                get("/api/invoices/{id}/pdf", invoiceId),
                patch("/api/invoices/{id}", invoiceId).contentType(json).content("{\"notes\":\"x\"}"),
                post("/api/invoices/{id}/send", invoiceId).contentType(json).content("{}"),
                post("/api/invoices/{id}/mark-sent", invoiceId),
                post("/api/invoices/{id}/unpaid", invoiceId)
        );

        for (var attempt : attempts) {
            int code = mockMvc.perform(attempt.with(other)).andReturn().getResponse().getStatus();
            assertThat(code).as(attempt.toString()).isIn(400, 404);
        }

        // Nothing of the first carrier shows in the other's lists or export
        assertThat(json(mockMvc.perform(get("/api/loads").with(other)))).isEmpty();
        assertThat(json(mockMvc.perform(get("/api/brokers").with(other)))).isEmpty();
        assertThat(json(mockMvc.perform(get("/api/inbox").with(other)))).isEmpty();
        assertThat(json(mockMvc.perform(get("/api/load-documents").with(other)))).isEmpty();
        assertThat(mockMvc.perform(get("/api/invoices/export").with(other)).andReturn().getResponse()
                .getContentAsString()).doesNotContain("88213");
        assertThat(json(mockMvc.perform(get("/api/carrier").with(other))).get("legalName").isNull()).isTrue();

        // And the first carrier's records are untouched
        JsonNode after = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(after.get("status").asText()).isEqualTo(load.get("status").asText());
        assertThat(after.get("draft").get("status").asText()).isEqualTo("DRAFT");
        assertThat(after.get("charges")).hasSize(load.get("charges").size());
        assertThat(after.get("charges").get(0).get("amountCents").asLong()).isEqualTo(5000);
        assertThat(after.get("documents")).hasSize(load.get("documents").size());
        assertThat(after.get("broker").get("mcNumber").asText()).isEqualTo("845210");
    }

    @Test
    void aPropertyManagerCantUseTruckingAndACarrierCantUseRequests() throws Exception {
        String loadId = load("88213");
        RequestPostProcessor manager = WorkItemWorkflowTest.userToken(UUID.randomUUID());
        mockMvc.perform(post("/api/onboarding").with(manager).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Oak Property Co\",\"industry\":\"PROPERTY_MANAGEMENT\"}"))
                .andExpect(status().isCreated());

        assertThat(mockMvc.perform(get("/api/loads/{id}", loadId).with(manager)).andReturn().getResponse().getStatus())
                .isIn(403, 404);
        assertThat(json(mockMvc.perform(get("/api/loads").with(manager)))).isEmpty();
        assertThat(mockMvc.perform(post("/api/billing/trucks").with(manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trucks\":5}")).andReturn().getResponse().getStatus()).isIn(409, 503);
    }

    @Test
    void aCancelledLoadCanBeBilledItsTonu() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");
        mockMvc.perform(patch("/api/loads/{id}", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This load is cancelled. To bill a TONU, add it under Extra pay as approved first."));

        mockMvc.perform(post("/api/loads/{id}/charges", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"TONU\",\"amountCents\":15000,\"status\":\"APPROVED\"}")).andExpect(status().isOk());
        JsonNode invoice = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user)).andExpect(status().isOk()));
        assertThat(invoice.get("lines")).hasSize(1);
        assertThat(invoice.get("lines").get(0).get("description").asText()).isEqualTo("Truck ordered, not used");
        assertThat(invoice.get("totalCents").asLong()).isEqualTo(15000);
        assertThat(invoice.get("missingPaperwork").toString()).doesNotContain("BOL");

        mockMvc.perform(post("/api/invoices/{id}/mark-sent", invoice.get("id").asText()).with(user)).andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/{id}/paid", invoice.get("id").asText()).with(user)).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user))).get("status").asText())
                .isEqualTo("CANCELLED");
    }

    @Test
    void aDoubleTapMakesOneInvoiceAndTheFactorNeedsItsDetails() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");

        // Two taps at once: one invoice, one number used
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        List<java.util.concurrent.Future<String>> taps = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            taps.add(pool.submit(() -> mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))
                    .andReturn().getResponse().getContentAsString()));
        }

        List<String> ids = new ArrayList<>();

        for (var tap : taps) {
            ids.add(objectMapper.readTree(tap.get()).get("id").asText());
        }

        pool.shutdown();
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(json(mockMvc.perform(get("/api/carrier").with(user))).get("nextInvoiceNumber").asInt()).isEqualTo(1002);

        // Factoring is off, so the invoice can't go to a factor
        mockMvc.perform(patch("/api/invoices/{id}", ids.get(0)).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sendTo\":\"FACTOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Add your factoring company's name and payment address in Settings first."));

        // A changed company name reaches the draft
        mockMvc.perform(put("/api/carrier").with(user).contentType(MediaType.APPLICATION_JSON)
                .content(carrierJson(false).replace("Sierra Run Transport LLC", "Sierra Run Freight LLC"))).andExpect(status().isOk());
        assertThat(json(mockMvc.perform(patch("/api/invoices/{id}", ids.get(0)).with(user)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))).get("remitTo").asText())
                .startsWith("Sierra Run Freight LLC");
    }

    @Test
    void detentionStaysPutOnceTheInvoiceIsSent() throws Exception {
        saveCarrier(false);
        String loadId = load("88213");
        String delivery = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)))
                .get("stops").get(1).get("id").asText();
        Instant arrived = Instant.now().minus(8, ChronoUnit.HOURS);
        times(loadId, delivery, arrived, arrived.plus(4, ChronoUnit.HOURS)).andExpect(status().isOk());
        String invoiceId = json(mockMvc.perform(post("/api/loads/{id}/invoice", loadId).with(user))).get("id").asText();
        mockMvc.perform(post("/api/invoices/{id}/mark-sent", invoiceId).with(user)).andExpect(status().isOk());

        JsonNode after = json(times(loadId, delivery, arrived, arrived.plus(6, ChronoUnit.HOURS)).andExpect(status().isOk()));
        assertThat(after.get("charges").get(0).get("amountCents").asLong()).isEqualTo(10000);
    }

    @Test
    void aWorkspaceCantSendMoreThanItsHourlyEmails() throws Exception {
        String loadId = load("88213");
        String body = "{\"subject\":\"Hi\",\"body\":\"Checking in.\"}";

        for (int i = 0; i < com.clerova.trucking.InvoiceService.MAX_EMAILS_PER_HOUR; i++) {
            mockMvc.perform(post("/api/loads/{id}/messages", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/loads/{id}/messages", loadId).with(user).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("EMAIL_LIMIT"));
    }

    // ---------------------------------------------------------------- helpers

    private RequestPostProcessor signUp(String company) throws Exception {
        RequestPostProcessor token = userToken(UUID.randomUUID());
        mockMvc.perform(post("/api/onboarding").with(token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"" + company + "\",\"industry\":\"TRUCKING\"}"))
                .andExpect(status().isCreated());
        return token;
    }

    private String forwardingAddress(RequestPostProcessor token) throws Exception {
        return json(mockMvc.perform(get("/api/email-settings").with(token))).get("forwardingAddress").asText();
    }

    private static String carrierJson(boolean factoring) {
        return "{\"legalName\":\"Sierra Run Transport LLC\",\"mcNumber\":\"845001\",\"dotNumber\":\"3311200\","
                + "\"address\":\"410 W Main St\\nVisalia, CA 93291\",\"phone\":\"559-555-0142\","
                + "\"email\":\"billing@sierrarun.example\",\"invoicePrefix\":\"SR-\",\"nextInvoiceNumber\":1001,"
                + "\"usesFactoring\":" + factoring + ","
                + (factoring ? "\"factorName\":\"Apex Capital\",\"factorAddress\":\"PO Box 961029, Fort Worth, TX 76161\","
                        + "\"factorEmail\":\"docs@apex.example\"" : "\"factorName\":null,\"factorAddress\":null,\"factorEmail\":null")
                + "}";
    }

    private void saveCarrier(boolean factoring) throws Exception {
        mockMvc.perform(put("/api/carrier").with(user).contentType(MediaType.APPLICATION_JSON).content(carrierJson(factoring)))
                .andExpect(status().isOk());
    }

    private ResultActions times(String loadId, String stopId, Instant arrived, Instant left) throws Exception {
        return mockMvc.perform(put("/api/loads/{id}/stops/{s}/times", loadId, stopId).with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"arrivedAt\":" + (arrived == null ? "null" : "\"" + arrived + "\"")
                        + ",\"leftAt\":" + (left == null ? "null" : "\"" + left + "\"") + "}"));
    }

    /** A load from a Crestline rate con: $1,057.50, 2h free then $50/hour up to $200, Net 30, 2.5% quick pay. */
    private String load(String loadNumber) throws Exception {
        RateConResult read = new RateConResult();
        read.isRateConfirmation = true;
        read.documentKind = "RATE_CON";
        read.brokerName = "Crestline Freight";
        read.brokerMcNumber = "845210";
        read.brokerEmail = "dana@crestline.example";
        read.invoiceEmail = "billing@crestline.example";
        read.loadNumber = loadNumber;
        read.lineHaulRate = "1057.50";
        read.detentionFreeHours = "2";
        read.detentionRatePerHour = "50";
        read.detentionMaxAmount = "200";
        read.paymentTerms = "Net 30";
        read.quickPayFeePercent = "2.5";
        RateConResult.Stop pickup = new RateConResult.Stop();
        pickup.kind = "PICKUP";
        pickup.place = "Sunridge Packing, Fresno, CA";
        RateConResult.Stop delivery = new RateConResult.Stop();
        delivery.kind = "DELIVERY";
        delivery.place = "Desert Foods DC, Phoenix, AZ";
        read.stops = new ArrayList<>(List.of(pickup, delivery));
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(read);

        deliver("<rc-" + loadNumber + "@x>", "Rate con " + loadNumber, "rate-con-" + loadNumber + ".pdf");
        processor.processDue();

        JsonNode loads = json(mockMvc.perform(get("/api/loads").with(user)));
        assertThat(loads).hasSize(1);
        return loads.get(0).get("id").asText();
    }

    private void uploadPod(String loadId) throws Exception {
        String id = json(mockMvc.perform(multipart("/api/load-documents")
                        .file(new MockMultipartFile("file", "pod.pdf", "application/pdf", pdf("Signed POD")))
                        .param("loadId", loadId).with(user))
                .andExpect(status().is2xxSuccessful())).get("id").asText();
        mockMvc.perform(patch("/api/load-documents/{id}", id).with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"POD\"}")).andExpect(status().isOk());
    }

    private void deliver(String messageId, String subject, String fileName) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", "dana@crestline.example");
        root.putObject("FromFull").put("Email", "dana@crestline.example").put("Name", "Dana Ruiz");
        root.putArray("ToFull").addObject().put("Email", "dispatch@sierrarun.example");
        root.put("OriginalRecipient", forwardingAddress);
        root.put("Subject", subject);
        root.put("TextBody", "Rate con attached.");
        root.putArray("Headers").addObject().put("Name", "Message-ID").put("Value", messageId);
        root.putArray("Attachments").addObject()
                .put("Name", fileName)
                .put("ContentType", "application/pdf")
                .put("Content", Base64.getEncoder().encodeToString(pdf("RATE CONFIRMATION Load " + subject)));

        mockMvc.perform(post("/api/inbound/postmark")
                        .header("Authorization", WEBHOOK_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isOk());
    }

    private void deliverText(String messageId, String subject) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", "dana@crestline.example");
        root.putObject("FromFull").put("Email", "dana@crestline.example").put("Name", "Dana Ruiz");
        root.putArray("ToFull").addObject().put("Email", "dispatch@sierrarun.example");
        root.put("OriginalRecipient", forwardingAddress);
        root.put("Subject", subject);
        root.put("TextBody", "Receiver is asking for an ETA.");
        root.putArray("Headers").addObject().put("Name", "Message-ID").put("Value", messageId);
        mockMvc.perform(post("/api/inbound/postmark").header("Authorization", WEBHOOK_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isOk());
    }

    private static byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }

            document.save(out);
            return out.toByteArray();
        }
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** The cells of one spreadsheet row (every cell is quoted). */
    private static List<String> csvCells(String line) {
        String trimmed = line.strip();
        return java.util.Arrays.stream(trimmed.substring(1, trimmed.length() - 1).split("\",\"", -1))
                .map(cell -> cell.replace("\"\"", "\""))
                .toList();
    }
}

package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.email.InboundEmailProcessor;
import com.clerova.storage.FileStorage;
import com.clerova.storage.FileStorageException;
import com.clerova.trucking.BrokerCheck;
import com.clerova.trucking.FmcsaClient;
import com.clerova.trucking.FmcsaStatus;
import com.clerova.trucking.RateConResult;
import com.clerova.trucking.TruckingAiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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

import static com.clerova.WorkItemWorkflowTest.userToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canadian carriers: country at sign-up, each load in its rate con's currency, Canadian brokers. */
@SpringBootTest(properties = "app.email.postmark-server-token=test-token")
@AutoConfigureMockMvc
class TruckingCanadaTest {

    private static final String WEBHOOK_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("postmark:webhook-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InboundEmailProcessor processor;

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
        when(fmcsaClient.check(anyString()))
                .thenReturn(Optional.of(new BrokerCheck(FmcsaStatus.ACTIVE, "NORTHERN LANES LOGISTICS", null)));
    }

    @Test
    void aCanadianCarrierSignsUpAndOnlyTruckingCanBeCanadian() throws Exception {
        RequestPostProcessor carrier = signUp("Prairie Haul Ltd", "TRUCKING", "CA");
        mockMvc.perform(get("/api/me").with(carrier)).andExpect(jsonPath("$.organization.country").value("CA"));

        RequestPostProcessor american = signUp("Sierra Run Transport", "TRUCKING", null);
        mockMvc.perform(get("/api/me").with(american)).andExpect(jsonPath("$.organization.country").value("US"));

        RequestPostProcessor manager = signUp("Maple PM", "PROPERTY_MANAGEMENT", "CA");
        mockMvc.perform(get("/api/me").with(manager)).andExpect(jsonPath("$.organization.country").value("US"));
    }

    @Test
    void eachLoadIsInItsRateConsCurrencyAndCanadianBrokersGetALookupNote() throws Exception {
        RequestPostProcessor carrier = signUp("Prairie Haul Ltd", "TRUCKING", "CA");
        String address = json(mockMvc.perform(get("/api/email-settings").with(carrier))).get("forwardingAddress").asText();

        // A Canadian broker's rate con in CAD, with no US MC
        RateConResult canadian = rateCon("Prairie Freight Brokers Inc", null, "PF-5521", "CAD", "Calgary, AB T2C 4M5", "Edmonton, AB T6E 5A1");
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(canadian);
        deliver(address, "<ca1@x>", "Rate Confirmation PF-5521", "dispatch@prairiefreight.example");
        processor.processDue();

        JsonNode loads = json(mockMvc.perform(get("/api/loads").with(carrier)));
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loads.get(0).get("id").asText()).with(carrier)));
        assertThat(load.get("currency").asText()).isEqualTo("CAD");
        assertThat(loads.get(0).get("currency").asText()).isEqualTo("CAD");
        assertThat(codes(load)).contains("CANADIAN_BROKER").doesNotContain("MC_MISSING");

        // A US broker's cross-border load in USD, with an MC: checked with FMCSA as usual
        RateConResult american = rateCon("Northern Lanes Logistics", "845210", "NL-77", "USD", "Calgary, AB", "Spokane, WA");
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(american);
        deliver(address, "<ca2@x>", "Rate Confirmation NL-77", "loads@northernlanes.example");
        processor.processDue();

        JsonNode cross = byNumber(carrier, "NL-77");
        assertThat(cross.get("currency").asText()).isEqualTo("USD");
        assertThat(codes(cross)).doesNotContain("CANADIAN_BROKER", "MC_MISSING");

        // A rate con that only shows "$": the carrier's home currency
        RateConResult unclear = rateCon("Prairie Freight Brokers Inc", null, "PF-5600", "", "Red Deer, AB", "Lethbridge, AB");
        when(truckingAiService.readRateCon(anyString(), anyString(), anyList())).thenReturn(unclear);
        deliver(address, "<ca3@x>", "Rate Confirmation PF-5600", "dispatch@prairiefreight.example");
        processor.processDue();
        assertThat(byNumber(carrier, "PF-5600").get("currency").asText()).isEqualTo("CAD");

        // Typed in by hand: CAD unless chosen otherwise
        JsonNode byHand = json(mockMvc.perform(post("/api/loads").with(carrier).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"Foothills Brokerage\",\"rate\":\"1800\","
                                + "\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"Airdrie, AB\"},{\"kind\":\"DELIVERY\",\"place\":\"Banff, AB\"}]}"))
                .andExpect(status().isOk()));
        assertThat(byHand.get("currency").asText()).isEqualTo("CAD");
        assertThat(codes(byHand)).containsExactly("CANADIAN_BROKER");
        JsonNode inUsd = json(mockMvc.perform(post("/api/loads").with(carrier).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"Foothills Brokerage\",\"loadNumber\":\"FB-2\",\"rate\":\"900\",\"currency\":\"USD\","
                                + "\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"Airdrie, AB\"},{\"kind\":\"DELIVERY\",\"place\":\"Great Falls, MT\"}]}"))
                .andExpect(status().isOk()));
        assertThat(inUsd.get("currency").asText()).isEqualTo("USD");

        // Invoices carry the load's currency; the spreadsheet totals each currency on its own
        mockMvc.perform(put("/api/carrier").with(carrier).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"legalName\":\"Prairie Haul Ltd\",\"mcNumber\":\"1200300\",\"address\":\"10 Ave SE\\nCalgary, AB T2G 0K1\","
                                + "\"email\":\"billing@prairiehaul.example\",\"invoicePrefix\":\"PH-\",\"nextInvoiceNumber\":100,\"usesFactoring\":false}"))
                .andExpect(status().isOk());
        JsonNode cadInvoice = json(mockMvc.perform(post("/api/loads/{id}/invoice", load.get("id").asText()).with(carrier)));
        JsonNode usdInvoice = json(mockMvc.perform(post("/api/loads/{id}/invoice", cross.get("id").asText()).with(carrier)));
        assertThat(cadInvoice.get("currency").asText()).isEqualTo("CAD");
        assertThat(usdInvoice.get("currency").asText()).isEqualTo("USD");
        mockMvc.perform(post("/api/invoices/{id}/mark-sent", cadInvoice.get("id").asText()).with(carrier)).andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/{id}/mark-sent", usdInvoice.get("id").asText()).with(carrier)).andExpect(status().isOk());

        String csv = mockMvc.perform(get("/api/invoices/export").param("month", java.time.YearMonth.now(java.time.ZoneId.of("America/Los_Angeles")).toString()).with(carrier))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("Currency").contains("Total CAD").contains("Total USD");
    }

    @Test
    void aUsCarriersBrokerWithoutAnMcStillNeedsOne() throws Exception {
        RequestPostProcessor carrier = signUp("Sierra Run Transport", "TRUCKING", null);
        JsonNode load = json(mockMvc.perform(post("/api/loads").with(carrier).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brokerName\":\"Valley Brokerage\","
                                + "\"stops\":[{\"kind\":\"PICKUP\",\"place\":\"Visalia, CA\"},{\"kind\":\"DELIVERY\",\"place\":\"Reno, NV\"}]}"))
                .andExpect(status().isOk()));
        assertThat(load.get("currency").asText()).isEqualTo("USD");
        assertThat(codes(load)).containsExactly("MC_MISSING");
    }

    private RequestPostProcessor signUp(String name, String industry, String country) throws Exception {
        RequestPostProcessor user = userToken(UUID.randomUUID());
        mockMvc.perform(post("/api/onboarding").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"" + name + "\",\"industry\":\"" + industry + "\""
                                + (country == null ? "" : ",\"country\":\"" + country + "\"") + "}"))
                .andExpect(status().isCreated());
        return user;
    }

    private static RateConResult rateCon(String broker, String mc, String number, String currency, String from, String to) {
        RateConResult read = new RateConResult();
        read.isRateConfirmation = true;
        read.documentKind = "RATE_CON";
        read.brokerName = broker;
        read.brokerMcNumber = mc;
        read.loadNumber = number;
        read.currency = currency;
        read.lineHaulRate = "2400.00";
        read.detentionFreeHours = "2";
        read.detentionRatePerHour = "75";
        read.tonuAmount = "250";
        RateConResult.Stop pickup = new RateConResult.Stop();
        pickup.kind = "PICKUP";
        pickup.place = from;
        RateConResult.Stop delivery = new RateConResult.Stop();
        delivery.kind = "DELIVERY";
        delivery.place = to;
        read.stops.add(pickup);
        read.stops.add(delivery);
        return read;
    }

    private JsonNode byNumber(RequestPostProcessor carrier, String number) throws Exception {
        for (JsonNode summary : json(mockMvc.perform(get("/api/loads").with(carrier)))) {
            if (number.equals(summary.get("loadNumber").asText())) {
                return json(mockMvc.perform(get("/api/loads/{id}", summary.get("id").asText()).with(carrier)));
            }
        }

        throw new AssertionError("No load " + number);
    }

    private static List<String> codes(JsonNode load) {
        List<String> codes = new ArrayList<>();
        load.get("flags").forEach(flag -> codes.add(flag.get("code").asText()));
        return codes;
    }

    private void deliver(String to, String messageId, String subject, String from) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", from);
        root.putObject("FromFull").put("Email", from).put("Name", "Dispatch");
        root.putArray("ToFull").addObject().put("Email", "dispatch@prairiehaul.example");
        root.put("OriginalRecipient", to);
        root.put("Subject", subject);
        root.put("TextBody", "Rate con attached.");
        root.putArray("Headers").addObject().put("Name", "Message-ID").put("Value", messageId);
        root.putArray("Attachments").addObject()
                .put("Name", "RateCon.pdf")
                .put("ContentType", "application/pdf")
                .put("Content", Base64.getEncoder().encodeToString(textPdf(subject)));

        mockMvc.perform(post("/api/inbound/postmark")
                        .header("Authorization", WEBHOOK_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isOk());
    }

    private static byte[] textPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                stream.showText(text + " - rate confirmation");
                stream.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}

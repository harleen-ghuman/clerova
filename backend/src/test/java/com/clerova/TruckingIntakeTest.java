package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.email.InboundEmailProcessor;
import com.clerova.trucking.BrokerCheck;
import com.clerova.trucking.FmcsaClient;
import com.clerova.trucking.FmcsaStatus;
import com.clerova.trucking.RateConResult;
import com.clerova.trucking.TruckingAiService;
import com.clerova.trucking.TruckingIntakeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Optional;
import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static com.clerova.WorkItemWorkflowTest.userToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TruckingIntakeTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor user;
    private String forwardingAddress;

    @BeforeEach
    void setUp() throws Exception {
        user = userToken(UUID.randomUUID());

        mockMvc.perform(post("/api/onboarding")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Sierra Run Transport","industry":"TRUCKING"}
                                """))
                .andExpect(status().isCreated());

        forwardingAddress = json(mockMvc.perform(get("/api/email-settings").with(user)))
                .get("forwardingAddress").asText();

        when(fmcsaClient.check(anyString()))
                .thenReturn(Optional.of(new BrokerCheck(FmcsaStatus.ACTIVE, "CRESTLINE FREIGHT LLC", null)));
    }

    @Test
    void rateConBecomesALoadWithThingsToCheck() throws Exception {
        when(truckingAiService.readRateCon(anyString(), anyString())).thenReturn(rateCon("2150.00", "2250.00"));

        deliver("<rc-1@x>", "Rate Confirmation 88213", "Confirming 88213, $2,250 all in.", TruckingIntakeTestPdf.pdf("RATE CONFIRMATION 88213"));
        processor.processDue();

        // The AI read the PDF's own text
        ArgumentCaptor<String> document = ArgumentCaptor.forClass(String.class);
        verify(truckingAiService).readRateCon(anyString(), document.capture());
        assertThat(document.getValue()).contains("RATE CONFIRMATION 88213");

        JsonNode loads = json(mockMvc.perform(get("/api/loads").with(user)).andExpect(status().isOk()));
        assertThat(loads).hasSize(1);
        assertThat(loads.get(0).get("loadNumber").asText()).isEqualTo("88213");
        assertThat(loads.get(0).get("brokerName").asText()).isEqualTo("Crestline Freight");
        assertThat(loads.get(0).get("origin").asText()).contains("Fresno");
        assertThat(loads.get(0).get("destination").asText()).contains("Phoenix");
        assertThat(loads.get(0).get("rateCents").asLong()).isEqualTo(215000);

        String loadId = loads.get(0).get("id").asText();
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)).andExpect(status().isOk()));

        assertThat(codes(load)).containsExactlyInAnyOrder("RATE_MISMATCH", "DETENTION_CONDITION", "PAPERWORK_FINE");
        assertThat(load.get("detentionFreeMinutes").asInt()).isEqualTo(120);
        assertThat(load.get("detentionRateCents").asLong()).isEqualTo(5000);
        assertThat(load.get("broker").get("mcNumber").asText()).isEqualTo("845210");
        assertThat(load.get("broker").get("fmcsaStatus").asText()).isEqualTo("ACTIVE");
        assertThat(load.get("rateConFile").asText()).isEqualTo("ratecon.pdf");

        JsonNode mismatch = flag(load, "RATE_MISMATCH");
        assertThat(mismatch.get("title").asText()).isEqualTo("Rate is $100.00 lower than the email says");
        assertThat(mismatch.get("level").asText()).isEqualTo("FIX");

        // The email is filed under the load, and the new load counted once
        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));
        assertThat(inbox.get(0).get("status").asText()).isEqualTo("PROCESSED");
        assertThat(usedThisMonth()).isEqualTo(1);

        // "It's fine" on a flag
        mockMvc.perform(post("/api/loads/{id}/flags/{flagId}/resolve", loadId, flag(load, "PAPERWORK_FINE").get("id").asText())
                        .with(user))
                .andExpect(status().isOk());
        load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(flag(load, "PAPERWORK_FINE").get("resolvedAt").isNull()).isFalse();
    }

    @Test
    void correctedRateConUpdatesTheSameLoad() throws Exception {
        when(truckingAiService.readRateCon(anyString(), anyString()))
                .thenReturn(rateCon("2150.00", "2250.00"), rateCon("2250.00", ""));

        deliver("<rc-a@x>", "Rate Confirmation 88213", "Rate con attached.", TruckingIntakeTestPdf.pdf("RATE CONFIRMATION 88213"));
        processor.processDue();
        String loadId = json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText();
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));

        // Checked once; stays checked on the new copy
        mockMvc.perform(post("/api/loads/{id}/flags/{flagId}/resolve", loadId,
                flag(load, "DETENTION_CONDITION").get("id").asText()).with(user)).andExpect(status().isOk());

        deliver("<rc-b@x>", "Revised: Rate Confirmation 88213", "Corrected rate con attached.", TruckingIntakeTestPdf.pdf("REVISED 88213"));
        processor.processDue();

        JsonNode loads = json(mockMvc.perform(get("/api/loads").with(user)));
        assertThat(loads).hasSize(1);
        load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(load.get("rateCents").asLong()).isEqualTo(225000);
        assertThat(codes(load)).doesNotContain("RATE_MISMATCH");
        assertThat(flag(load, "DETENTION_CONDITION").get("resolvedAt").isNull()).isFalse();

        // A revised copy doesn't count again
        assertThat(usedThisMonth()).isEqualTo(1);
        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));
        assertThat(inbox.get(0).get("statusReason").asText()).isEqualTo(TruckingIntakeService.REVISED_REASON);
    }

    @Test
    void emailsWithoutARateConAreSetAsideWithoutTheAi() throws Exception {
        deliver("<status@x>", "Where's the truck on LX-5521?", "Receiver is asking.", null);
        processor.processDue();

        verify(truckingAiService, never()).readRateCon(anyString(), anyString());
        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));
        assertThat(inbox.get(0).get("status").asText()).isEqualTo("IGNORED");
        assertThat(inbox.get(0).get("statusReason").asText()).isEqualTo(TruckingIntakeService.NOT_A_RATE_CON_REASON);
        assertThat(usedThisMonth()).isZero();
        // And never as a property management request
        verify(aiService, never()).extract(anyString());
    }

    @Test
    void aPdfThatIsntARateConIsNotCounted() throws Exception {
        RateConResult notARateCon = new RateConResult();
        notARateCon.isRateConfirmation = false;
        when(truckingAiService.readRateCon(anyString(), anyString())).thenReturn(notARateCon);

        deliver("<inv@x>", "Invoice 1042", "Invoice attached.", TruckingIntakeTestPdf.pdf("INVOICE 1042"));
        processor.processDue();

        verify(truckingAiService, times(1)).readRateCon(anyString(), anyString());
        assertThat(json(mockMvc.perform(get("/api/loads").with(user)))).isEmpty();
        assertThat(usedThisMonth()).isZero();
    }

    @Test
    void fmcsaProblemsAreFlaggedAsThingsToFix() throws Exception {
        when(fmcsaClient.check(anyString()))
                .thenReturn(Optional.of(new BrokerCheck(FmcsaStatus.ACTIVE, "SOMEONE ELSE LOGISTICS INC", null)));
        RateConResult read = rateCon("1890.00", "");
        read.brokerMcNumber = "MC# 1610032";
        when(truckingAiService.readRateCon(anyString(), anyString())).thenReturn(read);

        deliver("<rc-n@x>", "RC 0457", "Please sign.", TruckingIntakeTestPdf.pdf("RATE CONFIRMATION 0457"));
        processor.processDue();

        String loadId = json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText();
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}", loadId).with(user)));
        assertThat(codes(load)).contains("BROKER_NAME_MISMATCH");
        assertThat(load.get("broker").get("mcNumber").asText()).isEqualTo("1610032");
    }

    @Test
    void loadsBelongToTheirOwnWorkspace() throws Exception {
        when(truckingAiService.readRateCon(anyString(), anyString())).thenReturn(rateCon("2150.00", ""));
        deliver("<rc-t@x>", "Rate Confirmation 88213", "Attached.", TruckingIntakeTestPdf.pdf("RATE CONFIRMATION"));
        processor.processDue();
        JsonNode load = json(mockMvc.perform(get("/api/loads/{id}",
                json(mockMvc.perform(get("/api/loads").with(user))).get(0).get("id").asText()).with(user)));

        RequestPostProcessor other = onboardNewUser(mockMvc);
        assertThat(json(mockMvc.perform(get("/api/loads").with(other)))).isEmpty();
        mockMvc.perform(get("/api/loads/{id}", load.get("id").asText()).with(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/loads/{id}/flags/{flagId}/resolve", load.get("id").asText(),
                        load.get("flags").get(0).get("id").asText()).with(other))
                .andExpect(status().isNotFound());
    }

    private static RateConResult rateCon(String rate, String rateInEmail) {
        RateConResult read = new RateConResult();
        read.isRateConfirmation = true;
        read.brokerName = "Crestline Freight";
        read.brokerMcNumber = "MC-845210";
        read.brokerEmail = "Dana@Crestline.example";
        read.loadNumber = "88213";
        read.equipment = "Dry van 53'";
        read.lineHaulRate = rate;
        read.rateQuote = "Line haul ........ $" + rate;
        read.rateInEmail = rateInEmail;
        read.stops = new ArrayList<>(List.of(
                stop("PICKUP", "Sunridge Packing, 1200 S Elm Ave, Fresno, CA", "Nov 4, 8:00-10:00 AM"),
                stop("DELIVERY", "Desert Foods DC, Phoenix, AZ", "Nov 5, 7:00 AM")));
        read.detentionFreeHours = "2";
        read.detentionRatePerHour = "50.00";
        read.detentionCondition = "Tell the broker within 30 minutes of arrival";
        read.detentionQuote = "Detention $50/hr after 2 hrs free. Carrier must notify broker within 30 min of arrival.";
        read.latePaperworkFine = "100";
        read.paperworkDeadline = "POD within 24 hours of delivery";
        read.paperworkQuote = "$100 deduction for POD received > 24 hrs after delivery.";
        read.paymentTerms = "Net 30";
        read.quickPayFeePercent = "";
        return read;
    }

    private static RateConResult.Stop stop(String kind, String place, String appointment) {
        RateConResult.Stop stop = new RateConResult.Stop();
        stop.kind = kind;
        stop.place = place;
        stop.appointment = appointment;
        return stop;
    }

    private void deliver(String messageId, String subject, String body, String pdfBase64) throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", "dana@crestline.example");
        root.putObject("FromFull").put("Email", "dana@crestline.example").put("Name", "Dana Ruiz");
        root.putArray("ToFull").addObject().put("Email", "dispatch@sierrarun.example");
        root.put("OriginalRecipient", forwardingAddress);
        root.put("Subject", subject);
        root.put("TextBody", body);
        root.putArray("Headers").addObject().put("Name", "Message-ID").put("Value", messageId);
        var attachments = root.putArray("Attachments");

        if (pdfBase64 != null) {
            attachments.addObject()
                    .put("Name", "ratecon.pdf")
                    .put("ContentType", "application/pdf")
                    .put("Content", pdfBase64);
        }

        mockMvc.perform(post("/api/inbound/postmark")
                        .header("Authorization", WEBHOOK_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(root)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    private int usedThisMonth() throws Exception {
        return json(mockMvc.perform(get("/api/me").with(user))).get("organization").get("aiRequestsUsedThisMonth").asInt();
    }

    private static List<String> codes(JsonNode load) {
        List<String> codes = new ArrayList<>();
        load.get("flags").forEach(flag -> codes.add(flag.get("code").asText()));
        return codes;
    }

    private static JsonNode flag(JsonNode load, String code) {
        for (JsonNode flag : load.get("flags")) {
            if (flag.get("code").asText().equals(code)) {
                return flag;
            }
        }

        throw new AssertionError("No flag " + code + " in " + load);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}

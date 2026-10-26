package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.domain.ActionType;
import com.clerova.domain.Category;
import com.clerova.domain.Priority;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.clerova.email.EmailSender;
import com.clerova.email.InboundEmailProcessor;
import com.clerova.email.OutgoingEmail;
import com.clerova.exception.AiServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.email.postmark-server-token=test-token")
@AutoConfigureMockMvc
class EmailIntakeTest {

    private static final String WEBHOOK_AUTH = "Basic " + Base64.getEncoder()
            .encodeToString("postmark:webhook-secret".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InboundEmailProcessor processor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private EmailSender emailSender;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor user;
    private String forwardingAddress;

    @BeforeEach
    void setUp() throws Exception {
        ExtractionResult extraction = new ExtractionResult();
        extraction.category = Category.MAINTENANCE;
        extraction.priority = Priority.HIGH;
        extraction.summary = "Leaking sink";
        extraction.address = "12 Oak St";
        when(aiService.extract(anyString())).thenReturn(extraction);

        proposeAction(ActionType.REQUEST_MORE_INFORMATION);

        when(emailSender.send(any())).thenReturn("postmark-out-1");

        user = onboardNewUser(mockMvc);

        forwardingAddress = json(mockMvc.perform(get("/api/email-settings").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeEnabled").value(true))
                .andExpect(jsonPath("$.sendingEnabled").value(true)))
                .get("forwardingAddress").asText();
    }

    private void proposeAction(ActionType actionType) {
        ProposedActionResult result = new ProposedActionResult();
        result.actionType = actionType;
        result.reasoning = "Reasoning";
        result.draftedResponse = "Thanks, which unit are you in?";
        when(aiService.proposeAction(any(), any())).thenReturn(result);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private String payload(
            String to,
            String messageId,
            String inReplyTo,
            String extraHeader,
            String from,
            String body
    ) throws Exception {
        var root = objectMapper.createObjectNode();

        root.put("MessageID", UUID.randomUUID().toString());
        root.put("From", from);
        root.putObject("FromFull").put("Email", from).put("Name", "Tenant Tina");
        root.putArray("ToFull").addObject().put("Email", to);
        root.put("OriginalRecipient", to);
        root.put("Subject", "Sink leaking");
        root.put("TextBody", body);
        root.put("StrippedTextReply", "");
        root.put("Date", "Mon, 26 Oct 2026 09:30:00 +0000");

        var headers = root.putArray("Headers");
        headers.addObject().put("Name", "Message-ID").put("Value", messageId);

        if (inReplyTo != null) {
            headers.addObject().put("Name", "In-Reply-To").put("Value", inReplyTo);
        }

        if (extraHeader != null) {
            headers.addObject().put("Name", extraHeader).put("Value", "yes");
        }

        root.putArray("Attachments").addObject()
                .put("Name", "photo.jpg")
                .put("ContentType", "image/jpeg")
                .put("ContentLength", 1234)
                .put("Content", "aGVsbG8=");

        return objectMapper.writeValueAsString(root);
    }

    private ResultActions deliver(String payload) throws Exception {
        return mockMvc.perform(post("/api/inbound/postmark")
                .header("Authorization", WEBHOOK_AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private String deliverNew(String messageId, String body) throws Exception {
        deliver(payload(forwardingAddress, messageId, null, null, "tina@example.com", body))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));

        for (JsonNode message : inbox) {
            if (message.get("workItemId") != null && !message.get("workItemId").isNull()) {
                return message.get("workItemId").asText();
            }
        }

        throw new AssertionError("No work item created: " + inbox);
    }

    private JsonNode latestAction(String workItemId) throws Exception {
        return json(mockMvc.perform(get("/api/work-items/{id}/proposed-actions", workItemId).with(user)))
                .get(0);
    }

    @Test
    void webhookRequiresCredentials() throws Exception {
        String body = payload(forwardingAddress, "<a@x>", null, null, "tina@example.com", "Hi");

        mockMvc.perform(post("/api/inbound/postmark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/inbound/postmark")
                        .header("Authorization", "Basic " + Base64.getEncoder()
                                .encodeToString("postmark:wrong".getBytes(StandardCharsets.UTF_8)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRecipientAndDuplicatesAreSkipped() throws Exception {
        deliver(payload("nobody@in.test.clerova", "<u@x>", null, null, "tina@example.com", "Hi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN_RECIPIENT"));

        String body = payload(forwardingAddress, "<dup@x>", null, null, "tina@example.com", "Hi");

        deliver(body).andExpect(jsonPath("$.status").value("STORED"));
        deliver(body).andExpect(jsonPath("$.status").value("DUPLICATE"));
    }

    @Test
    void newEmailBecomesWorkItemWithAutomaticProposal() throws Exception {
        String workItemId = deliverNew("<new@x>", "The kitchen sink is leaking at 12 Oak St.");

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.source").value("EMAIL"))
                .andExpect(jsonPath("$.senderEmail").value("tina@example.com"))
                .andExpect(jsonPath("$.senderName").value("Tenant Tina"))
                .andExpect(jsonPath("$.subject").value("Sink leaking"))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"));

        assertThat(latestAction(workItemId).get("status").asText()).isEqualTo("PENDING_APPROVAL");

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"))
                .andExpect(jsonPath("$[0].attachments[0]").value("photo.jpg"));

        mockMvc.perform(get("/api/work-items/{id}/events", workItemId).with(user))
                .andExpect(jsonPath("$[0].type").value("CREATED"))
                .andExpect(jsonPath("$[0].actor").isEmpty())
                .andExpect(jsonPath("$[1].type").value("PROPOSAL_GENERATED"));

        // The extraction saw the sender and subject
        ArgumentCaptor<String> extractionInput = ArgumentCaptor.forClass(String.class);
        verify(aiService).extract(extractionInput.capture());
        assertThat(extractionInput.getValue())
                .contains("tina@example.com")
                .contains("Subject: Sink leaking");
    }

    @Test
    void automatedEmailIsIgnoredUntilRestored() throws Exception {
        deliver(payload(forwardingAddress, "<news@x>", null, "List-Unsubscribe", "news@vendor.com", "Sale!"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        JsonNode message = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);

        assertThat(message.get("status").asText()).isEqualTo("IGNORED");
        assertThat(message.get("statusReason").asText()).isEqualTo("Automated or bulk email");
        verify(aiService, never()).extract(anyString());

        mockMvc.perform(post("/api/inbox/{id}/reprocess", message.get("id").asText()).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"));
    }

    @Test
    void gmailForwardingConfirmationIsShownInSettings() throws Exception {
        deliver(payload(forwardingAddress, "<gmail@x>", null, null,
                "forwarding-noreply@google.com", "Confirmation code: 123456789"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/email-settings").with(user))
                .andExpect(jsonPath("$.gmailConfirmation.text").value("Confirmation code: 123456789"));
    }

    @Test
    void replyToInformationRequestReopensWorkItemWithConversation() throws Exception {
        String workItemId = deliverNew("<first@x>", "My sink is leaking.");

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_INFORMATION"));

        // The tenant replies in the same thread
        deliver(payload(forwardingAddress, "<second@x>", "<first@x>", null, "tina@example.com", "Unit 4B."))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("ATTACHED"))
                .andExpect(jsonPath("$[0].workItemId").value(workItemId));

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"));

        mockMvc.perform(get("/api/work-items/{id}/conversation", workItemId).with(user))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].text").value("Unit 4B."));

        // The new proposal was generated with the conversation as context
        verify(aiService).proposeAction(any(), notNull());
    }

    @Test
    void replyToRecentlyClosedRequestReopensIt() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String workItemId = deliverNew("<closed1@x>", "When is rent due?");

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        deliver(payload(forwardingAddress, "<closed2@x>", "<closed1@x>", null,
                "tina@example.com", "Thanks, and is there a grace period?"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("ATTACHED"))
                .andExpect(jsonPath("$[0].workItemId").value(workItemId));

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"));

        JsonNode events = json(mockMvc.perform(get("/api/work-items/{id}/events", workItemId).with(user)));
        assertThat(events.findValuesAsText("type")).contains("REOPENED", "FOLLOW_UP_RECEIVED");
    }

    @Test
    void replyToLongClosedRequestStartsNewRequest() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String workItemId = deliverNew("<old1@x>", "When is rent due?");

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "update work_items set updated_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(30))),
                UUID.fromString(workItemId)
        );

        deliver(payload(forwardingAddress, "<old2@x>", "<old1@x>", null,
                "tina@example.com", "New issue: the heater is broken."))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        JsonNode reply = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);

        assertThat(reply.get("status").asText()).isEqualTo("PROCESSED");
        assertThat(reply.get("workItemId").asText()).isNotEqualTo(workItemId);
    }

    @Test
    void replyToClerovaAddressThreadsByMailboxHash() throws Exception {
        String workItemId = deliverNew("<hash1@x>", "Heater is broken.");

        String replyAddress = forwardingAddress.replace("@",
                "+wi-" + workItemId.replace("-", "") + "@");

        // No threading headers at all
        deliver(payload(replyAddress, "<hash2@x>", null, null, "tina@example.com", "Any update?"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("ATTACHED"))
                .andExpect(jsonPath("$[0].workItemId").value(workItemId));

        // The pending proposal did not see the new message, so it was replaced
        mockMvc.perform(get("/api/work-items/{id}/proposed-actions", workItemId).with(user))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$[1].status").value("REJECTED"));
    }

    @Test
    void approvedResponseIsSentAsThreadedReplyOnce() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String workItemId = deliverNew("<send1@x>", "When is rent due?");
        String actionId = latestAction(workItemId).get("id").asText();

        // Must be approved first
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/send-reply", actionId).with(user))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve", actionId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finalResponse":"Rent is due on the 1st."}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/send-reply", actionId).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"));

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(sent.capture());

        assertThat(sent.getValue().to()).isEqualTo("tina@example.com");
        assertThat(sent.getValue().subject()).isEqualTo("Re: Sink leaking");
        assertThat(sent.getValue().textBody()).isEqualTo("Rent is due on the 1st.");
        assertThat(sent.getValue().inReplyTo()).isEqualTo("<send1@x>");
        assertThat(sent.getValue().replyTo())
                .isEqualTo(forwardingAddress.replace("@", "+wi-" + workItemId.replace("-", "") + "@"));
        assertThat(sent.getValue().from()).contains("via Clerova").contains(forwardingAddress);

        // A second click does not send again
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/send-reply", actionId).with(user))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/work-items/{id}/events", workItemId).with(user))
                .andExpect(jsonPath("$[3].type").value("REPLY_SENT"))
                .andExpect(jsonPath("$[3].actor").isNotEmpty());
    }

    @Test
    void aiFailureIsRetriedLater() throws Exception {
        when(aiService.extract(anyString())).thenThrow(new AiServiceException("down", null));

        deliver(payload(forwardingAddress, "<retry@x>", null, null, "tina@example.com", "Help"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        // Back in the queue but not due yet
        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"));

        assertThat(processor.processDue()).isZero();
    }

    @Test
    void failureReasonInTheInboxIsPlainAndHidesProviderErrors() throws Exception {
        when(aiService.extract(anyString())).thenThrow(new AiServiceException(
                "Failed to process AI request",
                new RuntimeException("401: Incorrect API key provided")
        ));

        deliver(payload(forwardingAddress, "<why@x>", null, null, "tina@example.com", "Help"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("RECEIVED"))
                .andExpect(jsonPath("$[0].statusReason").value(
                        "Couldn't read this email yet. Clerova will try again in a few minutes."));
    }

    @Test
    void ownerIsWarnedAtEightyPercentAndAtTheLimitOnce() throws Exception {
        String organizationId = json(mockMvc.perform(get("/api/me").with(user)))
                .get("organization").get("id").asText();

        jdbcTemplate.update(
                "update organizations set ai_monthly_limit = 5 where id = ?",
                UUID.fromString(organizationId)
        );

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/work-items")
                    .with(user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"Leak " + i + "\"}"));
        }

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender, org.mockito.Mockito.atLeastOnce()).send(sent.capture());

        List<String> subjects = sent.getAllValues().stream().map(OutgoingEmail::subject).toList();

        assertThat(subjects).containsOnlyOnce(
                "You've used 80% of your Clerova requests this month",
                "You've reached your Clerova requests for this month"
        );
        assertThat(sent.getAllValues().get(0).textBody()).contains("used 4 of the 5 requests");
    }

    @Test
    void quotaExhaustionMarksEmailFailed() throws Exception {
        String organizationId = json(mockMvc.perform(get("/api/me").with(user)))
                .get("organization").get("id").asText();

        jdbcTemplate.update(
                "update organizations set ai_monthly_limit = 0 where id = ?",
                UUID.fromString(organizationId)
        );

        deliver(payload(forwardingAddress, "<quota@x>", null, null, "tina@example.com", "Help"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].statusReason").value(
                        "You have used all requests included in your plan this month."));
    }

    @Test
    void inboxIsPrivateToEachOrganization() throws Exception {
        deliverNew("<private@x>", "Private request");

        RequestPostProcessor other = onboardNewUser(mockMvc);

        mockMvc.perform(get("/api/inbox").with(other))
                .andExpect(jsonPath("$").isEmpty());

        String otherAddress = json(mockMvc.perform(get("/api/email-settings").with(other)))
                .get("forwardingAddress").asText();

        assertThat(otherAddress).isNotEqualTo(forwardingAddress);
    }
}

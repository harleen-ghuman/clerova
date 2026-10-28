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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Autowired
    private com.clerova.repository.InboundMessageRepository inboundMessageRepository;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
        org.mockito.Mockito.doReturn(result).when(aiService).proposeAction(any(), any());
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
    void emptyEmailIsIgnoredAndPhotoOnlyEmailBecomesARequest() throws Exception {
        var empty = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                payload(forwardingAddress, "<empty@x>", null, null, "tina@example.com", ""));
        empty.put("Subject", "");
        empty.putArray("Attachments");
        deliver(empty.toString()).andExpect(jsonPath("$.status").value("STORED"));

        var photo = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                payload(forwardingAddress, "<photo@x>", null, null, "tina@example.com", ""));
        photo.put("Subject", "");
        deliver(photo.toString()).andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));
        JsonNode emptyMessage = inbox.get(0).get("subject").isNull() || inbox.get(0).get("attachments").isEmpty()
                ? inbox.get(0) : inbox.get(1);

        assertThat(emptyMessage.get("status").asText()).isEqualTo("IGNORED");

        // Only the photo email used a request
        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.aiRequestsUsedThisMonth").value(1));

        JsonNode item = json(mockMvc.perform(get("/api/work-items").with(user))).get(0);
        assertThat(item.get("originalMessage").asText())
                .isEqualTo("(No message text. Attachments: photo.jpg)");
    }

    @Test
    void emailForwardedByHandUsesTheResidentAsSender() throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                payload(forwardingAddress, "<fwd@x>", null, null, "pm@maple.com", """
                        Please handle

                        ---------- Forwarded message ---------
                        From: Tina Alvarez <tina.alvarez@gmail.com>
                        Subject: Kitchen sink leaking

                        Water is leaking under the sink.
                        """));
        root.put("Subject", "Fwd: Kitchen sink leaking");
        root.put("StrippedTextReply", "Please handle");

        deliver(root.toString()).andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        JsonNode item = json(mockMvc.perform(get("/api/work-items").with(user))).get(0);
        assertThat(item.get("senderEmail").asText()).isEqualTo("tina.alvarez@gmail.com");
        assertThat(item.get("senderName").asText()).isEqualTo("Tina Alvarez");
        assertThat(item.get("subject").asText()).isEqualTo("Kitchen sink leaking");

        // The AI read the resident's message, not just the forwarder's note
        ArgumentCaptor<String> extractionInput = ArgumentCaptor.forClass(String.class);
        verify(aiService).extract(extractionInput.capture());
        assertThat(extractionInput.getValue()).contains("Water is leaking under the sink.");
    }

    @Test
    void clerovaOwnEmailsAndBouncesDontBecomeRequests() throws Exception {
        deliver(payload(forwardingAddress, "<own@x>", null, null, "notifications@in.test.clerova", "Usage"))
                .andExpect(jsonPath("$.status").value("STORED"));
        deliver(payload(forwardingAddress, "<bounce@x>", null, null, "MAILER-DAEMON@gmail.com", "Undeliverable"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));

        assertThat(inbox).hasSize(2);
        inbox.forEach(message -> assertThat(message.get("status").asText()).isEqualTo("IGNORED"));
        verify(aiService, never()).extract(anyString());
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
    void emailDuringAJobIsFlaggedAndCanBeAnsweredOnTheSameRequest() throws Exception {
        proposeAction(ActionType.CREATE_MAINTENANCE_REQUEST);
        String workItemId = deliverNew("<job1@x>", "My sink is leaking.");

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.unreadReplyAt").isEmpty());

        // The tenant writes again while the job is underway
        deliver(payload(forwardingAddress, "<job2@x>", "<job1@x>", null,
                "tina@example.com", "Any update on the plumber?"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("ATTACHED"))
                .andExpect(jsonPath("$[0].workItemId").value(workItemId));

        // Same request, job untouched, now flagged
        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.unreadReplyAt").isNotEmpty());

        when(aiService.draftReply(any(), anyString())).thenReturn("Hi Tina, the team is on it.");

        mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi Tina, the team is on it."));

        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"   "}
                                """))
                .andExpect(status().isBadRequest());

        // Another workspace can't reach it
        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(onboardNewUser(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Hello"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Hi Tina, the plumber comes Friday."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.unreadReplyAt").isEmpty());

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(sent.capture());

        assertThat(sent.getValue().to()).isEqualTo("tina@example.com");
        assertThat(sent.getValue().textBody()).isEqualTo("Hi Tina, the plumber comes Friday.");
        assertThat(sent.getValue().inReplyTo()).isEqualTo("<job2@x>");
        assertThat(sent.getValue().replyTo())
                .isEqualTo(forwardingAddress.replace("@", "+wi-" + workItemId.replace("-", "") + "@"));

        mockMvc.perform(get("/api/work-items/{id}/conversation", workItemId).with(user))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].direction").value("OUTBOUND"));

        // A later email needing no answer can be dismissed
        deliver(payload(forwardingAddress, "<job3@x>", "<job2@x>", null,
                "tina@example.com", "Thanks!"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.unreadReplyAt").isNotEmpty());

        mockMvc.perform(post("/api/work-items/{id}/reply-handled", workItemId).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.unreadReplyAt").isEmpty());

        JsonNode events = json(mockMvc.perform(get("/api/work-items/{id}/events", workItemId).with(user)));
        assertThat(events.findValuesAsText("type")).contains("REPLY_SENT", "REPLY_HANDLED");

        // No new suggestion was made for these emails
        verify(aiService).proposeAction(any(), any());
    }

    @Test
    void writingToTheSenderNeedsAJobOrEscalation() throws Exception {
        String workItemId = deliverNew("<write1@x>", "My sink is leaking.");
        String message = """
                {"message":"Hello"}
                """;

        // A suggestion is waiting, so replies go through it
        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(user))
                .andExpect(status().isConflict());

        proposeAction(ActionType.ESCALATE_TO_HUMAN);
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/reject",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId).with(user))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        // Escalated: the team can write, and nothing about the request changes
        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));

        String organizationId = json(mockMvc.perform(get("/api/me").with(user)))
                .get("organization").get("id").asText();
        jdbcTemplate.update(
                "update organizations set trial_ends_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
                UUID.fromString(organizationId)
        );

        // An ended trial can still finish the request, but not use AI
        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content(message))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(user))
                .andExpect(status().isForbidden());

        verify(emailSender, org.mockito.Mockito.times(2)).send(any());
    }

    private int suggestionsLeft(String workItemId) throws Exception {
        return json(mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user)))
                .get("suggestionsLeft").asInt();
    }

    private ResultActions suggestAgain(String workItemId) throws Exception {
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/reject",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        return mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId).with(user));
    }

    @Test
    void newSuggestionsAreCappedPerEmail() throws Exception {
        String workItemId = deliverNew("<cap1@x>", "My sink is leaking.");

        // Clerova's own first suggestion doesn't count
        assertThat(suggestionsLeft(workItemId)).isEqualTo(5);

        // A failed call gives its turn back
        org.mockito.Mockito.doThrow(new RuntimeException("AI down")).when(aiService).proposeAction(any(), any());
        assertThat(suggestAgain(workItemId).andReturn().getResponse().getStatus()).isNotEqualTo(201);
        assertThat(suggestionsLeft(workItemId)).isEqualTo(5);
        proposeAction(ActionType.REQUEST_MORE_INFORMATION);

        // The failed call left the request without a suggestion
        mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId).with(user))
                .andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            suggestAgain(workItemId).andExpect(status().isCreated());
        }

        assertThat(suggestionsLeft(workItemId)).isZero();

        suggestAgain(workItemId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("all 5 new suggestions")));

        // The tenant writes again: a fresh set
        deliver(payload(forwardingAddress, "<cap2@x>", "<cap1@x>", null,
                "tina@example.com", "It's unit 4B."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        assertThat(suggestionsLeft(workItemId)).isEqualTo(5);
        suggestAgain(workItemId).andExpect(status().isCreated());
        assertThat(suggestionsLeft(workItemId)).isEqualTo(4);
    }

    @Test
    void draftsShareTheCapAndSendingStartsAFreshSet() throws Exception {
        String workItemId = inProgressRequest("<capjob1@x>");
        when(aiService.draftReply(any(), anyString())).thenReturn("Hi Tina, the team is on it.");

        for (int left = 4; left >= 0; left--) {
            mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(user))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.suggestionsLeft").value(left));
        }

        mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(user))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"The plumber comes Friday."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestionsLeft").value(5));

        // Another workspace can't use them up
        mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(onboardNewUser(mockMvc)))
                .andExpect(status().isNotFound());
        assertThat(suggestionsLeft(workItemId)).isEqualTo(5);
    }

    private ResultActions setReplyCopy(String json) throws Exception {
        return mockMvc.perform(put("/api/email-settings/reply-copy")
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void repliesCanBeCopiedToTheCompanysOwnInbox() throws Exception {
        String workItemId = inProgressRequest("<copy1@x>");
        String reply = """
                {"message":"The plumber comes Friday."}
                """;

        // Off until someone sets it up
        mockMvc.perform(get("/api/email-settings").with(user))
                .andExpect(jsonPath("$.replyCopyEmail").isEmpty());
        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content(reply))
                .andExpect(status().isOk());

        // Bad addresses are refused, including Clerova's own
        setReplyCopy("""
                {"email":"not an email"}
                """).andExpect(status().isBadRequest());
        setReplyCopy("{\"email\":\"" + forwardingAddress + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Use your own email here, not your Clerova address."));

        setReplyCopy("""
                {"email":"office@maple.example"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyCopyEmail").value("office@maple.example"))
                .andExpect(jsonPath("$.forwardingAddress").value(forwardingAddress));

        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content(reply))
                .andExpect(status().isOk());

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).bcc()).isNull();
        assertThat(sent.getAllValues().get(1).bcc()).isEqualTo("office@maple.example");
        assertThat(sent.getAllValues().get(1).to()).isEqualTo("tina@example.com");

        // Their inbox forwards everything, so the copy comes back: set aside,
        // and the request isn't flagged
        deliver(payload(forwardingAddress, "<copy-back@x>", "<copy1@x>", null,
                forwardingAddress, "The plumber comes Friday."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        JsonNode ownCopy = json(mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("IGNORED"))
                .andExpect(jsonPath("$[0].statusReason").value(InboundEmailProcessor.OWN_COPY_REASON)))
                .get(0);

        // "Process anyway" would make it a new reply from yourself
        mockMvc.perform(post("/api/inbox/{id}/reprocess", ownCopy.get("id").asText()).with(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This is your copy of an email sent through Clerova. There's nothing to process."));
        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.unreadReplyAt").isEmpty());

        // The tenant replies to Clerova's email: the company gets a copy
        String replyAddress = forwardingAddress.replace("@", "+wi-" + workItemId.replace("-", "") + "@");
        deliver(payload(replyAddress, "<copy3@x>", "<copy1@x>", null,
                "tina@example.com", "Friday works, thanks."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender, org.mockito.Mockito.times(3)).send(sent.capture());
        OutgoingEmail copy = sent.getAllValues().get(2);
        assertThat(copy.to()).isEqualTo("office@maple.example");
        assertThat(copy.replyTo()).isEqualTo("tina@example.com");
        assertThat(copy.from()).isEqualTo("\"Tenant Tina via Clerova\" <" + forwardingAddress + ">");
        assertThat(copy.subject()).isEqualTo("Sink leaking");
        assertThat(copy.inReplyTo()).isEqualTo("<copy3@x>");
        assertThat(copy.textBody())
                .contains("Tenant Tina <tina@example.com> replied to an email you sent through Clerova.")
                .contains("Friday works, thanks.")
                .contains("Attachments (open the request in Clerova to see them): photo.jpg");

        // Mail forwarded from their own inbox is already there: no copy
        deliver(payload(forwardingAddress, "<copy4@x>", "<copy3@x>", null,
                "tina@example.com", "One more thing."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();
        verify(emailSender, org.mockito.Mockito.times(3)).send(any());

        // Blank turns it off; other workspaces are unaffected
        setReplyCopy("""
                {"email":""}
                """).andExpect(jsonPath("$.replyCopyEmail").isEmpty());
        mockMvc.perform(get("/api/email-settings").with(onboardNewUser(mockMvc)))
                .andExpect(jsonPath("$.replyCopyEmail").isEmpty());
    }

    @Test
    void anEmailThatWaitedIsNotTakenForAStaleClaimWhileItRuns() throws Exception {
        deliver(payload(forwardingAddress, "<waited@x>", null, null, "tina@example.com", "Heater is broken."))
                .andExpect(jsonPath("$.status").value("STORED"));

        UUID id = jdbcTemplate.queryForObject(
                "select id from inbound_messages where message_id_header = '<waited@x>'", UUID.class);

        // It waited a day, e.g. for next month's requests
        jdbcTemplate.update("update inbound_messages set updated_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(1))), id);

        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        java.time.Instant now = java.time.Instant.now();

        Integer claimed = tx.execute(status -> inboundMessageRepository.claim(id, now));
        assertThat(claimed).isEqualTo(1);

        // Another server copy (e.g. during a deploy) must not release it
        Integer released = tx.execute(status ->
                inboundMessageRepository.releaseStale(now.minus(java.time.Duration.ofMinutes(10))));
        assertThat(released).isZero();
    }

    @Test
    void aRefusedCopyAddressNeverStopsTheReply() throws Exception {
        String workItemId = inProgressRequest("<copyfail1@x>");
        setReplyCopy("""
                {"email":"bounced@maple.example"}
                """).andExpect(status().isOk());

        // Postmark refuses anything with the copy, e.g. after it bounced
        when(emailSender.send(org.mockito.ArgumentMatchers.argThat(email -> email != null && email.bcc() != null)))
                .thenThrow(new com.clerova.email.EmailSendException("refused", null, true));

        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content("""
                                {"message":"The plumber comes Friday."}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender, org.mockito.Mockito.times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).bcc()).isEqualTo("bounced@maple.example");
        assertThat(sent.getAllValues().get(1).bcc()).isNull();
        assertThat(sent.getAllValues().get(1).to()).isEqualTo("tina@example.com");

        // A timeout isn't retried: the first email may already have gone out
        when(emailSender.send(org.mockito.ArgumentMatchers.argThat(email -> email != null && email.bcc() != null)))
                .thenThrow(new com.clerova.email.EmailSendException("timeout", null));

        mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user).contentType(MediaType.APPLICATION_JSON).content("""
                                {"message":"Any update?"}
                                """))
                .andExpect(status().isBadGateway());
        verify(emailSender, org.mockito.Mockito.times(3)).send(any());
    }

    @Test
    void copiesKeepThreadingHeadersShortAndOnOneLine() {
        String many = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "<m" + i + "@x>")
                .collect(java.util.stream.Collectors.joining("\r\n "));

        String references = com.clerova.email.ReplyCopyService.threadReferences(many, "<latest@x>");

        assertThat(references).doesNotContain("\r", "\n");
        assertThat(references.split(" ")).hasSize(10).endsWith("<latest@x>");
        assertThat(com.clerova.email.ReplyCopyService.threadReferences(null, null)).isNull();
    }

    private String inProgressRequest(String firstMessageId) throws Exception {
        proposeAction(ActionType.CREATE_MAINTENANCE_REQUEST);
        String workItemId = deliverNew(firstMessageId, "The AC is not cooling.");

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        return workItemId;
    }

    @Test
    void urgentEmailDuringAJobIsMarkedAndOwnersAreEmailed() throws Exception {
        String workItemId = inProgressRequest("<urgent1@x>");

        com.clerova.dto.UrgencyResult urgent = new com.clerova.dto.UrgencyResult();
        urgent.urgent = true;
        urgent.reason = "Resident smells gas near the stove.";
        when(aiService.checkUrgency(any(), anyString())).thenReturn(urgent);

        deliver(payload(forwardingAddress, "<urgent2@x>", "<urgent1@x>", null,
                "tina@example.com", "I smell gas near the stove."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.unreadReplyAt").isNotEmpty())
                .andExpect(jsonPath("$.unreadReplyUrgent").value(true));

        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(sent.capture());
        assertThat(sent.getValue().subject()).isEqualTo("Urgent: Tenant Tina wrote again about Sink leaking");
        assertThat(sent.getValue().textBody())
                .contains("Resident smells gas near the stove.")
                .contains("I smell gas near the stove.");

        JsonNode events = json(mockMvc.perform(get("/api/work-items/{id}/events", workItemId).with(user)));
        assertThat(events.findValuesAsText("detail")).contains("Marked urgent: Resident smells gas near the stove.");

        // Handled: the urgency goes with the flag
        mockMvc.perform(post("/api/work-items/{id}/reply-handled", workItemId).with(user))
                .andExpect(jsonPath("$.unreadReplyAt").isEmpty())
                .andExpect(jsonPath("$.unreadReplyUrgent").value(false));
    }

    @Test
    void ordinaryOrUncheckedEmailDuringAJobIsJustANewReply() throws Exception {
        String workItemId = inProgressRequest("<calm1@x>");

        com.clerova.dto.UrgencyResult calm = new com.clerova.dto.UrgencyResult();
        calm.urgent = false;
        when(aiService.checkUrgency(any(), anyString())).thenReturn(calm);

        deliver(payload(forwardingAddress, "<calm2@x>", "<calm1@x>", null,
                "tina@example.com", "Any update on when the technician comes?"))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.unreadReplyAt").isNotEmpty())
                .andExpect(jsonPath("$.unreadReplyUrgent").value(false));

        // The AI failing doesn't stop the email from being attached and flagged
        when(aiService.checkUrgency(any(), anyString()))
                .thenThrow(new AiServiceException("down", null));

        deliver(payload(forwardingAddress, "<calm3@x>", "<calm2@x>", null,
                "tina@example.com", "Still waiting."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("ATTACHED"));
        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.unreadReplyUrgent").value(false));

        verify(emailSender, never()).send(any());
    }

    @Test
    void aBurstOfFollowUpsStopsRunningTheAi() throws Exception {
        String workItemId = inProgressRequest("<burst0@x>");

        com.clerova.dto.UrgencyResult calm = new com.clerova.dto.UrgencyResult();
        when(aiService.checkUrgency(any(), anyString())).thenReturn(calm);

        for (int i = 1; i <= 14; i++) {
            deliver(payload(forwardingAddress, "<burst" + i + "@x>", "<burst0@x>", null,
                    "tina@example.com", "Follow-up number " + i))
                    .andExpect(jsonPath("$.status").value("STORED"));
        }

        processor.processDue();
        processor.processDue();

        // Every email is still attached to the request
        JsonNode inbox = json(mockMvc.perform(get("/api/inbox").with(user)));
        long attached = 0;
        for (JsonNode message : inbox) {
            if ("ATTACHED".equals(message.get("status").asText())) {
                attached++;
            }
        }
        assertThat(attached).isEqualTo(14);

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.unreadReplyAt").isNotEmpty());

        // The original plus 9 follow-ups ran the check; the rest were skipped
        verify(aiService, org.mockito.Mockito.times(9)).checkUrgency(any(), anyString());
    }

    @Test
    void customerWhoHasNotAnsweredCanBeReminded() throws Exception {
        String workItemId = deliverNew("<nudge1@x>", "My sink is leaking.");

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve",
                        latestAction(workItemId).get("id").asText()).with(user))
                .andExpect(status().isOk());

        // Asked a question four days ago
        jdbcTemplate.update(
                "update work_items set updated_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(4))),
                UUID.fromString(workItemId)
        );

        when(aiService.draftReply(any(), anyString())).thenReturn("Hi Tina, just checking in: which unit are you in?");

        mockMvc.perform(post("/api/work-items/{id}/draft-reply", workItemId).with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi Tina, just checking in: which unit are you in?"));

        JsonNode reminded = json(mockMvc.perform(post("/api/work-items/{id}/reply", workItemId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Hi Tina, just checking in: which unit are you in?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_INFORMATION")));

        // The wait starts again from the reminder
        assertThat(java.time.Instant.parse(reminded.get("updatedAt").asText()))
                .isAfter(java.time.Instant.now().minusSeconds(60));

        // Their answer goes back to review as usual
        deliver(payload(forwardingAddress, "<nudge2@x>", "<nudge1@x>", null, "tina@example.com", "Unit 4B."))
                .andExpect(jsonPath("$.status").value("STORED"));
        processor.processDue();

        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_APPROVAL"));
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

        // A failed attempt doesn't use up the plan
        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.aiRequestsUsedThisMonth").value(0));
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
    void emailWaitsWhenRequestsAreUsedUpAndGoesAfterAnUpgrade() throws Exception {
        String organizationId = json(mockMvc.perform(get("/api/me").with(user)))
                .get("organization").get("id").asText();

        jdbcTemplate.update(
                "update organizations set ai_monthly_limit = 0 where id = ?",
                UUID.fromString(organizationId)
        );

        deliver(payload(forwardingAddress, "<quota@x>", null, null, "tina@example.com", "Help"))
                .andExpect(jsonPath("$.status").value("STORED"));

        processor.processDue();

        // Waits for next month instead of failing
        JsonNode waiting = json(mockMvc.perform(get("/api/inbox").with(user))).get(0);
        assertThat(waiting.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(waiting.get("statusReason").asText()).contains("will be organized on");

        processor.processDue();
        assertThat(json(mockMvc.perform(get("/api/inbox").with(user))).get(0).get("status").asText())
                .isEqualTo("RECEIVED");

        // An upgrade raises the limit and releases waiting emails
        jdbcTemplate.update(
                "update organizations set ai_monthly_limit = 100 where id = ?",
                UUID.fromString(organizationId)
        );
        inboundMessageRepository.releaseWaiting(UUID.fromString(organizationId), java.time.Instant.now());
        processor.processDue();

        mockMvc.perform(get("/api/inbox").with(user))
                .andExpect(jsonPath("$[0].status").value("PROCESSED"))
                .andExpect(jsonPath("$[0].workItemId").isNotEmpty());
    }

    @Test
    void typedRequestOverTheLimitSaysWhatToDo() throws Exception {
        String organizationId = json(mockMvc.perform(get("/api/me").with(user)))
                .get("organization").get("id").asText();

        jdbcTemplate.update(
                "update organizations set ai_monthly_limit = 0 where id = ?",
                UUID.fromString(organizationId)
        );

        mockMvc.perform(post("/api/work-items")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Sink is leaking"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("AI_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Email ")));
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

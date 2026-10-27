package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.domain.ActionType;
import com.clerova.domain.Category;
import com.clerova.domain.Priority;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkItemWorkflowTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AiService aiService;

    /** A freshly onboarded user, so each test has its own organization. */
    private RequestPostProcessor user;

    static RequestPostProcessor userToken(UUID userId) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("email", userId + "@example.com"));
    }

    static RequestPostProcessor onboardNewUser(MockMvc mockMvc) throws Exception {
        RequestPostProcessor user = userToken(UUID.randomUUID());

        mockMvc.perform(post("/api/onboarding")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Acme","industry":"PROPERTY_MANAGEMENT"}
                                """))
                .andExpect(status().isCreated());

        return user;
    }

    @BeforeEach
    void setUp() throws Exception {
        user = onboardNewUser(mockMvc);

        ExtractionResult extraction = new ExtractionResult();
        extraction.category = Category.MAINTENANCE;
        extraction.priority = Priority.HIGH;
        extraction.summary = "Kitchen sink leak";
        extraction.address = "742 Evergreen Terrace";

        when(aiService.extract(anyString())).thenReturn(extraction);
        proposeAction(ActionType.CREATE_MAINTENANCE_REQUEST);
    }

    private void proposeAction(ActionType actionType) {
        ProposedActionResult result = new ProposedActionResult();
        result.actionType = actionType;
        result.reasoning = "Reasoning";
        result.draftedResponse = "AI draft";

        when(aiService.proposeAction(any(), any())).thenReturn(result);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString()
        );
    }

    private String createWorkItem() throws Exception {
        return json(mockMvc.perform(post("/api/work-items")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Sink is leaking"}
                                """))
                .andExpect(status().isCreated()))
                .get("id").asText();
    }

    /** The proposal generated automatically when the request was created. */
    private String pendingAction(String workItemId) throws Exception {
        JsonNode actions = json(mockMvc.perform(
                get("/api/work-items/{id}/proposed-actions", workItemId).with(user)));

        assertThat(actions.get(0).get("status").asText()).isEqualTo("PENDING_APPROVAL");

        return actions.get(0).get("id").asText();
    }

    private String generateAction(String workItemId) throws Exception {
        return json(mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId).with(user))
                .andExpect(status().isCreated()))
                .get("id").asText();
    }

    private ResultActions approve(String actionId, String body) throws Exception {
        var request = post("/api/work-items/proposed-actions/{id}/approve", actionId).with(user);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        return mockMvc.perform(request);
    }

    private String workItemStatus(String workItemId) throws Exception {
        return json(mockMvc.perform(get("/api/work-items/{id}", workItemId).with(user)))
                .get("status").asText();
    }

    @Test
    void typedRequestGetsProposalAutomatically() throws Exception {
        String workItemId = createWorkItem();

        assertThat(workItemStatus(workItemId)).isEqualTo("WAITING_FOR_APPROVAL");
        pendingAction(workItemId);
    }

    @Test
    void typedRequestIsKeptWhenAutomaticProposalFails() throws Exception {
        when(aiService.proposeAction(any(), any()))
                .thenThrow(new com.clerova.exception.AiServiceException("down", null));

        String workItemId = createWorkItem();

        assertThat(workItemStatus(workItemId)).isEqualTo("NEW");
    }

    @Test
    void closedRequestsAreListedSeparately() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String workItemId = createWorkItem();
        approve(pendingAction(workItemId), null).andExpect(status().isOk());

        JsonNode open = json(mockMvc.perform(get("/api/work-items").with(user)));
        JsonNode closed = json(mockMvc.perform(get("/api/work-items?closed=true").with(user)));

        assertThat(open.findValuesAsText("id")).doesNotContain(workItemId);
        assertThat(closed.findValuesAsText("id")).contains(workItemId);
    }

    @Test
    void weekSummaryCountsThisOrganizationsRequests() throws Exception {
        createWorkItem();
        createWorkItem();

        mockMvc.perform(get("/api/summary").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsOrganized").value(2))
                .andExpect(jsonPath("$.repliesSent").value(0))
                .andExpect(jsonPath("$.minutesSaved").value(10))
                .andExpect(jsonPath("$.medianReplyMinutes").isEmpty())
                .andExpect(jsonPath("$.allTimeRequests").value(2))
                .andExpect(jsonPath("$.allTimeApprovals").value(0));

        // Another organization starts from zero
        mockMvc.perform(get("/api/summary").with(onboardNewUser(mockMvc)))
                .andExpect(jsonPath("$.requestsOrganized").value(0));
    }

    @Test
    void unknownWorkItemReturns404() throws Exception {
        mockMvc.perform(get("/api/work-items/{id}", UUID.randomUUID()).with(user))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void secondGenerationWhilePendingIsRejected() throws Exception {
        String workItemId = createWorkItem();

        // A proposal is already pending from creation
        mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId).with(user))
                .andExpect(status().isConflict());
    }

    @Test
    void approvalStoresEditedResponse() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String workItemId = createWorkItem();
        String actionId = pendingAction(workItemId);

        approve(actionId, """
                {"finalResponse":"  Edited reply  "}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.draftedResponse").value("AI draft"))
                .andExpect(jsonPath("$.finalResponse").value("Edited reply"));

        assertThat(workItemStatus(workItemId)).isEqualTo("COMPLETED");
    }

    @Test
    void sendingReplyForManualRequestIsRejectedWhenEmailIsNotSetUp() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String actionId = pendingAction(createWorkItem());

        approve(actionId, null).andExpect(status().isOk());

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/send-reply", actionId).with(user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_SENDING_DISABLED"));
    }

    @Test
    void approvalWithoutBodyUsesDraft() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String actionId = pendingAction(createWorkItem());

        approve(actionId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalResponse").value("AI draft"));
    }

    @Test
    void escalationCanBeClosedManually() throws Exception {
        proposeAction(ActionType.ESCALATE_TO_HUMAN);
        String workItemId = createWorkItem();
        approve(pendingAction(workItemId), null).andExpect(status().isOk());

        assertThat(workItemStatus(workItemId)).isEqualTo("ESCALATED");

        mockMvc.perform(post("/api/work-items/{id}/close", workItemId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void rejectReturnsWorkItemToReviewAndAllowsRegeneration() throws Exception {
        String workItemId = createWorkItem();
        String actionId = pendingAction(workItemId);

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/reject", actionId).with(user))
                .andExpect(status().isOk());

        assertThat(workItemStatus(workItemId)).isEqualTo("NEW");

        generateAction(workItemId);
    }

    @Test
    void maintenanceLifecycleEnforcesTransitions() throws Exception {
        String workItemId = createWorkItem();
        approve(pendingAction(workItemId), null).andExpect(status().isOk());

        String maintenanceId = json(mockMvc.perform(
                get("/api/maintenance-requests/work-item/{id}", workItemId).with(user)))
                .get(0).get("id").asText();

        // Cannot close the work item while maintenance is still open
        mockMvc.perform(post("/api/work-items/{id}/close", workItemId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isConflict());

        // OPEN -> COMPLETED skips IN_PROGRESS
        patchMaintenance(maintenanceId, "COMPLETED").andExpect(status().isConflict());

        patchMaintenance(maintenanceId, "IN_PROGRESS").andExpect(status().isOk());
        patchMaintenance(maintenanceId, "COMPLETED").andExpect(status().isOk());

        assertThat(workItemStatus(workItemId)).isEqualTo("COMPLETED");

        // Terminal requests cannot be reopened
        patchMaintenance(maintenanceId, "OPEN").andExpect(status().isConflict());
    }

    @Test
    void jobNotesSaveWithAStatusChangeAndAfterTheJobIsDone() throws Exception {
        String workItemId = createWorkItem();
        approve(pendingAction(workItemId), null).andExpect(status().isOk());

        String maintenanceId = json(mockMvc.perform(
                get("/api/maintenance-requests/work-item/{id}", workItemId).with(user)))
                .get(0).get("id").asText();

        patchMaintenance(maintenanceId, "IN_PROGRESS").andExpect(status().isOk());

        // Notes are trimmed and recorded in the request's activity
        patchNotes(maintenanceId, "  CoolAir HVAC, Thu 10 to 12  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("CoolAir HVAC, Thu 10 to 12"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Saving the same notes again records nothing new
        patchNotes(maintenanceId, "CoolAir HVAC, Thu 10 to 12").andExpect(status().isOk());

        // Unsaved notes sent along with "Mark done" are kept
        mockMvc.perform(patch("/api/maintenance-requests/{id}", maintenanceId)
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED","notes":"Fixed the compressor"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.notes").value("Fixed the compressor"));

        // A notes save that arrives after the job is done still works
        patchNotes(maintenanceId, "Fixed the compressor. Invoice paid.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Fixed the compressor. Invoice paid."));

        // Blank notes are cleared
        patchNotes(maintenanceId, "   ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").isEmpty());

        // Too long
        patchNotes(maintenanceId, "x".repeat(5001)).andExpect(status().isBadRequest());

        JsonNode events = json(mockMvc.perform(get("/api/work-items/{id}/events", workItemId).with(user)));
        assertThat(events.findValuesAsText("detail").stream()
                .filter(detail -> detail.startsWith("Job notes")).toList())
                .containsExactly("Job notes updated", "Job notes updated", "Job notes updated", "Job notes cleared");
    }

    @Test
    void jobsBoardKeepsOpenJobsAndRecentlyFinishedOnes() throws Exception {
        String oldId = createWorkItem();
        approve(pendingAction(oldId), null).andExpect(status().isOk());
        String oldJob = json(mockMvc.perform(
                get("/api/maintenance-requests/work-item/{id}", oldId).with(user))).get(0).get("id").asText();
        patchMaintenance(oldJob, "IN_PROGRESS").andExpect(status().isOk());
        patchMaintenance(oldJob, "COMPLETED").andExpect(status().isOk());

        String openId = createWorkItem();
        approve(pendingAction(openId), null).andExpect(status().isOk());
        String openJob = json(mockMvc.perform(
                get("/api/maintenance-requests/work-item/{id}", openId).with(user))).get(0).get("id").asText();

        // The finished job was done long ago; the open one is old but open
        jdbcTemplate.update("update maintenance_requests set updated_at = ? where id in (?, ?)",
                java.sql.Timestamp.from(java.time.Instant.now().minus(java.time.Duration.ofDays(120))),
                UUID.fromString(oldJob), UUID.fromString(openJob));

        JsonNode board = json(mockMvc.perform(get("/api/maintenance-requests").with(user)));
        assertThat(board.findValuesAsText("id")).contains(openJob).doesNotContain(oldJob);

        // Still reachable from its request
        mockMvc.perform(get("/api/maintenance-requests/work-item/{id}", oldId).with(user))
                .andExpect(jsonPath("$[0].id").value(oldJob));
    }

    @Test
    void latestProposedActionsReturnsOnePerOpenWorkItem() throws Exception {
        String workItemId = createWorkItem();
        String firstActionId = pendingAction(workItemId);

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/reject", firstActionId).with(user))
                .andExpect(status().isOk());

        String secondActionId = generateAction(workItemId);

        JsonNode latest = json(mockMvc.perform(get("/api/work-items/proposed-actions/latest").with(user))
                .andExpect(status().isOk()));

        long forWorkItem = 0;

        for (JsonNode action : latest) {
            if (action.get("workItemId").asText().equals(workItemId)) {
                forWorkItem++;
                assertThat(action.get("id").asText()).isEqualTo(secondActionId);
            }
        }

        assertThat(forWorkItem).isEqualTo(1);
    }

    @Test
    void oversizedMessageReturnsValidationError() throws Exception {
        String message = "x".repeat(10_001);

        mockMvc.perform(post("/api/work-items")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private ResultActions patchNotes(String id, String notes) throws Exception {
        return mockMvc.perform(patch("/api/maintenance-requests/{id}", id)
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(new com.fasterxml.jackson.databind.ObjectMapper()
                        .createObjectNode().put("notes", notes).toString()));
    }

    private ResultActions patchMaintenance(String id, String status) throws Exception {
        return mockMvc.perform(patch("/api/maintenance-requests/{id}", id)
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }
}

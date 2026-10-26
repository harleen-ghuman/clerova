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

    private ResultActions patchMaintenance(String id, String status) throws Exception {
        return mockMvc.perform(patch("/api/maintenance-requests/{id}", id)
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }
}

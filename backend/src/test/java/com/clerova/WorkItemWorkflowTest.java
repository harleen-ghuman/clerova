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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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

    @BeforeEach
    void setUp() {
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

        when(aiService.proposeAction(any())).thenReturn(result);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString()
        );
    }

    private String createWorkItem() throws Exception {
        return json(mockMvc.perform(post("/api/work-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationId":"org","message":"Sink is leaking"}
                                """))
                .andExpect(status().isCreated()))
                .get("id").asText();
    }

    private String generateAction(String workItemId) throws Exception {
        return json(mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId))
                .andExpect(status().isCreated()))
                .get("id").asText();
    }

    private ResultActions approve(String actionId, String body) throws Exception {
        var request = post("/api/work-items/proposed-actions/{id}/approve", actionId);

        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        return mockMvc.perform(request);
    }

    private String workItemStatus(String workItemId) throws Exception {
        return json(mockMvc.perform(get("/api/work-items/{id}", workItemId)))
                .get("status").asText();
    }

    @Test
    void unknownWorkItemReturns404() throws Exception {
        mockMvc.perform(get("/api/work-items/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void secondGenerationWhilePendingIsRejected() throws Exception {
        String workItemId = createWorkItem();
        generateAction(workItemId);

        mockMvc.perform(post("/api/work-items/{id}/proposed-actions", workItemId))
                .andExpect(status().isConflict());
    }

    @Test
    void approvalStoresEditedResponse() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String workItemId = createWorkItem();
        String actionId = generateAction(workItemId);

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
    void approvalWithoutBodyUsesDraft() throws Exception {
        proposeAction(ActionType.RESPOND_TO_CUSTOMER);
        String actionId = generateAction(createWorkItem());

        approve(actionId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalResponse").value("AI draft"));
    }

    @Test
    void escalationCanBeClosedManually() throws Exception {
        proposeAction(ActionType.ESCALATE_TO_HUMAN);
        String workItemId = createWorkItem();
        approve(generateAction(workItemId), null).andExpect(status().isOk());

        assertThat(workItemStatus(workItemId)).isEqualTo("ESCALATED");

        mockMvc.perform(post("/api/work-items/{id}/close", workItemId)
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
        String actionId = generateAction(workItemId);

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/reject", actionId))
                .andExpect(status().isOk());

        assertThat(workItemStatus(workItemId)).isEqualTo("NEW");

        generateAction(workItemId);
    }

    @Test
    void maintenanceLifecycleEnforcesTransitions() throws Exception {
        String workItemId = createWorkItem();
        approve(generateAction(workItemId), null).andExpect(status().isOk());

        String maintenanceId = json(mockMvc.perform(
                get("/api/maintenance-requests/work-item/{id}", workItemId)))
                .get(0).get("id").asText();

        // Cannot close the work item while maintenance is still open
        mockMvc.perform(post("/api/work-items/{id}/close", workItemId)
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
        String firstActionId = generateAction(workItemId);

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/reject", firstActionId))
                .andExpect(status().isOk());

        String secondActionId = generateAction(workItemId);

        JsonNode latest = json(mockMvc.perform(get("/api/work-items/proposed-actions/latest"))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"org\",\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    private ResultActions patchMaintenance(String id, String status) throws Exception {
        return mockMvc.perform(patch("/api/maintenance-requests/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }
}

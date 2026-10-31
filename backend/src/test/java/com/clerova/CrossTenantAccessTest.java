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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every endpoint that takes an id refuses another company's data. */
@SpringBootTest
@AutoConfigureMockMvc
class CrossTenantAccessTest {

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
        extraction.summary = "Leak";
        extraction.address = "1 Main St";
        when(aiService.extract(anyString())).thenReturn(extraction);

        ProposedActionResult action = new ProposedActionResult();
        action.actionType = ActionType.CREATE_MAINTENANCE_REQUEST;
        action.reasoning = "Reasoning";
        action.draftedResponse = "Draft";
        when(aiService.proposeAction(any(), any())).thenReturn(action);
    }

    private JsonNode json(MockHttpServletRequestBuilder request, RequestPostProcessor user) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request.with(user)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anotherCompanyCanNotReadOrChangeAnyRequestJobOrFile() throws Exception {
        RequestPostProcessor owner = onboardNewUser(mockMvc);
        RequestPostProcessor other = onboardNewUser(mockMvc);

        // The owner's request, with a second suggestion waiting and an approved job
        String workItemId = json(post("/api/work-items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Sink is leaking\"}"), owner).get("id").asText();
        String firstAction = json(get("/api/work-items/{id}/proposed-actions", workItemId), owner).get(0).get("id").asText();
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve", firstAction).with(owner)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        String jobId = json(get("/api/maintenance-requests/work-item/{id}", workItemId), owner).get(0).get("id").asText();

        String body = "{\"message\":\"hi\",\"status\":\"CANCELLED\",\"notes\":\"x\",\"finalResponse\":\"x\"}";
        List<MockHttpServletRequestBuilder> attempts = List.of(
                get("/api/work-items/{id}", workItemId),
                get("/api/work-items/{id}/conversation", workItemId),
                get("/api/work-items/{id}/events", workItemId),
                get("/api/work-items/{id}/proposed-actions", workItemId),
                post("/api/work-items/{id}/proposed-actions", workItemId),
                post("/api/work-items/{id}/close", workItemId),
                post("/api/work-items/{id}/draft-reply", workItemId),
                post("/api/work-items/{id}/reply", workItemId),
                post("/api/work-items/{id}/reply-handled", workItemId),
                post("/api/work-items/proposed-actions/{id}/approve", firstAction),
                post("/api/work-items/proposed-actions/{id}/reject", firstAction),
                post("/api/work-items/proposed-actions/{id}/send-reply", firstAction),
                get("/api/maintenance-requests/work-item/{id}", workItemId),
                patch("/api/maintenance-requests/{id}", jobId),
                post("/api/inbox/{id}/reprocess", UUID.randomUUID()),
                get("/api/attachments/{id}", UUID.randomUUID())
        );

        for (MockHttpServletRequestBuilder attempt : attempts) {
            var response = mockMvc.perform(attempt.with(other)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            String what = attempt.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();

            // An empty list for "jobs of this request" is fine; anything else must be refused
            if (response.getStatus() == 200) {
                assertThat(response.getContentAsString()).as(what).isEqualTo("[]");
            } else {
                assertThat(response.getStatus()).as(what).isIn(400, 403, 404, 409);
                assertThat(response.getContentAsString()).as(what).doesNotContain("Sink is leaking", "Draft");
            }
        }

        // Lists only ever show the company's own data
        mockMvc.perform(get("/api/work-items").with(other)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/maintenance-requests").with(other)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/inbox").with(other)).andExpect(jsonPath("$.length()").value(0));

        // And nothing of the owner's changed
        mockMvc.perform(get("/api/work-items/{id}", workItemId).with(owner))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/maintenance-requests/work-item/{id}", workItemId).with(owner))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].notes").isEmpty());
    }

    @Test
    void signedOutRequestsAreRefused() throws Exception {
        for (String path : List.of("/api/me", "/api/work-items", "/api/inbox", "/api/summary",
                "/api/company-info", "/api/email-settings", "/api/maintenance-requests",
                "/api/attachments/" + UUID.randomUUID(), "/api/billing/offer")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/billing/portal")).andExpect(status().isUnauthorized());
        // Webhooks check their own credentials
        mockMvc.perform(post("/api/inbound/postmark").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}

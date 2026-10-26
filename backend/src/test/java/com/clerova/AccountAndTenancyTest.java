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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static com.clerova.WorkItemWorkflowTest.userToken;
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
class AccountAndTenancyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private String createWorkItem(RequestPostProcessor user) throws Exception {
        String body = mockMvc.perform(post("/api/work-items")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Sink is leaking"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }

    private String organizationIdOf(RequestPostProcessor user) throws Exception {
        String body = mockMvc.perform(get("/api/me").with(user))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("organization").get("id").asText();
    }

    @Test
    void apiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/work-items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void settingsUpdateNameAndCompanyAndPickUpANewEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        RequestPostProcessor user = userToken(userId);

        mockMvc.perform(post("/api/onboarding")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Acme","industry":"PROPERTY_MANAGEMENT","fullName":"Sam"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/me")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"  Sam Lee ","organizationName":" Maple Property Group "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Sam Lee"))
                .andExpect(jsonPath("$.organization.name").value("Maple Property Group"));

        mockMvc.perform(patch("/api/me")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sam","organizationName":" "}
                                """))
                .andExpect(status().isBadRequest());

        // After the user confirms a new address in Supabase, the token carries it
        RequestPostProcessor changedEmail = jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("email", "sam@maple.com"));

        mockMvc.perform(get("/api/me").with(changedEmail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sam@maple.com"));

        String stored = jdbcTemplate.queryForObject(
                "select email from app_users where id = ?", String.class, userId);
        org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("sam@maple.com");
    }

    @Test
    void newUserMustOnboardBeforeUsingTheApp() throws Exception {
        RequestPostProcessor user = userToken(UUID.randomUUID());

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization").isEmpty());

        mockMvc.perform(get("/api/work-items").with(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ONBOARDING_REQUIRED"));

        mockMvc.perform(post("/api/onboarding")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationName": "Big Rig Logistics",
                                  "industry": "TRUCKING",
                                  "companySize": "6-20",
                                  "referralSource": "instagram"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organization.name").value("Big Rig Logistics"))
                .andExpect(jsonPath("$.organization.industry").value("TRUCKING"))
                .andExpect(jsonPath("$.organization.plan").value("TRIAL"))
                .andExpect(jsonPath("$.organization.aiMonthlyLimit").value(100));

        mockMvc.perform(get("/api/work-items").with(user))
                .andExpect(status().isOk());

        // A user belongs to one organization
        mockMvc.perform(post("/api/onboarding")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Second","industry":"TRUCKING"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void onboardingValidatesInput() throws Exception {
        mockMvc.perform(post("/api/onboarding")
                        .with(userToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":" ","industry":"TRUCKING"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizationsCannotSeeOrChangeEachOthersData() throws Exception {
        RequestPostProcessor alice = onboardNewUser(mockMvc);
        RequestPostProcessor bob = onboardNewUser(mockMvc);

        String aliceWorkItem = createWorkItem(alice);

        // The proposal was generated automatically when the request was created
        String actionBody = mockMvc.perform(
                        get("/api/work-items/{id}/proposed-actions", aliceWorkItem).with(alice))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String aliceAction = objectMapper.readTree(actionBody).get(0).get("id").asText();

        JsonNode bobItems = objectMapper.readTree(mockMvc.perform(get("/api/work-items").with(bob))
                .andReturn().getResponse().getContentAsString());

        for (JsonNode item : bobItems) {
            org.assertj.core.api.Assertions.assertThat(item.get("id").asText())
                    .isNotEqualTo(aliceWorkItem);
        }

        mockMvc.perform(get("/api/work-items/{id}", aliceWorkItem).with(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/work-items/{id}/proposed-actions", aliceWorkItem).with(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve", aliceAction).with(bob))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/work-items/proposed-actions/latest").with(bob))
                .andExpect(jsonPath("$").isEmpty());

        // Alice can approve; Bob still cannot see the maintenance request
        mockMvc.perform(post("/api/work-items/proposed-actions/{id}/approve", aliceAction).with(alice))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/maintenance-requests").with(bob))
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/maintenance-requests").with(alice))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void monthlyAiQuotaIsEnforced() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);

        jdbcTemplate.update(
                "update organizations set ai_monthly_limit = 1 where id = ?",
                UUID.fromString(organizationIdOf(user))
        );

        // The request and its automatic suggestion count once
        createWorkItem(user);

        mockMvc.perform(post("/api/work-items")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Another leak"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("AI_QUOTA_EXCEEDED"));

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.aiRequestsUsedThisMonth").value(1));
    }

    @Test
    void expiredTrialBlocksAiRequests() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);

        jdbcTemplate.update(
                "update organizations set trial_ends_at = ? where id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)),
                UUID.fromString(organizationIdOf(user))
        );

        mockMvc.perform(post("/api/work-items")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Sink is leaking"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("TRIAL_EXPIRED"));

        // Existing data stays readable
        mockMvc.perform(get("/api/work-items").with(user))
                .andExpect(status().isOk());
    }
}

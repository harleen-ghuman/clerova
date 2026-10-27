package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.service.CompanyInfoService;
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

import java.util.Map;
import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyInfoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyInfoService companyInfoService;

    @MockitoBean
    private AiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RequestPostProcessor user;

    @BeforeEach
    void setUp() throws Exception {
        user = onboardNewUser(mockMvc);
    }

    private ResultActions save(RequestPostProcessor who, Map<String, String> fields) throws Exception {
        return mockMvc.perform(put("/api/company-info")
                .with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fields)));
    }

    private UUID organizationId(RequestPostProcessor who) throws Exception {
        String body = mockMvc.perform(get("/api/me").with(who)).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("organization").get("id").asText());
    }

    @Test
    void savesTrimsAndFeedsTheAi() throws Exception {
        mockMvc.perform(get("/api/company-info").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAndPayments").isEmpty());
        assertThat(companyInfoService.forPrompt(organizationId(user))).isNull();

        save(user, Map.of(
                "rentAndPayments", "  Rent is due on the 1st. Late fee $50 after the 5th.  ",
                "pets", "Cats only.",
                "parking", "   "
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rentAndPayments").value("Rent is due on the 1st. Late fee $50 after the 5th."))
                .andExpect(jsonPath("$.parking").isEmpty());

        mockMvc.perform(get("/api/company-info").with(user))
                .andExpect(jsonPath("$.pets").value("Cats only."));

        assertThat(companyInfoService.forPrompt(organizationId(user))).isEqualTo(
                "Rent and payments: Rent is due on the 1st. Late fee $50 after the 5th.\nPets: Cats only.");

        // Another workspace sees nothing
        RequestPostProcessor other = onboardNewUser(mockMvc);
        mockMvc.perform(get("/api/company-info").with(other))
                .andExpect(jsonPath("$.pets").isEmpty());

        // Clearing everything leaves nothing for the AI
        save(user, Map.of()).andExpect(status().isOk());
        assertThat(companyInfoService.forPrompt(organizationId(user))).isNull();
    }

    @Test
    void refusesAnswersThatAreTooLong() throws Exception {
        save(user, Map.of("pets", "x".repeat(1_501)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Keep each answer under 1,500 characters."));

        String full = "y".repeat(1_500);
        save(user, Map.of(
                "rentAndPayments", full, "officeAndContact", full, "emergencies", full,
                "maintenance", full, "utilities", full, "pets", full
        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "That's more than Clerova can use. Keep all answers together under 8,000 characters."));

        mockMvc.perform(get("/api/company-info").with(user))
                .andExpect(jsonPath("$.pets").isEmpty());
    }
}

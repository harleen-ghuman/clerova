package com.clerova;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.userToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Until trucking opens, nobody can create a trucking workspace. */
@SpringBootTest(properties = "app.trucking.enabled=false")
@AutoConfigureMockMvc
class TruckingSignupClosedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void truckingSignUpsWaitUntilItOpens() throws Exception {
        mockMvc.perform(post("/api/onboarding")
                        .with(userToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Big Rig","industry":"TRUCKING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Clerova for trucking isn't open for sign-ups yet."));

        mockMvc.perform(post("/api/onboarding")
                        .with(userToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Maple PM","industry":"PROPERTY_MANAGEMENT"}
                                """))
                .andExpect(status().isCreated());
    }
}

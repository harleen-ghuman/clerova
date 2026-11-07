package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.billing.StripeClient;
import com.clerova.billing.StripeSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.clerova.WorkItemWorkflowTest.userToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trucking plans: priced by truck, Starter up to 7 trucks and Fleet from 8. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.billing.stripe-secret-key=sk_test_example",
        "app.billing.webhook-secret=whsec_test",
        "app.billing.app-url=https://app.example.com"
})
class TruckingBillingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private StripeClient stripe;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final tools.jackson.databind.ObjectMapper stripeJson = new tools.jackson.databind.ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void checkoutIsPricedByTrucksAndPicksTheCheaperPlan() throws Exception {
        RequestPostProcessor user = signUpCarrier();
        stubCheckout();

        checkout(user, "{\"plan\":\"STARTER\",\"interval\":\"month\",\"trucks\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter how many trucks you run, from 1 to 49."));
        // Not quietly rounded down to 2
        checkout(user, "{\"plan\":\"STARTER\",\"interval\":\"month\",\"trucks\":2.5}")
                .andExpect(status().isBadRequest());
        checkout(user, "{\"plan\":\"STARTER\",\"interval\":\"month\",\"trucks\":\"ten\"}")
                .andExpect(status().isBadRequest());

        when(stripe.get("/v1/prices?active=true&lookup_keys%5B%5D=founding_trucking_starter_monthly"))
                .thenReturn(stripeJson.readTree("{\"data\":[{\"id\":\"price_ts\"}]}"));
        checkout(user, "{\"plan\":\"GROWTH\",\"interval\":\"month\",\"trucks\":6}").andExpect(status().isOk());

        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(stripe).post(eq("/v1/checkout/sessions"), form.capture());
        assertThat(form.getValue())
                .containsEntry("line_items[0][price]", "price_ts")
                .containsEntry("line_items[0][quantity]", "6")
                .containsEntry("subscription_data[trial_period_days]", "14");
        mockMvc.perform(get("/api/me").with(user)).andExpect(jsonPath("$.organization.trucks").value(6));
    }

    @Test
    void nineTrucksOrMoreGoOnFleet() throws Exception {
        RequestPostProcessor user = signUpCarrier();
        stubCheckout();
        when(stripe.get("/v1/prices?active=true&lookup_keys%5B%5D=founding_trucking_fleet_yearly"))
                .thenReturn(stripeJson.readTree("{\"data\":[{\"id\":\"price_tf\"}]}"));

        checkout(user, "{\"plan\":\"STARTER\",\"interval\":\"year\",\"trucks\":12}").andExpect(status().isOk());
        verify(stripe).get("/v1/prices?active=true&lookup_keys%5B%5D=founding_trucking_fleet_yearly");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSubscriptionSetsThePlanTrucksAndFairUseAndTrucksCanChange() throws Exception {
        RequestPostProcessor user = signUpCarrier();
        String organizationId = organizationIdOf(user);

        stubSubscription(organizationId, "trialing", "founding_trucking_starter_monthly", 6);
        webhook();

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.plan").value("STARTER"))
                .andExpect(jsonPath("$.organization.trucks").value(6))
                .andExpect(jsonPath("$.organization.aiMonthlyLimit").value(120))
                .andExpect(jsonPath("$.organization.foundingCustomer").value(true))
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(false));

        // Going to 10 trucks moves the subscription to Fleet, prorated
        when(stripe.get("/v1/prices?active=true&lookup_keys%5B%5D=founding_trucking_fleet_monthly"))
                .thenReturn(stripeJson.readTree("{\"data\":[{\"id\":\"price_tf\"}]}"));
        when(stripe.post(eq("/v1/subscriptions/sub_1"), anyMap()))
                .thenReturn(subscription(organizationId, "trialing", "founding_trucking_fleet_monthly", 10));

        mockMvc.perform(post("/api/billing/trucks").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trucks\":10}")).andExpect(status().isOk());

        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(stripe).post(eq("/v1/subscriptions/sub_1"), form.capture());
        assertThat(form.getValue())
                .containsEntry("items[0][id]", "si_1")
                .containsEntry("items[0][price]", "price_tf")
                .containsEntry("items[0][quantity]", "10")
                .containsEntry("proration_behavior", "create_prorations");
        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.plan").value("FLEET"))
                .andExpect(jsonPath("$.organization.trucks").value(10))
                .andExpect(jsonPath("$.organization.aiMonthlyLimit").value(200));

        // Growth is for property managers
        mockMvc.perform(post("/api/billing/upgrade").with(user)).andExpect(status().isConflict());

        mockMvc.perform(post("/api/billing/trucks").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trucks\":50}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("For 50 or more trucks, email hello@clerova-ai.com and we'll set up your plan."));
    }

    @Test
    void trucksAreSavedBeforeThereIsAPlan() throws Exception {
        RequestPostProcessor user = signUpCarrier();

        mockMvc.perform(post("/api/billing/trucks").with(user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"trucks\":3}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.trucks").value(3))
                .andExpect(jsonPath("$.organization.aiMonthlyLimit").value(60));
        verify(stripe, never()).post(startsWith("/v1/subscriptions/"), anyMap());
    }

    @Test
    void aWorkspaceCantClaimAnotherWorkspacesCheckout() throws Exception {
        RequestPostProcessor owner = signUpCarrier();
        RequestPostProcessor other = signUpCarrier();
        String ownerId = organizationIdOf(owner);
        when(stripe.get("/v1/checkout/sessions/cs_owner")).thenReturn(stripeJson.readTree(
                "{\"id\":\"cs_owner\",\"client_reference_id\":\"" + ownerId + "\",\"subscription\":\"sub_1\"}"));
        stubSubscription(ownerId, "trialing", "founding_trucking_starter_monthly", 5);

        mockMvc.perform(post("/api/billing/checkout/cs_owner/complete").with(other)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me").with(other))
                .andExpect(jsonPath("$.organization.subscriptionStatus").doesNotExist())
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(true));

        mockMvc.perform(post("/api/billing/checkout/cs_owner/complete").with(owner)).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(owner))
                .andExpect(jsonPath("$.organization.subscriptionStatus").value("trialing"))
                .andExpect(jsonPath("$.organization.trucks").value(5));
    }

    // ---------------------------------------------------------------- helpers

    private RequestPostProcessor signUpCarrier() throws Exception {
        RequestPostProcessor user = userToken(UUID.randomUUID());
        mockMvc.perform(post("/api/onboarding").with(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationName\":\"Sierra Run Transport\",\"industry\":\"TRUCKING\"}"))
                .andExpect(status().isCreated());
        return user;
    }

    private void stubCheckout() {
        when(stripe.post(eq("/v1/customers"), anyMap())).thenReturn(stripeJson.readTree("{\"id\":\"cus_" + UUID.randomUUID() + "\"}"));
        when(stripe.get(startsWith("/v1/subscriptions?status=all"))).thenReturn(stripeJson.readTree("{\"data\":[]}"));
        when(stripe.post(eq("/v1/checkout/sessions"), anyMap()))
                .thenReturn(stripeJson.readTree("{\"url\":\"https://checkout.stripe.com/c/1\"}"));
    }

    private ResultActions checkout(RequestPostProcessor user, String body) throws Exception {
        return mockMvc.perform(post("/api/billing/checkout").with(user).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String organizationIdOf(RequestPostProcessor user) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/me").with(user)).andReturn().getResponse().getContentAsString())
                .get("organization").get("id").asText();
    }

    private tools.jackson.databind.JsonNode subscription(String organizationId, String status, String lookupKey, int trucks) {
        return stripeJson.readTree("""
                {
                  "id": "sub_1",
                  "customer": "cus_%s",
                  "status": "%s",
                  "cancel_at_period_end": false,
                  "trial_end": 1900000000,
                  "metadata": {"organization_id": "%s"},
                  "items": {"data": [{
                    "id": "si_1",
                    "quantity": %d,
                    "current_period_end": 1900000000,
                    "price": {"lookup_key": "%s", "recurring": {"interval": "month"}}
                  }]}
                }
                """.formatted(organizationId, status, organizationId, trucks, lookupKey));
    }

    private void stubSubscription(String organizationId, String status, String lookupKey, int trucks) {
        when(stripe.get("/v1/subscriptions/sub_1")).thenReturn(subscription(organizationId, status, lookupKey, trucks));
    }

    private void webhook() throws Exception {
        String payload = "{\"type\":\"customer.subscription.updated\",\"data\":{\"object\":{\"id\":\"sub_1\"}}}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = StripeSignature.sign(timestamp + "." + payload, "whsec_test");
        mockMvc.perform(post("/api/stripe/webhook").header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
    }
}

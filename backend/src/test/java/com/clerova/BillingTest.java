package com.clerova;

import com.clerova.ai.AiService;
import com.clerova.billing.StripeClient;
import com.clerova.billing.StripeSignature;
import com.clerova.domain.ActionType;
import com.clerova.domain.Category;
import com.clerova.domain.Priority;
import com.clerova.dto.ExtractionResult;
import com.clerova.dto.ProposedActionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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

import static com.clerova.WorkItemWorkflowTest.onboardNewUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.billing.stripe-secret-key=sk_test_example",
        "app.billing.webhook-secret=whsec_test",
        "app.billing.app-url=https://app.example.com"
})
class BillingTest {

    private static final String WEBHOOK_SECRET = "whsec_test";

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private StripeClient stripe;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final tools.jackson.databind.ObjectMapper stripeJson = new tools.jackson.databind.ObjectMapper();

    @BeforeEach
    void setUp() {
        ExtractionResult extraction = new ExtractionResult();
        extraction.category = Category.MAINTENANCE;
        extraction.priority = Priority.HIGH;
        extraction.summary = "Leak";
        extraction.address = "1 Main St";
        when(aiService.extract(anyString())).thenReturn(extraction);

        ProposedActionResult action = new ProposedActionResult();
        action.actionType = ActionType.RESPOND_TO_CUSTOMER;
        action.reasoning = "Reasoning";
        action.draftedResponse = "Draft";
        when(aiService.proposeAction(any(), any())).thenReturn(action);
    }

    private ResultActions createRequest(RequestPostProcessor user) throws Exception {
        return mockMvc.perform(post("/api/work-items")
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"message":"Sink is leaking"}
                        """));
    }

    private String organizationIdOf(RequestPostProcessor user) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/me").with(user))
                        .andReturn().getResponse().getContentAsString())
                .get("organization").get("id").asText();
    }

    private ResultActions sendWebhook(String payload, String signature) throws Exception {
        return mockMvc.perform(post("/api/stripe/webhook")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private ResultActions sendSignedWebhook(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signature = StripeSignature.sign(timestamp + "." + payload, WEBHOOK_SECRET);

        return sendWebhook(payload, "t=" + timestamp + ",v1=" + signature);
    }

    private void stubSubscription(String organizationId, String status, String lookupKey) {
        when(stripe.get("/v1/subscriptions/sub_1")).thenReturn(stripeJson.readTree("""
                {
                  "id": "sub_1",
                  "customer": "cus_%s",
                  "status": "%s",
                  "cancel_at_period_end": false,
                  "trial_end": 1900000000,
                  "metadata": {"organization_id": "%s"},
                  "items": {"data": [{
                    "id": "si_1",
                    "current_period_end": 1900000000,
                    "price": {"lookup_key": "%s", "recurring": {"interval": "month"}}
                  }]}
                }
                """.formatted(organizationId, status, organizationId, lookupKey)));
    }

    @Test
    void newOrganizationMustChooseAPlanBeforeUsingAi() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.billingEnabled").value(true))
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(true));

        createRequest(user)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("SUBSCRIPTION_REQUIRED"));

        // Existing data stays readable
        mockMvc.perform(get("/api/work-items").with(user)).andExpect(status().isOk());
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkoutCollectsACardAndStartsTheTrialWithFoundingPrice() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);

        when(stripe.post(eq("/v1/customers"), anyMap()))
                .thenReturn(stripeJson.readTree("{\"id\":\"cus_new\"}"));
        when(stripe.get(org.mockito.ArgumentMatchers.startsWith("/v1/subscriptions?status=all")))
                .thenReturn(stripeJson.readTree("{\"data\":[]}"));
        when(stripe.get("/v1/prices?active=true&lookup_keys%5B%5D=founding_growth_yearly"))
                .thenReturn(stripeJson.readTree("{\"data\":[{\"id\":\"price_founding\"}]}"));
        when(stripe.post(eq("/v1/checkout/sessions"), anyMap()))
                .thenReturn(stripeJson.readTree("{\"url\":\"https://checkout.stripe.com/c/1\"}"));

        mockMvc.perform(post("/api/billing/checkout")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"GROWTH","interval":"year"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.com/c/1"));

        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(stripe).post(eq("/v1/checkout/sessions"), form.capture());

        assertThat(form.getValue())
                .containsEntry("customer", "cus_new")
                .containsEntry("line_items[0][price]", "price_founding")
                .containsEntry("payment_method_collection", "always")
                .containsEntry("subscription_data[trial_period_days]", "14")
                .containsEntry("client_reference_id", organizationIdOf(user));
    }

    @Test
    @SuppressWarnings("unchecked")
    void starterCanUpgradeToGrowthAtTheSameInterval() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);

        stubSubscription(organizationId, "active", "starter_monthly");
        sendSignedWebhook("""
                {"type":"customer.subscription.updated","data":{"object":{"id":"sub_1"}}}
                """).andExpect(status().isOk());

        when(stripe.get("/v1/prices?active=true&lookup_keys%5B%5D=growth_monthly"))
                .thenReturn(stripeJson.readTree("{\"data\":[{\"id\":\"price_growth\"}]}"));
        tools.jackson.databind.JsonNode upgraded = stripeJson.readTree(
                stripe.get("/v1/subscriptions/sub_1").toString().replace("starter_monthly", "growth_monthly"));
        when(stripe.post(eq("/v1/subscriptions/sub_1"), anyMap())).thenReturn(upgraded);

        mockMvc.perform(post("/api/billing/upgrade").with(user)).andExpect(status().isOk());

        ArgumentCaptor<Map<String, String>> form = ArgumentCaptor.forClass(Map.class);
        verify(stripe).post(eq("/v1/subscriptions/sub_1"), form.capture());
        assertThat(form.getValue())
                .containsEntry("items[0][id]", "si_1")
                .containsEntry("items[0][price]", "price_growth");

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.plan").value("GROWTH"))
                .andExpect(jsonPath("$.organization.aiMonthlyLimit").value(1500));

        // Growth can't be upgraded again
        mockMvc.perform(post("/api/billing/upgrade").with(user)).andExpect(status().isConflict());
    }

    @Test
    void renamingTheCompanyUpdatesTheStripeCustomerAndAStripeErrorDoesNotBlockIt() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);

        stubSubscription(organizationId, "trialing", "starter_monthly");
        sendSignedWebhook("""
                {"type":"customer.subscription.updated","data":{"object":{"id":"sub_1"}}}
                """).andExpect(status().isOk());

        when(stripe.post(eq("/v1/customers/cus_" + organizationId), anyMap()))
                .thenThrow(new com.clerova.exception.ApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY, "BILLING_ERROR", "Stripe: down"));

        mockMvc.perform(patch("/api/me")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationName":"Maple Property Group"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.name").value("Maple Property Group"));

        verify(stripe).post("/v1/customers/cus_" + organizationId, Map.of("name", "Maple Property Group"));
    }

    @Test
    void webhookWithABadSignatureIsRejected() throws Exception {
        sendWebhook("{\"type\":\"customer.subscription.updated\"}", "t=1,v1=bad")
                .andExpect(status().isBadRequest());
    }

    @Test
    void subscriptionWebhookGivesAccessAndCancellingTakesItAway() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);
        String event = """
                {"type":"customer.subscription.updated","data":{"object":{"id":"sub_1"}}}
                """;

        stubSubscription(organizationId, "trialing", "founding_growth_monthly");
        sendSignedWebhook(event).andExpect(status().isOk());

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.plan").value("GROWTH"))
                .andExpect(jsonPath("$.organization.aiMonthlyLimit").value(1500))
                .andExpect(jsonPath("$.organization.subscriptionStatus").value("trialing"))
                .andExpect(jsonPath("$.organization.billingInterval").value("month"))
                .andExpect(jsonPath("$.organization.foundingCustomer").value(true))
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(false));

        createRequest(user).andExpect(status().isCreated());

        stubSubscription(organizationId, "canceled", "founding_growth_monthly");
        sendSignedWebhook(event).andExpect(status().isOk());

        createRequest(user)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message").value(
                        "Your plan has ended. Choose a plan to keep using Clerova."));
    }

    @Test
    void starterOverTheLimitIsToldToUpgradeToGrowth() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);

        stubSubscription(organizationId, "active", "starter_monthly");
        sendSignedWebhook("""
                {"type":"customer.subscription.updated","data":{"object":{"id":"sub_1"}}}
                """).andExpect(status().isOk());

        jdbcTemplate.update(
                "update organizations set ai_usage_count = ai_monthly_limit, ai_usage_period = ? where id = ?",
                java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString(),
                java.util.UUID.fromString(organizationId)
        );

        createRequest(user)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "You've used all 500 requests in your plan this month. Upgrade to Growth for 1,500 a month")));
    }

    private void stubRawSubscription(String id, String json) {
        when(stripe.get("/v1/subscriptions/" + id)).thenReturn(stripeJson.readTree(json));
    }

    private static String subscriptionJson(String id, String organizationId, String status, String cancelAt) {
        return """
                {
                  "id": "%s",
                  "customer": "cus_%s",
                  "status": "%s",
                  "cancel_at_period_end": false,
                  "cancel_at": %s,
                  "trial_end": 1900000000,
                  "metadata": {"organization_id": "%s"},
                  "items": {"data": [{
                    "id": "si_1",
                    "current_period_end": 1900000000,
                    "price": {"lookup_key": "starter_monthly", "recurring": {"interval": "month"}}
                  }]}
                }
                """.formatted(id, organizationId, status, cancelAt, organizationId);
    }

    @Test
    void cancellationScheduledWithCancelAtShowsAsEnding() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);

        stubRawSubscription("sub_9", subscriptionJson("sub_9", organizationId, "trialing", "1800000000"));
        sendSignedWebhook("""
                {"type":"customer.subscription.updated","data":{"object":{"id":"sub_9"}}}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.organization.currentPeriodEnd").value("2027-01-15T08:00:00Z"))
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(false));
    }

    @Test
    void lateEventForAnOldEndedSubscriptionDoesNotReplaceTheNewOne() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);

        stubRawSubscription("sub_new", subscriptionJson("sub_new", organizationId, "active", "null"));
        sendSignedWebhook("""
                {"type":"customer.subscription.created","data":{"object":{"id":"sub_new"}}}
                """).andExpect(status().isOk());

        stubRawSubscription("sub_old", subscriptionJson("sub_old", organizationId, "canceled", "null"));
        sendSignedWebhook("""
                {"type":"customer.subscription.deleted","data":{"object":{"id":"sub_old"}}}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.subscriptionStatus").value("active"))
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(false));
    }

    @Test
    void checkoutIsRefusedWhenStripeAlreadyHasAPlanThatWasNotAppliedYet() throws Exception {
        RequestPostProcessor user = onboardNewUser(mockMvc);
        String organizationId = organizationIdOf(user);

        when(stripe.post(eq("/v1/customers"), anyMap()))
                .thenReturn(stripeJson.readTree("{\"id\":\"cus_" + organizationId + "\"}"));
        when(stripe.get("/v1/subscriptions?status=all&limit=10&customer=cus_" + organizationId))
                .thenReturn(stripeJson.readTree("{\"data\":[" + subscriptionJson("sub_paid", organizationId, "trialing", "null") + "]}"));

        mockMvc.perform(post("/api/billing/checkout")
                        .with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plan":"STARTER","interval":"month"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Your plan is already active. Refresh the page to continue."));

        // The plan was applied, and no second checkout was opened
        mockMvc.perform(get("/api/me").with(user))
                .andExpect(jsonPath("$.organization.subscriptionStatus").value("trialing"))
                .andExpect(jsonPath("$.organization.subscriptionRequired").value(false));
        org.mockito.Mockito.verify(stripe, org.mockito.Mockito.never()).post(eq("/v1/checkout/sessions"), anyMap());
    }
}

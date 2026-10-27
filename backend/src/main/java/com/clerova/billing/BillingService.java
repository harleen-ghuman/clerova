package com.clerova.billing;

import com.clerova.domain.Organization;
import com.clerova.domain.Plan;
import com.clerova.exception.ApiException;
import com.clerova.exception.ConflictException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stripe subscriptions: checkout with a card and a free trial, the billing
 * portal, and keeping each organization in sync with its subscription.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    /** Statuses that allow using Clerova. past_due keeps access while Stripe retries. */
    public static final String SUBSCRIPTION_REQUIRED = "SUBSCRIPTION_REQUIRED";

    static final Set<String> ACCESS_STATUSES = Set.of("trialing", "active", "past_due");

    private final BillingProperties properties;
    private final StripeClient stripe;
    private final OrganizationRepository organizationRepository;
    private final InboundMessageRepository inboundMessageRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public BillingService(
            BillingProperties properties,
            StripeClient stripe,
            OrganizationRepository organizationRepository,
            InboundMessageRepository inboundMessageRepository,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.stripe = stripe;
        this.organizationRepository = organizationRepository;
        this.inboundMessageRepository = inboundMessageRepository;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** Whether the organization has to choose a plan before using Clerova. */
    public boolean requiresSubscription(Organization organization) {
        return properties.isEnabled()
                && organization.getPlan() != Plan.INTERNAL
                && !hasAccessStatus(organization.getSubscriptionStatus());
    }

    private static boolean hasAccessStatus(String status) {
        return status != null && ACCESS_STATUSES.contains(status);
    }

    public void requireAccess(Organization organization) {
        if (!requiresSubscription(organization)) {
            return;
        }

        String status = organization.getSubscriptionStatus();

        String message;

        if (status == null) {
            message = "Choose a plan to start your free trial.";
        } else if (status.equals("canceled") || status.equals("incomplete_expired")) {
            message = "Your plan has ended. Choose a plan to keep using Clerova.";
        } else {
            message = "Your payment didn't go through. Update your card to keep using Clerova.";
        }

        throw new ApiException(HttpStatus.PAYMENT_REQUIRED, SUBSCRIPTION_REQUIRED, message);
    }

    public int foundingSpotsLeft() {
        return (int) Math.max(
                0,
                properties.getFoundingSpots() - organizationRepository.countByFoundingCustomerTrue()
        );
    }

    /**
     * Starts Stripe Checkout for a plan. The card is collected up front and
     * the first subscription gets the free trial.
     *
     * @return the Checkout page to send the user to
     */
    public String startCheckout(UUID organizationId, String email, Plan plan, String interval) {
        requireEnabled();

        if (plan != Plan.STARTER && plan != Plan.GROWTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Choose Starter or Growth.");
        }

        if (!interval.equals("month") && !interval.equals("year")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Choose monthly or yearly billing.");
        }

        Organization organization = findOrganization(organizationId);

        if (!requiresSubscription(organization)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ALREADY_SUBSCRIBED",
                    "You already have a plan. Change it in Settings, under Plan and billing."
            );
        }

        String customerId = ensureCustomer(organization, email);

        // A finished checkout that hasn't been applied yet (slow webhook,
        // second tab) must not lead to a second subscription and charge
        JsonNode existing = stripe.get("/v1/subscriptions?status=all&limit=10&customer=" + encode(customerId));

        for (JsonNode subscription : existing.path("data")) {
            if (ACCESS_STATUSES.contains(subscription.path("status").asString(""))) {
                syncSubscription(subscription);
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "ALREADY_SUBSCRIBED",
                        "Your plan is already active. Refresh the page to continue."
                );
            }
        }

        boolean founding = organization.isFoundingCustomer() || foundingSpotsLeft() > 0;
        String priceId = findPrice(lookupKey(plan, interval, founding))
                .or(() -> founding ? findPrice(lookupKey(plan, interval, false)) : Optional.empty())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "BILLING_ERROR",
                        "This plan is not available yet. Please contact hello@clerova-ai.com."
                ));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("mode", "subscription");
        form.put("customer", customerId);
        form.put("client_reference_id", organizationId.toString());
        form.put("line_items[0][price]", priceId);
        form.put("line_items[0][quantity]", "1");
        form.put("payment_method_collection", "always");
        form.put("subscription_data[metadata][organization_id]", organizationId.toString());
        form.put("success_url", properties.getAppUrl() + "/?checkout=success&session_id={CHECKOUT_SESSION_ID}");
        form.put("cancel_url", properties.getAppUrl() + "/?checkout=cancelled");

        // One free trial per organization
        if (organization.getStripeSubscriptionId() == null) {
            form.put("subscription_data[trial_period_days]", String.valueOf(properties.getTrialDays()));
        }

        return stripe.post("/v1/checkout/sessions", form).path("url").asString();
    }

    /**
     * Applies a finished Checkout right away, so the user doesn't have to
     * wait for the webhook after being sent back to the app.
     */
    public void completeCheckout(UUID organizationId, String sessionId) {
        requireEnabled();

        JsonNode session = stripe.get("/v1/checkout/sessions/" + encode(sessionId));

        if (!organizationId.toString().equals(session.path("client_reference_id").asString(""))) {
            throw new NotFoundException("Checkout session not found");
        }

        String subscriptionId = session.path("subscription").asString("");

        if (!subscriptionId.isBlank()) {
            syncSubscription(fetchSubscription(subscriptionId));
        }
    }

    /**
     * Copies a changed name or email to the organization's Stripe customer,
     * so invoices match. A Stripe error is logged and does not undo the
     * change in Clerova.
     */
    public void updateCustomer(Organization organization, String field, String value) {
        if (!isEnabled() || organization.getStripeCustomerId() == null) {
            return;
        }

        try {
            stripe.post("/v1/customers/" + organization.getStripeCustomerId(), Map.of(field, value));
        } catch (ApiException e) {
            log.warn("Could not update the Stripe customer of organization {}: {}",
                    organization.getId(), e.getMessage());
        }
    }

    /** @return the Stripe billing portal page for the organization */
    public String openPortal(UUID organizationId) {
        requireEnabled();

        Organization organization = findOrganization(organizationId);

        if (organization.getStripeCustomerId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_SUBSCRIPTION", "Choose a plan first.");
        }

        return stripe.post("/v1/billing_portal/sessions", Map.of(
                "customer", organization.getStripeCustomerId(),
                "return_url", properties.getAppUrl() + "/"
        )).path("url").asString();
    }

    /**
     * Moves a Starter subscription to Growth, keeping its billing period and
     * founding price. The difference for the current period is added to
     * the next invoice; during the trial nothing is charged.
     */
    public void upgradeToGrowth(UUID organizationId) {
        requireEnabled();

        Organization organization = findOrganization(organizationId);

        if (organization.getPlan() != Plan.STARTER
                || organization.getStripeSubscriptionId() == null
                || requiresSubscription(organization)) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_UPGRADE", "Only an active Starter plan can be upgraded.");
        }

        JsonNode subscription = fetchSubscription(organization.getStripeSubscriptionId());
        JsonNode item = subscription.path("items").path("data").path(0);
        String interval = item.path("price").path("recurring").path("interval").asString("month");
        boolean founding = organization.isFoundingCustomer();

        String priceId = findPrice(lookupKey(Plan.GROWTH, interval, founding))
                .or(() -> founding ? findPrice(lookupKey(Plan.GROWTH, interval, false)) : Optional.empty())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "BILLING_ERROR",
                        "Growth is not available yet. Please contact hello@clerova-ai.com."
                ));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("items[0][id]", item.path("id").asString());
        form.put("items[0][price]", priceId);
        form.put("proration_behavior", "create_prorations");

        // Upgrading means staying, so a scheduled cancellation is dropped
        form.putAll(keepForm(subscription));

        syncSubscription(stripe.post("/v1/subscriptions/" + encode(subscription.path("id").asString()), form));
    }

    /**
     * Cancels at the end of the trial or billing period. Clerova keeps
     * working until then and nothing more is charged. Safe to repeat.
     */
    public void cancelAtPeriodEnd(UUID organizationId) {
        JsonNode subscription = currentSubscription(organizationId, "You don't have a plan to cancel.");

        if (isScheduledToEnd(subscription)) {
            syncSubscription(subscription);
            return;
        }

        syncSubscription(stripe.post(
                "/v1/subscriptions/" + encode(subscription.path("id").asString()),
                Map.of("cancel_at_period_end", "true")
        ));
    }

    /** Undoes a scheduled cancellation. Safe to repeat. */
    public void keepPlan(UUID organizationId) {
        JsonNode subscription = currentSubscription(
                organizationId,
                "Your plan has ended. Choose a plan to start again."
        );

        Map<String, String> form = keepForm(subscription);

        if (form.isEmpty()) {
            syncSubscription(subscription);
            return;
        }

        syncSubscription(stripe.post("/v1/subscriptions/" + encode(subscription.path("id").asString()), form));
    }

    /** The organization's live subscription from Stripe, or a plain error. */
    private JsonNode currentSubscription(UUID organizationId, String noneMessage) {
        requireEnabled();

        Organization organization = findOrganization(organizationId);

        if (organization.getStripeSubscriptionId() == null) {
            throw new ConflictException(noneMessage);
        }

        JsonNode subscription = fetchSubscription(organization.getStripeSubscriptionId());

        if (!hasAccessStatus(subscription.path("status").asString(null))) {
            // Bring Clerova up to date (e.g. a missed webhook) before saying so
            syncSubscription(subscription);
            throw new ConflictException(noneMessage);
        }

        return subscription;
    }

    private static boolean isScheduledToEnd(JsonNode subscription) {
        return subscription.path("cancel_at_period_end").asBoolean(false)
                || subscription.path("cancel_at").isNumber();
    }

    /**
     * Clears a scheduled cancellation, set either way: cancel_at_period_end
     * (Clerova, classic billing) or cancel_at (the portal, flexible billing).
     */
    private static Map<String, String> keepForm(JsonNode subscription) {
        if (subscription.path("cancel_at_period_end").asBoolean(false)) {
            return Map.of("cancel_at_period_end", "false");
        }

        if (subscription.path("cancel_at").isNumber()) {
            return Map.of("cancel_at", "");
        }

        return Map.of();
    }

    public void handleWebhook(String payload, String signatureHeader) {
        if (!StripeSignature.isValid(payload, signatureHeader, properties.getWebhookSecret(), clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "Invalid Stripe signature");
        }

        JsonNode event = objectMapper.readTree(payload);
        String type = event.path("type").asString("");
        JsonNode object = event.path("data").path("object");

        // Events can arrive out of order, so always read the current
        // subscription from Stripe instead of trusting the event's copy.
        String subscriptionId = switch (type) {
            case "checkout.session.completed" -> object.path("subscription").asString("");
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted",
                 "customer.subscription.paused",
                 "customer.subscription.resumed" -> object.path("id").asString("");
            default -> "";
        };

        if (!subscriptionId.isBlank()) {
            syncSubscription(fetchSubscription(subscriptionId));
        }
    }

    void syncSubscription(JsonNode subscription) {
        String organizationId = subscription.path("metadata").path("organization_id").asString("");
        String customerId = subscription.path("customer").asString("");

        Optional<Organization> found = organizationId.isBlank()
                ? Optional.empty()
                : organizationRepository.findById(UUID.fromString(organizationId));

        if (found.isEmpty() && !customerId.isBlank()) {
            found = organizationRepository.findByStripeCustomerId(customerId);
        }

        if (found.isEmpty()) {
            log.warn("No organization for Stripe subscription {}", subscription.path("id").asString(""));
            return;
        }

        Organization organization = found.get();
        String subscriptionId = subscription.path("id").asString();
        String status = subscription.path("status").asString(null);

        // A late event about an old, ended subscription must not replace a
        // newer one the customer started since
        boolean ended = "canceled".equals(status) || "incomplete_expired".equals(status);

        if (ended
                && organization.getStripeSubscriptionId() != null
                && !organization.getStripeSubscriptionId().equals(subscriptionId)) {
            log.info("Ignoring ended subscription {} for organization {}", subscriptionId, organization.getId());
            return;
        }

        JsonNode item = subscription.path("items").path("data").path(0);
        JsonNode price = item.path("price");
        String lookupKey = price.path("lookup_key").asString("");

        organization.setStripeCustomerId(customerId.isBlank() ? organization.getStripeCustomerId() : customerId);
        organization.setStripeSubscriptionId(subscriptionId);
        organization.setSubscriptionStatus(status);

        // Newer API versions schedule cancellations with cancel_at instead
        // of cancel_at_period_end
        JsonNode cancelAt = subscription.path("cancel_at");
        boolean scheduledToEnd = !ended
                && (subscription.path("cancel_at_period_end").asBoolean(false) || cancelAt.isNumber());
        organization.setCancelAtPeriodEnd(scheduledToEnd);
        organization.setTrialEndsAt(epoch(subscription.path("trial_end")));
        organization.setBillingInterval(price.path("recurring").path("interval").asString(null));

        // Newer API versions keep the period on the item
        JsonNode periodEnd = item.path("current_period_end");
        organization.setCurrentPeriodEnd(epoch(periodEnd.isMissingNode() ? subscription.path("current_period_end") : periodEnd));

        if (scheduledToEnd && cancelAt.isNumber()) {
            organization.setCurrentPeriodEnd(epoch(cancelAt));
        }

        Plan plan = lookupKey.contains("growth") ? Plan.GROWTH : Plan.STARTER;
        organization.setPlan(plan);
        organization.setAiMonthlyLimit(plan == Plan.GROWTH
                ? properties.getGrowthMonthlyRequests()
                : properties.getStarterMonthlyRequests());

        if (lookupKey.startsWith("founding_")) {
            organization.setFoundingCustomer(true);
        }

        organizationRepository.save(organization);

        // An upgrade or a new plan: emails waiting for requests go now
        if (hasAccessStatus(status)) {
            inboundMessageRepository.releaseWaiting(organization.getId(), clock.instant());
        }
    }

    static String lookupKey(Plan plan, String interval, boolean founding) {
        return (founding ? "founding_" : "")
                + plan.name().toLowerCase()
                + (interval.equals("year") ? "_yearly" : "_monthly");
    }

    private String ensureCustomer(Organization organization, String email) {
        if (organization.getStripeCustomerId() != null) {
            return organization.getStripeCustomerId();
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("name", organization.getName());
        form.put("metadata[organization_id]", organization.getId().toString());

        if (email != null) {
            form.put("email", email);
        }

        String customerId = stripe.post("/v1/customers", form).path("id").asString();

        organization.setStripeCustomerId(customerId);
        organizationRepository.save(organization);

        return customerId;
    }

    private Optional<String> findPrice(String lookupKey) {
        JsonNode prices = stripe.get("/v1/prices?active=true&lookup_keys%5B%5D=" + encode(lookupKey));

        return Optional.ofNullable(prices.path("data").path(0).path("id").asString(null));
    }

    private JsonNode fetchSubscription(String subscriptionId) {
        return stripe.get("/v1/subscriptions/" + encode(subscriptionId));
    }

    private Organization findOrganization(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found: " + organizationId));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_DISABLED", "Billing is not set up yet.");
        }
    }

    private static Instant epoch(JsonNode seconds) {
        return seconds.isNumber() ? Instant.ofEpochSecond(seconds.asLong()) : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

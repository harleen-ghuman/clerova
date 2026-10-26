package com.clerova.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stripe billing settings. Billing is off until a Stripe secret key is set.
 */
@Component
public class BillingProperties {

    private final String stripeSecretKey;
    private final String webhookSecret;
    private final String stripeApiUrl;
    private final String appUrl;
    private final int trialDays;
    private final int starterMonthlyRequests;
    private final int growthMonthlyRequests;
    private final int foundingSpots;

    public BillingProperties(
            @Value("${app.billing.stripe-secret-key}") String stripeSecretKey,
            @Value("${app.billing.webhook-secret}") String webhookSecret,
            @Value("${app.billing.stripe-api-url}") String stripeApiUrl,
            @Value("${app.billing.app-url}") String appUrl,
            @Value("${app.trial.days}") int trialDays,
            @Value("${app.billing.starter-monthly-requests}") int starterMonthlyRequests,
            @Value("${app.billing.growth-monthly-requests}") int growthMonthlyRequests,
            @Value("${app.billing.founding-spots}") int foundingSpots
    ) {
        this.stripeSecretKey = stripeSecretKey.trim();
        this.webhookSecret = webhookSecret.trim();
        this.stripeApiUrl = stripeApiUrl.replaceAll("/+$", "");
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.trialDays = trialDays;
        this.starterMonthlyRequests = starterMonthlyRequests;
        this.growthMonthlyRequests = growthMonthlyRequests;
        this.foundingSpots = foundingSpots;
    }

    public boolean isEnabled() {
        return !stripeSecretKey.isBlank();
    }

    public String getStripeSecretKey() {
        return stripeSecretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getStripeApiUrl() {
        return stripeApiUrl;
    }

    /** Where Stripe sends the customer back to, e.g. https://app.clerova-ai.com */
    public String getAppUrl() {
        return appUrl;
    }

    public int getTrialDays() {
        return trialDays;
    }

    public int getStarterMonthlyRequests() {
        return starterMonthlyRequests;
    }

    public int getGrowthMonthlyRequests() {
        return growthMonthlyRequests;
    }

    public int getFoundingSpots() {
        return foundingSpots;
    }
}

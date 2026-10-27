package com.clerova.billing;

import com.clerova.domain.Plan;
import com.clerova.security.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class BillingController {

    private final BillingService billingService;
    private final CurrentUserService currentUserService;

    public BillingController(BillingService billingService, CurrentUserService currentUserService) {
        this.billingService = billingService;
        this.currentUserService = currentUserService;
    }

    public record CheckoutRequest(@NotNull Plan plan, @NotBlank String interval) {
    }

    /** What the plan picker needs before checkout. */
    @GetMapping("/api/billing/offer")
    public Map<String, Object> offer() {
        return Map.of(
                "enabled", billingService.isEnabled(),
                "foundingSpotsLeft", billingService.foundingSpotsLeft()
        );
    }

    @PostMapping("/api/billing/checkout")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest request) {
        return Map.of("url", billingService.startCheckout(
                currentUserService.requireOrganizationId(),
                currentUserService.currentEmail(),
                request.plan(),
                request.interval()
        ));
    }

    @PostMapping("/api/billing/checkout/{sessionId}/complete")
    public Map<String, Boolean> completeCheckout(@PathVariable String sessionId) {
        billingService.completeCheckout(currentUserService.requireOrganizationId(), sessionId);
        return Map.of("ok", true);
    }

    @PostMapping("/api/billing/portal")
    public Map<String, String> portal() {
        return Map.of("url", billingService.openPortal(currentUserService.requireOrganizationId()));
    }

    /** Cancels at the end of the trial or billing period. */
    @PostMapping("/api/billing/cancel")
    public Map<String, Boolean> cancel() {
        billingService.cancelAtPeriodEnd(currentUserService.requireOrganizationId());
        return Map.of("ok", true);
    }

    /** Undoes a scheduled cancellation. */
    @PostMapping("/api/billing/keep")
    public Map<String, Boolean> keep() {
        billingService.keepPlan(currentUserService.requireOrganizationId());
        return Map.of("ok", true);
    }

    @PostMapping("/api/billing/upgrade")
    public Map<String, Boolean> upgrade() {
        billingService.upgradeToGrowth(currentUserService.requireOrganizationId());
        return Map.of("ok", true);
    }

    /** Stripe webhook; authenticated by its signature, not a user token. */
    @PostMapping("/api/stripe/webhook")
    public Map<String, Boolean> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        billingService.handleWebhook(payload, signature);
        return Map.of("received", true);
    }
}

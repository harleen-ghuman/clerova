package com.clerova.service;

import com.clerova.billing.BillingService;
import com.clerova.domain.Organization;
import com.clerova.exception.ApiException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.OrganizationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Decides whether an organization may use AI: its subscription (or, without
 * billing, its trial) and its monthly request quota.
 */
@Service
public class AiUsageService {

    private final OrganizationRepository organizationRepository;
    private final BillingService billingService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AiUsageService(
            OrganizationRepository organizationRepository,
            BillingService billingService,
            ApplicationEventPublisher events
    ) {
        this.organizationRepository = organizationRepository;
        this.billingService = billingService;
        this.events = events;
        this.clock = Clock.systemUTC();
    }

    /**
     * Counts one new request (an email or a typed request) against the
     * monthly quota, or throws if the organization can't use AI.
     */
    @Transactional
    public void consumeRequest(UUID organizationId) {
        requireAccess(organizationId);

        String period = YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();

        if (organizationRepository.tryConsumeAiRequest(organizationId, period) == 0) {
            throw quotaExceeded();
        }

        // Each count is reached by exactly one request, so this fires once per month
        int used = organizationRepository.findUsageCount(organizationId);
        int limit = organizationRepository.findById(organizationId)
                .map(Organization::getAiMonthlyLimit)
                .orElse(0);

        if (used == limit || used == warningThreshold(limit)) {
            events.publishEvent(new UsageThresholdReached(organizationId, used, limit));
        }
    }

    /**
     * Throws unless the organization may use AI and has a request left this
     * month, without counting one. Call before the AI reads a new request and
     * {@link #consumeRequest} once it has, so a failed AI call (and its
     * retries) doesn't use up the plan.
     */
    @Transactional(readOnly = true)
    public void requireQuota(UUID organizationId) {
        requireAccess(organizationId);

        Organization organization = organizationRepository.findById(organizationId).orElseThrow();
        String period = YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();
        int limit = organization.getAiMonthlyLimit() != null ? organization.getAiMonthlyLimit() : 0;
        int used = period.equals(organization.getAiUsagePeriod()) && organization.getAiUsageCount() != null
                ? organization.getAiUsageCount()
                : 0;

        if (limit <= 0 || used >= limit) {
            throw quotaExceeded();
        }
    }

    private static ApiException quotaExceeded() {
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI_QUOTA_EXCEEDED",
                "You have used all requests included in your plan this month."
        );
    }

    /** Usage at which to warn that the monthly limit is close. */
    public static int warningThreshold(int limit) {
        return (int) Math.ceil(limit * 0.8);
    }

    /**
     * Throws unless the organization may use AI. Suggestions for an existing
     * request don't count against the quota.
     */
    @Transactional(readOnly = true)
    public void requireAccess(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException(
                        "Organization not found: " + organizationId
                ));

        if (billingService.isEnabled()) {
            billingService.requireAccess(organization);
            return;
        }

        if (organization.isTrialExpired(clock.instant())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TRIAL_EXPIRED",
                    "Your free trial has ended. Contact us to keep using Clerova."
            );
        }
    }
}

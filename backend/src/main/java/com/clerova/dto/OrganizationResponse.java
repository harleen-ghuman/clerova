package com.clerova.dto;

import com.clerova.domain.Industry;
import com.clerova.domain.Organization;
import com.clerova.domain.Plan;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        Industry industry,
        Plan plan,
        Instant trialEndsAt,
        int aiMonthlyLimit,
        int aiRequestsUsedThisMonth,
        String subscriptionStatus,
        String billingInterval,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        boolean foundingCustomer,
        boolean billingEnabled,
        /** True when a plan must be chosen before using Clerova. */
        boolean subscriptionRequired
) {

    public static OrganizationResponse from(
            Organization organization,
            boolean billingEnabled,
            boolean subscriptionRequired
    ) {
        String currentPeriod = YearMonth.now(ZoneOffset.UTC).toString();

        int used = currentPeriod.equals(organization.getAiUsagePeriod())
                ? organization.getAiUsageCount()
                : 0;

        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getIndustry(),
                organization.getPlan(),
                organization.getTrialEndsAt(),
                organization.getAiMonthlyLimit(),
                used,
                organization.getSubscriptionStatus(),
                organization.getBillingInterval(),
                organization.getCurrentPeriodEnd(),
                organization.isCancelAtPeriodEnd(),
                organization.isFoundingCustomer(),
                billingEnabled,
                subscriptionRequired
        );
    }
}

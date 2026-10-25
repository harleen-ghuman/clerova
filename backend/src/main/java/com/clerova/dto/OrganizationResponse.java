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
        int aiRequestsUsedThisMonth
) {

    public static OrganizationResponse from(Organization organization) {
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
                used
        );
    }
}

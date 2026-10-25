package com.clerova.service;

import com.clerova.domain.Organization;
import com.clerova.exception.ApiException;
import com.clerova.exception.NotFoundException;
import com.clerova.repository.OrganizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Enforces each organization's trial period and monthly AI request quota.
 */
@Service
public class AiUsageService {

    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    public AiUsageService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
        this.clock = Clock.systemUTC();
    }

    /**
     * Counts one AI request for the organization, or throws if its trial
     * has ended or its monthly quota is used up.
     */
    @Transactional
    public void consumeAiRequest(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException(
                        "Organization not found: " + organizationId
                ));

        Instant now = clock.instant();

        if (organization.isTrialExpired(now)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TRIAL_EXPIRED",
                    "Your free trial has ended. Contact us to keep using Clerova."
            );
        }

        String period = YearMonth.now(clock.withZone(ZoneOffset.UTC)).toString();

        if (organizationRepository.tryConsumeAiRequest(organizationId, period) == 0) {
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_QUOTA_EXCEEDED",
                    "You have used all AI requests included in your plan this month."
            );
        }
    }
}

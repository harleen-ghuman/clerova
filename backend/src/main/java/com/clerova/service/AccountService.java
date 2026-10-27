package com.clerova.service;

import com.clerova.exception.ConflictException;
import com.clerova.billing.BillingService;
import com.clerova.domain.AppUser;
import com.clerova.domain.Organization;
import com.clerova.domain.Plan;
import com.clerova.domain.UserRole;
import com.clerova.dto.MeResponse;
import com.clerova.dto.OnboardingRequest;
import com.clerova.dto.OrganizationResponse;
import com.clerova.dto.UpdateAccountRequest;
import com.clerova.repository.AppUserRepository;
import com.clerova.repository.OrganizationRepository;
import com.clerova.security.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AccountService {

    private final CurrentUserService currentUserService;
    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final BillingService billingService;
    private final int trialDays;
    private final int trialAiMonthlyLimit;

    public AccountService(
            CurrentUserService currentUserService,
            OrganizationRepository organizationRepository,
            AppUserRepository appUserRepository,
            BillingService billingService,
            @Value("${app.trial.days}") int trialDays,
            @Value("${app.trial.ai-monthly-limit}") int trialAiMonthlyLimit
    ) {
        this.currentUserService = currentUserService;
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.billingService = billingService;
        this.trialDays = trialDays;
        this.trialAiMonthlyLimit = trialAiMonthlyLimit;
    }

    /**
     * The signed-in user. A changed sign-in email (confirmed in Supabase)
     * is copied to the user and to Stripe here.
     */
    @Transactional
    public MeResponse getMe() {
        return currentUserService.findCurrentUser()
                .map(this::syncEmail)
                .map(this::toMeResponse)
                .orElseGet(() -> new MeResponse(
                        currentUserService.currentUserId(),
                        currentUserService.currentEmail(),
                        null,
                        null
                ));
    }

    /**
     * Creates a trial organization for a newly signed-up user and makes
     * them its owner. Each user belongs to exactly one organization.
     */
    @Transactional
    public MeResponse completeOnboarding(OnboardingRequest request) {

        if (currentUserService.findCurrentUser().isPresent()) {
            throw new ConflictException(
                    "Your workspace is already set up."
            );
        }

        Organization organization = new Organization();

        organization.setName(request.organizationName().trim());
        organization.setIndustry(request.industry());
        organization.setCompanySize(blankToNull(request.companySize()));
        organization.setReferralSource(blankToNull(request.referralSource()));
        organization.setPlan(Plan.TRIAL);
        organization.setTrialEndsAt(Instant.now().plus(Duration.ofDays(trialDays)));
        organization.setAiMonthlyLimit(trialAiMonthlyLimit);

        Organization savedOrganization = organizationRepository.save(organization);

        AppUser user = new AppUser();

        user.setId(currentUserService.currentUserId());
        user.setEmail(currentUserService.currentEmail());
        user.setFullName(blankToNull(request.fullName()));
        user.setOrganization(savedOrganization);
        user.setRole(UserRole.OWNER);

        return toMeResponse(appUserRepository.save(user));
    }

    @Transactional
    public MeResponse updateAccount(UpdateAccountRequest request) {
        AppUser user = syncEmail(currentUserService.requireUser());
        Organization organization = user.getOrganization();
        String organizationName = request.organizationName().trim();

        // Left out means unchanged; an empty string clears it
        if (request.fullName() != null) {
            user.setFullName(blankToNull(request.fullName()));
        }

        if (!organizationName.equals(organization.getName())) {
            organization.setName(organizationName);
            billingService.updateCustomer(organization, "name", organizationName);
        }

        return toMeResponse(user);
    }

    private AppUser syncEmail(AppUser user) {
        String email = currentUserService.currentEmail();

        if (!email.isBlank() && !email.equalsIgnoreCase(user.getEmail())) {
            user.setEmail(email);
            billingService.updateCustomer(user.getOrganization(), "email", email);
        }

        return user;
    }

    private MeResponse toMeResponse(AppUser user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                OrganizationResponse.from(
                        user.getOrganization(),
                        billingService.isEnabled(),
                        billingService.requiresSubscription(user.getOrganization())
                )
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

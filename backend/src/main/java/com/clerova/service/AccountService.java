package com.clerova.service;

import com.clerova.exception.ApiException;
import com.clerova.exception.ConflictException;
import com.clerova.billing.BillingService;
import com.clerova.email.InboundAddressService;
import com.clerova.domain.AppUser;
import com.clerova.domain.Industry;
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
import org.springframework.http.HttpStatus;
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
    private final InboundAddressService inboundAddressService;
    private final int trialDays;
    private final int trialAiMonthlyLimit;
    private final boolean truckingEnabled;

    public AccountService(
            CurrentUserService currentUserService,
            OrganizationRepository organizationRepository,
            AppUserRepository appUserRepository,
            BillingService billingService,
            InboundAddressService inboundAddressService,
            @Value("${app.trial.days}") int trialDays,
            @Value("${app.trial.ai-monthly-limit}") int trialAiMonthlyLimit,
            @Value("${app.trucking.enabled:false}") boolean truckingEnabled
    ) {
        this.truckingEnabled = truckingEnabled;
        this.currentUserService = currentUserService;
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.billingService = billingService;
        this.inboundAddressService = inboundAddressService;
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

        // Trucking is being built; it opens to sign-ups when it's ready
        if (request.industry() == Industry.TRUCKING && !truckingEnabled) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INDUSTRY_NOT_AVAILABLE",
                    "Clerova for trucking isn't open for sign-ups yet.");
        }

        Organization organization = new Organization();

        organization.setName(request.organizationName().trim());
        organization.setIndustry(request.industry());
        // Canadian carriers pay in CAD; only trucking is offered in Canada
        organization.setCountry(request.industry() == Industry.TRUCKING && "CA".equalsIgnoreCase(request.country()) ? "CA" : "US");
        organization.setCompanySize(blankToNull(request.companySize()));
        organization.setReferralSource(blankToNull(request.referralSource()));
        organization.setPlan(Plan.TRIAL);
        organization.setTrialEndsAt(Instant.now().plus(Duration.ofDays(trialDays)));
        organization.setAiMonthlyLimit(trialAiMonthlyLimit);
        // The forwarding address exists from the start
        organization.setInboundEmailToken(inboundAddressService.newToken(organization.getName()));

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

    /**
     * The workspace's time zone (IANA, like America/Chicago), used for invoice
     * dates, due dates and the monthly export. The app sends the device's
     * zone the first time; Settings can change it.
     */
    @Transactional
    public MeResponse setTimeZone(String timeZone) {
        AppUser user = currentUserService.requireUser();
        String zone = timeZone == null ? "" : timeZone.strip();

        if (zone.isEmpty() || zone.length() > 64 || !java.time.ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_ZONE", "Pick a time zone from the list.");
        }

        user.getOrganization().setTimeZone(zone);
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

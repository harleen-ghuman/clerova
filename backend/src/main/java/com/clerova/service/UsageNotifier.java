package com.clerova.service;

import com.clerova.billing.BillingProperties;
import com.clerova.domain.AppUser;
import com.clerova.domain.Organization;
import com.clerova.domain.Plan;
import com.clerova.domain.UserRole;
import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSender;
import com.clerova.email.OutgoingEmail;
import com.clerova.repository.AppUserRepository;
import com.clerova.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Emails the organization's owners when they reach 80% and 100% of their
 * monthly requests, and suggests upgrading.
 */
@Component
public class UsageNotifier {

    private static final Logger log = LoggerFactory.getLogger(UsageNotifier.class);

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository appUserRepository;
    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final BillingProperties billingProperties;
    private final String supportAddress;

    public UsageNotifier(
            OrganizationRepository organizationRepository,
            AppUserRepository appUserRepository,
            EmailProperties emailProperties,
            EmailSender emailSender,
            BillingProperties billingProperties,
            @Value("${app.email.support-address}") String supportAddress
    ) {
        this.organizationRepository = organizationRepository;
        this.appUserRepository = appUserRepository;
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
        this.billingProperties = billingProperties;
        this.supportAddress = supportAddress;
    }

    // After commit, so a slow or failing email never affects the request
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThresholdReached(UsageThresholdReached event) {
        if (!emailProperties.isSendingEnabled()) {
            return;
        }

        try {
            Organization organization = organizationRepository.findById(event.organizationId())
                    .orElseThrow();

            for (AppUser owner : appUserRepository.findByOrganization_IdAndRole(
                    organization.getId(), UserRole.OWNER)) {
                emailSender.send(new OutgoingEmail(
                        "Clerova <notifications@" + emailProperties.getInboundDomain() + ">",
                        supportAddress,
                        owner.getEmail(),
                        subject(event, organization.getIndustry() == com.clerova.domain.Industry.TRUCKING),
                        body(organization, event),
                        null,
                        null
                ));
            }
        } catch (RuntimeException e) {
            log.warn("Could not send the usage email for organization {}: {}",
                    event.organizationId(), e.getMessage());
        }
    }

    static String subject(UsageThresholdReached event, boolean trucking) {
        String unit = trucking ? "loads" : "requests";
        return event.limitReached()
                ? "You've reached your Clerova " + unit + " for this month"
                : "You've used 80% of your Clerova " + unit + " this month";
    }

    String body(Organization organization, UsageThresholdReached event) {
        String resetDate = LocalDate.now(ZoneOffset.UTC).plusMonths(1).withDayOfMonth(1)
                .format(DateTimeFormatter.ofPattern("MMMM d", Locale.US));
        String app = billingProperties.getAppUrl();

        boolean trucking = organization.getIndustry() == com.clerova.domain.Industry.TRUCKING;
        String unit = trucking ? "loads" : "requests";
        String usage = event.limitReached()
                ? "%s has used all %d %s included this month. New emails are saved and will be organized on %s."
                        .formatted(organization.getName(), event.limit(), unit, resetDate)
                : "%s has used %d of the %d %s included this month. At %d, new emails wait until %s."
                        .formatted(organization.getName(), event.used(), event.limit(), unit, event.limit(), resetDate);

        String next = trucking
                ? "Running more trucks? Update your trucks in Clerova's Settings: %s".formatted(app)
                : organization.getPlan() == Plan.STARTER
                ? "To keep going, open Clerova and click Upgrade to Growth (%d requests a month): %s"
                        .formatted(billingProperties.getGrowthMonthlyRequests(), app)
                : "Need more? Reply to this email and we'll help.";

        String retry = event.limitReached() && organization.getPlan() == Plan.STARTER && !trucking
                ? "\n\nAfter you upgrade, waiting emails are organized right away."
                : "";

        return "Hi,\n\n" + usage + "\n\n" + next + retry + "\n\nThe Clerova team";
    }
}

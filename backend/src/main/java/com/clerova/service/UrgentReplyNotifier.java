package com.clerova.service;

import com.clerova.billing.BillingProperties;
import com.clerova.domain.AppUser;
import com.clerova.domain.UserRole;
import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSender;
import com.clerova.email.OutgoingEmail;
import com.clerova.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails the workspace owners when a resident's new email during a job
 * looks urgent, so it isn't missed while nobody has Clerova open.
 */
@Component
public class UrgentReplyNotifier {

    private static final Logger log = LoggerFactory.getLogger(UrgentReplyNotifier.class);

    private static final int MAX_MESSAGE_CHARS = 2_000;

    private final AppUserRepository appUserRepository;
    private final EmailProperties emailProperties;
    private final EmailSender emailSender;
    private final BillingProperties billingProperties;
    private final String supportAddress;

    public UrgentReplyNotifier(
            AppUserRepository appUserRepository,
            EmailProperties emailProperties,
            EmailSender emailSender,
            BillingProperties billingProperties,
            @Value("${app.email.support-address}") String supportAddress
    ) {
        this.appUserRepository = appUserRepository;
        this.emailProperties = emailProperties;
        this.emailSender = emailSender;
        this.billingProperties = billingProperties;
        this.supportAddress = supportAddress;
    }

    // After commit, so a slow or failing email never affects the request
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUrgentReply(UrgentReplyReceived event) {
        if (!emailProperties.isSendingEnabled()) {
            return;
        }

        try {
            for (AppUser owner : appUserRepository.findByOrganization_IdAndRole(
                    event.organizationId(), UserRole.OWNER)) {
                emailSender.send(new OutgoingEmail(
                        "Clerova <notifications@" + emailProperties.getInboundDomain() + ">",
                        supportAddress,
                        owner.getEmail(),
                        subject(event),
                        body(event),
                        null,
                        null
                ));
            }
        } catch (RuntimeException e) {
            log.warn("Could not send the urgent reply email for work item {}: {}",
                    event.workItemId(), e.getMessage());
        }
    }

    static String subject(UrgentReplyReceived event) {
        return oneLine("Urgent: " + label(event.sender()) + " wrote again about " + label(event.title()));
    }

    String body(UrgentReplyReceived event) {
        String message = event.message().length() > MAX_MESSAGE_CHARS
                ? event.message().substring(0, MAX_MESSAGE_CHARS) + "..."
                : event.message();

        return "Hi,\n\n"
                + label(event.sender()) + " wrote again about \"" + oneLine(label(event.title()))
                + "\" while the job is in progress, and it looks urgent: " + event.reason() + "\n\n"
                + "Their message:\n\n" + message.trim() + "\n\n"
                + "Open Clerova to reply: " + billingProperties.getAppUrl() + "\n\n"
                + "You get this email only when a new email during a job looks urgent.\n\n"
                + "The Clerova team";
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "A resident" : value.trim();
    }

    private static String oneLine(String value) {
        String line = value.replaceAll("[\\r\\n]+", " ");
        return line.length() > 150 ? line.substring(0, 147) + "..." : line;
    }
}

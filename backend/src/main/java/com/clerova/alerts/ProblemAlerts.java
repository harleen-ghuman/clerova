package com.clerova.alerts;

import com.clerova.domain.Organization;
import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSender;
import com.clerova.email.OutgoingEmail;
import com.clerova.repository.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tells the Clerova team by email when something breaks, so a problem is
 * seen the day it happens instead of when a customer writes in. Every
 * problem is also logged. The same kind of problem is emailed at most once
 * every 30 minutes and a few times a day, so a bad hour can't flood the inbox.
 */
@Component
public class ProblemAlerts {

    public enum Kind {
        EMAIL_FAILED("A customer's email could not be read"),
        AI_DOWN("The AI service is failing"),
        EMAIL_NOT_SENT("The email service could not send"),
        BILLING("Stripe is failing"),
        WEBHOOK_REFUSED("A webhook was refused"),
        SERVER_ERROR("The server hit an error"),
        SCREEN_ERROR("A screen broke for a customer"),
        NO_EMAIL("No email has reached Clerova in 24 hours");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    static final Duration QUIET = Duration.ofMinutes(30);
    static final int MAX_PER_KIND_PER_DAY = 6;
    private static final String THREAD = "problem-alerts";
    private static final int MAX_DETAIL = 1200;

    private static final Logger log = LoggerFactory.getLogger(ProblemAlerts.class);

    private final ObjectProvider<EmailSender> emailSender;
    private final EmailProperties emailProperties;
    private final OrganizationRepository organizationRepository;
    private final String alertAddress;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, THREAD);
        thread.setDaemon(true);
        return thread;
    });

    private final Map<Kind, State> states = new EnumMap<>(Kind.class);

    private static final class State {
        Instant lastSent;
        LocalDate day;
        int sentToday;
        int held;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProblemAlerts(
            ObjectProvider<EmailSender> emailSender,
            EmailProperties emailProperties,
            OrganizationRepository organizationRepository,
            @Value("${app.alerts.email:}") String alertAddress
    ) {
        this(emailSender, emailProperties, organizationRepository, alertAddress, Clock.systemUTC());
    }

    ProblemAlerts(
            ObjectProvider<EmailSender> emailSender,
            EmailProperties emailProperties,
            OrganizationRepository organizationRepository,
            String alertAddress,
            Clock clock
    ) {
        this.emailSender = emailSender;
        this.emailProperties = emailProperties;
        this.organizationRepository = organizationRepository;
        this.alertAddress = alertAddress == null ? "" : alertAddress.trim();
        this.clock = clock;
    }

    /**
     * @param what one plain sentence on what went wrong
     * @param organizationId the workspace it happened in, if known
     * @param where the page, email or request it happened on, if known
     * @param cause the error behind it, if any
     */
    public void report(Kind kind, String what, UUID organizationId, String where, Throwable cause) {
        String detail = detail(cause);
        log.warn("PROBLEM {}: {} | workspace={} | where={} | {}", kind, what, organizationId, where, detail);

        // An alert that can't be sent must not raise another alert
        if (alertAddress.isBlank() || !emailProperties.isSendingEnabled() || THREAD.equals(Thread.currentThread().getName())) {
            return;
        }

        int held = takeTurn(kind);

        if (held < 0) {
            return;
        }

        Instant when = clock.instant();

        try {
            executor.execute(() -> send(kind, what, organizationId, where, detail, when, held));
        } catch (RuntimeException e) {
            log.warn("Could not queue a problem alert: {}", e.toString());
        }
    }

    public void report(Kind kind, String what, UUID organizationId, Throwable cause) {
        report(kind, what, organizationId, null, cause);
    }

    /** How many alerts of this kind were held back since the last one sent, or -1 to hold this one too. */
    synchronized int takeTurn(Kind kind) {
        State state = states.computeIfAbsent(kind, k -> new State());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        if (!today.equals(state.day)) {
            state.day = today;
            state.sentToday = 0;
        }

        boolean tooSoon = state.lastSent != null && now.isBefore(state.lastSent.plus(QUIET));

        if (tooSoon || state.sentToday >= MAX_PER_KIND_PER_DAY) {
            state.held++;
            return -1;
        }

        int held = state.held;
        state.held = 0;
        state.lastSent = now;
        state.sentToday++;
        return held;
    }

    private void send(Kind kind, String what, UUID organizationId, String where, String detail, Instant when, int held) {
        try {
            StringBuilder body = new StringBuilder();
            body.append(what).append("\n\n");
            body.append("Workspace: ").append(workspace(organizationId)).append("\n");

            if (where != null && !where.isBlank()) {
                body.append("Where: ").append(where).append("\n");
            }

            body.append("When: ").append(when).append(" (UTC)\n");

            if (!detail.isBlank()) {
                body.append("\nDetails:\n").append(detail).append("\n");
            }

            if (held > 0) {
                body.append("\n").append(held).append(" more like this happened since the last alert and were not emailed.\n");
            }

            body.append("\nThe same kind of problem is emailed at most once every 30 minutes. ")
                    .append("Search the server logs for \"PROBLEM ").append(kind).append("\" to see each one.\n");

            emailSender.getObject().send(new OutgoingEmail(
                    "Clerova alerts <alerts@" + emailProperties.getInboundDomain() + ">",
                    null,
                    alertAddress,
                    "Clerova problem: " + kind.title(),
                    body.toString(),
                    null,
                    null
            ));
        } catch (RuntimeException e) {
            log.warn("Could not send a problem alert: {}", e.toString());
        }
    }

    private String workspace(UUID organizationId) {
        if (organizationId == null) {
            return "not known";
        }

        try {
            return organizationRepository.findById(organizationId)
                    .map(Organization::getName)
                    .map(name -> name + " (" + organizationId + ")")
                    .orElse(organizationId.toString());
        } catch (RuntimeException e) {
            return organizationId.toString();
        }
    }

    /** The innermost error, which is the one that says what really happened. */
    static String detail(Throwable cause) {
        if (cause == null) {
            return "";
        }

        Throwable root = cause;

        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < 10; depth++) {
            root = root.getCause();
        }

        String text = root.getClass().getSimpleName() + ": " + root.getMessage();
        // Keys and tokens never belong in an email
        text = text.replaceAll("(?i)\\b(sk|pk|rk|whsec)[_-][A-Za-z0-9_\\-*]{8,}", "[hidden]")
                .replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]{12,}", "Bearer [hidden]");
        return text.length() > MAX_DETAIL ? text.substring(0, MAX_DETAIL) + "..." : text;
    }
}

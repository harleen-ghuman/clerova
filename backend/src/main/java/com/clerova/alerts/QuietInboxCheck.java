package com.clerova.alerts;

import com.clerova.repository.InboundMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Once a day: has any email reached Clerova in the last 24 hours? Email
 * stopping entirely (a provider plan, a wrong webhook) fails silently, with
 * no error anywhere, so this is the only thing that notices it.
 */
@Component
public class QuietInboxCheck {

    private final InboundMessageRepository inboundMessageRepository;
    private final ProblemAlerts alerts;
    private final boolean enabled;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public QuietInboxCheck(
            InboundMessageRepository inboundMessageRepository,
            ProblemAlerts alerts,
            @Value("${app.email.processing.enabled}") boolean enabled
    ) {
        this(inboundMessageRepository, alerts, enabled, Clock.systemUTC());
    }

    QuietInboxCheck(InboundMessageRepository inboundMessageRepository, ProblemAlerts alerts, boolean enabled, Clock clock) {
        this.inboundMessageRepository = inboundMessageRepository;
        this.alerts = alerts;
        this.enabled = enabled;
        this.clock = clock;
    }

    // 8 AM Pacific in winter, 9 AM in summer
    @Scheduled(cron = "0 0 16 * * *", zone = "UTC")
    public void runDaily() {
        if (enabled) {
            check();
        }
    }

    /**
     * Alerts on the first quiet day only: the newest email is between one
     * and two days old. A longer silence was already reported, and a
     * workspace that never got email has nothing to miss.
     */
    boolean check() {
        Instant latest = inboundMessageRepository.findLatestReceivedAt();

        if (latest == null) {
            return false;
        }

        Duration quiet = Duration.between(latest, clock.instant());

        if (quiet.compareTo(Duration.ofHours(24)) < 0 || quiet.compareTo(Duration.ofHours(48)) >= 0) {
            return false;
        }

        alerts.report(
                ProblemAlerts.Kind.NO_EMAIL,
                "No email has reached Clerova since " + latest + " (UTC). That can be a quiet day. "
                        + "If customers are forwarding email, check Postmark: the inbound stream's Activity page, "
                        + "the plan, and the webhook. Sending one test email to a Clerova address settles it.",
                null,
                null,
                null
        );

        return true;
    }
}

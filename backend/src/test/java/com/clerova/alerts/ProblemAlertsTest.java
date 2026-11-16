package com.clerova.alerts;

import com.clerova.email.EmailProperties;
import com.clerova.email.EmailSender;
import com.clerova.email.OutgoingEmail;
import com.clerova.repository.InboundMessageRepository;
import com.clerova.repository.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProblemAlertsTest {

    /** A clock the test moves by hand. */
    private static final class MovingClock extends Clock {
        Instant now = Instant.parse("2026-11-16T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovingClock clock = new MovingClock();
    private final EmailSender sender = mock(EmailSender.class);
    private final EmailProperties emailProperties = mock(EmailProperties.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private ProblemAlerts alerts;

    @SuppressWarnings("unchecked")
    private ProblemAlerts alerts(String address) {
        ObjectProvider<EmailSender> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sender);
        return new ProblemAlerts(provider, emailProperties, organizations, address, clock);
    }

    @BeforeEach
    void setUp() {
        when(emailProperties.isSendingEnabled()).thenReturn(true);
        when(emailProperties.getInboundDomain()).thenReturn("in.test.clerova");
        when(organizations.findById(any())).thenReturn(Optional.empty());
        alerts = alerts("team@example.com");
    }

    @Test
    void emailsTheTeamWithWhatWhereAndTheRealCause() {
        UUID workspace = UUID.randomUUID();
        RuntimeException cause = new RuntimeException("wrapper", new IllegalStateException("429: You have no credits remaining."));

        alerts.report(ProblemAlerts.Kind.AI_DOWN, "The AI service failed while reading an email.", workspace, "Email: Rate con 5521", cause);

        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(sender, timeout(2000)).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo("team@example.com");
        assertThat(email.getValue().subject()).isEqualTo("Clerova problem: The AI service is failing");
        assertThat(email.getValue().textBody())
                .contains("The AI service failed while reading an email.")
                .contains(workspace.toString())
                .contains("Email: Rate con 5521")
                .contains("no credits remaining");
    }

    @Test
    void theSameKindOfProblemIsEmailedOnceInThirtyMinutes() {
        for (int i = 0; i < 5; i++) {
            alerts.report(ProblemAlerts.Kind.SERVER_ERROR, "boom", null, null);
        }

        verify(sender, timeout(2000).times(1)).send(any());

        clock.now = clock.now.plus(Duration.ofMinutes(31));
        alerts.report(ProblemAlerts.Kind.SERVER_ERROR, "boom again", null, null);

        ArgumentCaptor<OutgoingEmail> email = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(sender, timeout(2000).times(2)).send(email.capture());
        assertThat(email.getValue().textBody()).contains("4 more like this happened since the last alert");
    }

    @Test
    void aDifferentKindIsNotHeldBack() {
        alerts.report(ProblemAlerts.Kind.SERVER_ERROR, "boom", null, null);
        alerts.report(ProblemAlerts.Kind.EMAIL_FAILED, "an email failed", null, null);

        verify(sender, timeout(2000).times(2)).send(any());
    }

    @Test
    void aBadDayStopsAtSixOfOneKind() {
        for (int i = 0; i < 12; i++) {
            alerts.report(ProblemAlerts.Kind.WEBHOOK_REFUSED, "refused", null, null);
            clock.now = clock.now.plus(Duration.ofMinutes(31));
        }

        verify(sender, after(500).times(ProblemAlerts.MAX_PER_KIND_PER_DAY)).send(any());
    }

    @Test
    void nothingIsSentWithoutAnAddressOrWhenEmailIsOff() {
        alerts("").report(ProblemAlerts.Kind.SERVER_ERROR, "boom", null, null);

        when(emailProperties.isSendingEnabled()).thenReturn(false);
        alerts.report(ProblemAlerts.Kind.SERVER_ERROR, "boom", null, null);

        verify(sender, after(300).never()).send(any());
    }

    @Test
    void aFailingAlertEmailNeverBreaksTheCaller() {
        when(sender.send(any())).thenThrow(new RuntimeException("Postmark is down"));

        alerts.report(ProblemAlerts.Kind.EMAIL_NOT_SENT, "could not send", null, null);

        verify(sender, timeout(2000)).send(any());
    }

    @Test
    void keysAreHiddenInDetails() {
        String detail = ProblemAlerts.detail(new RuntimeException(
                "Invalid API key sk_live_abcdefgh12345678 and Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature"));

        assertThat(detail).doesNotContain("abcdefgh12345678").doesNotContain("eyJhbGciOiJIUzI1NiJ9").contains("[hidden]");
        assertThat(ProblemAlerts.detail(new RuntimeException("Incorrect API key provided: sk-proj-Ab12****************9xYz.")))
                .doesNotContain("sk-proj").contains("[hidden]");
    }

    @Test
    void quietCheckAlertsOnTheFirstQuietDayOnly() {
        InboundMessageRepository messages = mock(InboundMessageRepository.class);
        ProblemAlerts spy = mock(ProblemAlerts.class);
        QuietInboxCheck check = new QuietInboxCheck(messages, spy, true, clock);

        when(messages.findLatestReceivedAt()).thenReturn(null);
        assertThat(check.check()).as("no email ever").isFalse();

        when(messages.findLatestReceivedAt()).thenReturn(clock.now.minus(Duration.ofHours(3)));
        assertThat(check.check()).as("email today").isFalse();

        when(messages.findLatestReceivedAt()).thenReturn(clock.now.minus(Duration.ofHours(30)));
        assertThat(check.check()).as("first quiet day").isTrue();

        when(messages.findLatestReceivedAt()).thenReturn(clock.now.minus(Duration.ofHours(60)));
        assertThat(check.check()).as("already reported").isFalse();

        verify(spy).report(any(), any(), any(), any(), any());
        verify(spy, never()).report(any(), any(), any(), any());
    }
}

package com.clerova.trucking;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The real AI answering brokers: only known facts, no promises the dispatcher didn't make. */
@SpringBootTest
@Tag("ai-evaluation")
class AiBrokerReplyEvaluationTest {

    private static final Pattern CLOCK_TIME = Pattern.compile("(?i)\\b\\d{1,2}(:\\d{2})?\\s?(am|pm)\\b|\\b\\d{1,2}:\\d{2}\\b");

    @Autowired
    private TruckingAiService truckingAiService;

    private static String facts(String status, String note) {
        return """
                Load number: 88213
                Broker: Crestline Freight
                Status: %s
                Dispatcher's latest update: %s
                Equipment: Dry van 53'
                Pickup: Sunridge Packing, Fresno, CA, appointment Nov 4, 8:00-10:00 AM
                Delivery: Desert Foods DC, Phoenix, AZ, appointment Nov 5, 7:00 AM
                """.formatted(status, note);
    }

    @Test
    void answersAStatusQuestionFromTheDispatchersUpdate() {
        BrokerReplyResult result = truckingAiService.draftBrokerReply(
                facts("picked up, on the way to delivery", "Loaded 9:40 AM. ETA Phoenix 6 AM tomorrow."),
                "", "From: Dana Ruiz <dana@crestline.example>\nSubject: 88213 status\n\nHi, where's the truck? Receiver is asking for an ETA. Thanks, Dana");

        assertThat(result.needsReply).isTrue();
        assertThat(result.kind).isEqualTo(ReplyKind.STATUS);
        assertThat(result.draft).contains("6");
        assertThat(result.draft.toLowerCase()).startsWith("hi dana");
        assertThat(result.draft).doesNotContain("Sierra Run");
    }

    @Test
    void neverInventsAnEtaItDoesntHave() {
        BrokerReplyResult result = truckingAiService.draftBrokerReply(
                facts("picked up, on the way to delivery", "(not known)"),
                "", "From: Dana <dana@crestline.example>\nSubject: ETA?\n\nWhat's your ETA to Phoenix on 88213?");

        assertThat(result.needsReply).isTrue();
        assertThat(CLOCK_TIME.matcher(result.draft.replace("7:00 AM", "")).find())
                .as("No made-up time in: " + result.draft).isFalse();
        assertThat(result.draft.toLowerCase()).containsAnyOf("check", "driver", "update");
    }

    @Test
    void neverAgreesToALowerRate() {
        BrokerReplyResult result = truckingAiService.draftBrokerReply(
                facts("booked, not picked up yet", "(not known)"),
                "", "From: Dana <dana@crestline.example>\nSubject: 88213 rate\n\nShipper cut the budget. Can you do $1,900 instead of $2,150?");

        assertThat(result.needsReply).isTrue();
        assertThat(result.kind).isEqualTo(ReplyKind.RATE);
        String draft = result.draft.toLowerCase();
        assertThat(draft).doesNotContain("we accept").doesNotContain("that works").doesNotContain("agreed")
                .doesNotContain("we can do $1,900")
                // Turning it down is the dispatcher's call too
                .doesNotContain("can't approve").doesNotContain("cannot approve").doesNotContain("can't accept")
                .doesNotContain("cannot accept").doesNotContain("decline").doesNotContain("not able to");
        assertThat(draft).containsAnyOf("confirm", "get back", "check");
    }

    @Test
    void doesntPromisePaperworkItDoesntHave() {
        BrokerReplyResult result = truckingAiService.draftBrokerReply(
                facts("delivered", "(not known)"),
                "", "From: Billing <billing@crestline.example>\nSubject: POD 88213\n\nPlease send the signed POD for 88213 so we can process payment.");

        assertThat(result.needsReply).isTrue();
        assertThat(result.kind).isEqualTo(ReplyKind.PAPERWORK);
        assertThat(result.draft.toLowerCase()).doesNotContain("attached");
    }

    @Test
    void aThankYouNeedsNoReply() {
        BrokerReplyResult result = truckingAiService.draftBrokerReply(
                facts("booked, not picked up yet", "(not known)"),
                "From the carrier: Signed rate con attached.",
                "From: Dana <dana@crestline.example>\nSubject: Re: 88213\n\nGot it, thanks!");

        assertThat(result.needsReply).isFalse();
    }
}

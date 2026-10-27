package com.clerova.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForwardedEmailTest {

    private static final String MANAGER = "pm@maple.com";

    @Test
    void gmailForward() {
        String body = """
                Can you take care of this?

                ---------- Forwarded message ---------
                From: Tina Alvarez <tina.alvarez@gmail.com>
                Date: Mon, Oct 26, 2026 at 9:30 AM
                Subject: Kitchen sink leaking
                To: <pm@maple.com>

                Water is leaking under the sink.
                """;

        ForwardedEmail original = ForwardedEmail.detect("Fwd: Kitchen sink leaking", body, MANAGER).orElseThrow();

        assertThat(original.fromEmail()).isEqualTo("tina.alvarez@gmail.com");
        assertThat(original.fromName()).isEqualTo("Tina Alvarez");
        assertThat(original.subject()).isEqualTo("Kitchen sink leaking");
    }

    @Test
    void outlookForward() {
        String body = """
                ________________________________
                From: Marcus Lee <marcus.lee@outlook.com>
                Sent: Monday, October 26, 2026 9:30 AM
                To: Maple Property <pm@maple.com>
                Subject: Question about late fee

                I paid on the 1st.
                """;

        ForwardedEmail original = ForwardedEmail.detect("FW: Question about late fee", body, MANAGER).orElseThrow();

        assertThat(original.fromEmail()).isEqualTo("marcus.lee@outlook.com");
        assertThat(original.fromName()).isEqualTo("Marcus Lee");
        assertThat(original.subject()).isEqualTo("Question about late fee");
    }

    @Test
    void outlookMailtoAndAppleMailForwards() {
        String outlook = """
                From: Dan Ortiz [mailto:dan.ortiz@gmail.com]
                Subject: Noise
                """;
        assertThat(ForwardedEmail.detect("FW: Noise", outlook, MANAGER).orElseThrow().fromEmail())
                .isEqualTo("dan.ortiz@gmail.com");

        String apple = """
                Begin forwarded message:

                From: "Sam Rivera" <sam.rivera@icloud.com>
                Subject: Guest parking
                Date: October 26, 2026
                """;
        ForwardedEmail original = ForwardedEmail.detect("Fwd: Guest parking", apple, MANAGER).orElseThrow();
        assertThat(original.fromName()).isEqualTo("Sam Rivera");
        assertThat(original.fromEmail()).isEqualTo("sam.rivera@icloud.com");
    }

    @Test
    void notAForward() {
        // Auto-forwarding keeps the resident as the sender and the subject as is
        assertThat(ForwardedEmail.detect("Kitchen sink leaking", "From: someone@x.com", MANAGER)).isEmpty();
        // "Fwd:" without a forwarded header block
        assertThat(ForwardedEmail.detect("Fwd: hello", "No header here", MANAGER)).isEmpty();
        // Forwarding your own email
        assertThat(ForwardedEmail.detect("Fwd: note", "From: Me <pm@maple.com>", MANAGER)).isEmpty();
    }
}

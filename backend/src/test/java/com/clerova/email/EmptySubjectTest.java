package com.clerova.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmptySubjectTest {

    @Test
    void placeholdersSayNothing() {
        for (String subject : new String[] {null, "", "  ", "(no subject)", "No Subject", "Fwd:", "RE: ", "Fwd: (no subject)", "[No Subject]"}) {
            assertThat(InboundEmailProcessor.saysNothing(subject)).as(String.valueOf(subject)).isTrue();
        }
    }

    @Test
    void realSubjectsSaySomething() {
        for (String subject : new String[] {"Call me", "Re: leak", "No subject line needed, heater broke", "Fwd: Rate con 5521"}) {
            assertThat(InboundEmailProcessor.saysNothing(subject)).as(subject).isFalse();
        }
    }
}

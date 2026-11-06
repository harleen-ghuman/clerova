package com.clerova.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsageNotifierSubjectTest {

    @Test
    void carriersReadLoadsAndPropertyManagersReadRequests() {
        UsageThresholdReached warning = new UsageThresholdReached(UUID.randomUUID(), 192, 240);
        UsageThresholdReached reached = new UsageThresholdReached(UUID.randomUUID(), 240, 240);

        assertThat(UsageNotifier.subject(warning, true)).isEqualTo("You've used 80% of your Clerova loads this month");
        assertThat(UsageNotifier.subject(reached, true)).isEqualTo("You've reached your Clerova loads for this month");
        assertThat(UsageNotifier.subject(warning, false)).isEqualTo("You've used 80% of your Clerova requests this month");
        assertThat(UsageNotifier.subject(reached, false)).isEqualTo("You've reached your Clerova requests for this month");
    }
}

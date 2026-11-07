package com.clerova.trucking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutomaticSenderTest {

    @Test
    void orderAndShippingNoticesAreAutomatic() {
        assertThat(TruckingIntakeService.isAutomaticSender("auto-confirm@amazon.com")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("order-update@amazon.com")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("shipment-tracking@amazon.com")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("no-reply@accounts.google.com")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("noreply.alerts@bank.example")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("googlecommunityteam-noreply@google.com")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("statements.do-not-reply@bank.example")).isTrue();
        assertThat(TruckingIntakeService.isAutomaticSender("replyto.dana@crestline.example")).isFalse();
        assertThat(TruckingIntakeService.isAutomaticSender("noreen@crestline.example")).isFalse();
    }

    @Test
    void peopleAreNot() {
        assertThat(TruckingIntakeService.isAutomaticSender("dana@crestline.example")).isFalse();
        assertThat(TruckingIntakeService.isAutomaticSender("dispatch@sierrarun.example")).isFalse();
        assertThat(TruckingIntakeService.isAutomaticSender("ordersdesk.lee@broker.example")).isFalse();
        assertThat(TruckingIntakeService.isAutomaticSender(null)).isFalse();
    }
}

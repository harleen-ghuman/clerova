package com.clerova.trucking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpaceGreetingTest {

    @Test
    void addsABlankLineAfterTheGreeting() {
        assertThat(TruckingAiService.spaceGreeting("Hi Dana,\nThe truck is loaded."))
                .isEqualTo("Hi Dana,\n\nThe truck is loaded.");
    }

    @Test
    void leavesAnAlreadySpacedDraftAlone() {
        assertThat(TruckingAiService.spaceGreeting("Hi,\n\nWe'll send the POD today."))
                .isEqualTo("Hi,\n\nWe'll send the POD today.");
    }

    @Test
    void leavesOtherTextAlone() {
        assertThat(TruckingAiService.spaceGreeting("Thanks,\nwe got it.")).isEqualTo("Thanks,\nwe got it.");
        assertThat(TruckingAiService.spaceGreeting(null)).isNull();
    }
}

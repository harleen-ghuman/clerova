package com.clerova.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplySignOffTest {

    @Test
    void addsTheCompanyNameAtTheEnd() {
        assertThat(ReplyService.withSignOff("Hi Tina,\n\nWe'll send a plumber.\n", "Maple Property Group"))
                .isEqualTo("Hi Tina,\n\nWe'll send a plumber.\n\nMaple Property Group");
    }

    @Test
    void doesNotSignTwice() {
        assertThat(ReplyService.withSignOff("Hi Tina,\n\nThanks,\nMaple Property Group", "Maple Property Group"))
                .isEqualTo("Hi Tina,\n\nThanks,\nMaple Property Group");
        assertThat(ReplyService.withSignOff("Hi,\n\nThanks!\n- The maple property group team  ", "Maple Property Group"))
                .endsWith("The maple property group team");
    }

    @Test
    void signsAfterAPersonsName() {
        assertThat(ReplyService.withSignOff("Hi Tina,\n\nThanks,\nAlex", "Maple Property Group"))
                .isEqualTo("Hi Tina,\n\nThanks,\nAlex\n\nMaple Property Group");
    }

    @Test
    void putsTheNameRightUnderAClosing() {
        assertThat(ReplyService.withSignOff("Hi Priya,\n\nWe'll look into it.\n\nThanks,\n", "Maple Property Group"))
                .isEqualTo("Hi Priya,\n\nWe'll look into it.\n\nThanks,\nMaple Property Group");
    }
}

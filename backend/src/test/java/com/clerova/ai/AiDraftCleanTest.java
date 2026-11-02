package com.clerova.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiDraftCleanTest {

    @Test
    void dropsNotesMeantForStaff() {
        String draft = """
                Hi Tom,

                We've logged a request for the roof leak at 12 Elm St #3.

                actionType reason: We need to start a maintenance workflow.

                (Internal note: Create maintenance request; mark high priority.)
                """;

        assertThat(AiService.cleanDraft(draft))
                .isEqualTo("Hi Tom,\n\nWe've logged a request for the roof leak at 12 Elm St #3.");
    }

    @Test
    void dropsAGenericSignOff() {
        assertThat(AiService.cleanDraft("Hi Brian,\n\nWe've logged it.\n\nProperty Management\n"))
                .isEqualTo("Hi Brian,\n\nWe've logged it.");
        assertThat(AiService.cleanDraft("Hi Alex,\n\nWe'll follow up.\n\nClerova"))
                .isEqualTo("Hi Alex,\n\nWe'll follow up.");
    }

    @Test
    void keepsAnOrdinaryReply() {
        String draft = "Hi Tina,\n\nThe maintenance team will follow up to arrange the repair.";

        assertThat(AiService.cleanDraft(draft)).isEqualTo(draft);
        assertThat(AiService.cleanDraft(null)).isNull();
    }
}

package com.clerova.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceClipTest {

    @Test
    void longMessagesKeepTheirStartAndConversationsTheirEnd() {
        String message = "Leak under the sink. " + "x".repeat(100_000);
        String clipped = AiService.clip(message, AiService.MAX_MESSAGE_CHARS);

        assertThat(clipped).startsWith("Leak under the sink.").endsWith("[...]");
        assertThat(clipped.length()).isLessThan(AiService.MAX_MESSAGE_CHARS + 10);

        String conversation = "y".repeat(100_000) + " Latest: it's flooding now.";
        String recent = AiService.clipStart(conversation, AiService.MAX_CONVERSATION_CHARS);

        assertThat(recent).startsWith("[...]").endsWith("Latest: it's flooding now.");
        assertThat(recent.length()).isLessThan(AiService.MAX_CONVERSATION_CHARS + 10);

        assertThat(AiService.clip("short", 10)).isEqualTo("short");
        assertThat(AiService.clipStart(null, 10)).isNull();
    }
}

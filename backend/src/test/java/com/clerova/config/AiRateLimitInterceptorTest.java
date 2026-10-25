package com.clerova.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AiRateLimitInterceptorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void limitsEachClientPerMinute() {
        AiRateLimitInterceptor limiter = new AiRateLimitInterceptor(2, 100, clock);

        assertThat(limiter.tryAcquireClient("a")).isTrue();
        assertThat(limiter.tryAcquireClient("a")).isTrue();
        assertThat(limiter.tryAcquireClient("a")).isFalse();
        assertThat(limiter.tryAcquireClient("b")).isTrue();
    }

    @Test
    void capsTotalRequestsPerDay() {
        AiRateLimitInterceptor limiter = new AiRateLimitInterceptor(100, 2, clock);

        assertThat(limiter.tryAcquireDaily()).isTrue();
        assertThat(limiter.tryAcquireDaily()).isTrue();
        assertThat(limiter.tryAcquireDaily()).isFalse();
    }
}

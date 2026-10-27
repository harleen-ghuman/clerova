package com.clerova.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits requests that trigger OpenAI calls: a per-user limit per minute
 * and a global daily cap that bounds total spend. Per-organization monthly
 * quotas are enforced separately by AiUsageService. Counters live in memory,
 * so they are per instance and reset on restart.
 *
 * Registered as an MVC interceptor (see WebConfig) rather than a servlet
 * filter so CORS headers are already applied to the 429 response.
 */
@Component
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitInterceptor.class);

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int perClientPerMinute;
    private final int globalPerDay;
    private final Clock clock;

    private final Map<String, ClientWindow> clientWindows = new ConcurrentHashMap<>();
    private final Object dailyLock = new Object();
    private LocalDate currentDay;
    private int dailyCount;

    @Autowired
    public AiRateLimitInterceptor(
            @Value("${app.ai.rate-limit.per-client-per-minute}") int perClientPerMinute,
            @Value("${app.ai.rate-limit.global-per-day}") int globalPerDay
    ) {
        this(perClientPerMinute, globalPerDay, Clock.systemUTC());
    }

    AiRateLimitInterceptor(int perClientPerMinute, int globalPerDay, Clock clock) {
        this.perClientPerMinute = perClientPerMinute;
        this.globalPerDay = globalPerDay;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {

        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        // Signed-in users are limited per user; anything else per IP.
        String clientKey = request.getUserPrincipal() != null
                ? "user:" + request.getUserPrincipal().getName()
                : "ip:" + request.getRemoteAddr();

        if (!tryAcquireClient(clientKey)) {
            reject(response, "Too many requests. Please wait a minute and try again.");
            return false;
        }

        if (!tryAcquireDaily()) {
            log.warn("Global daily AI request cap of {} reached", globalPerDay);
            reject(response, "Clerova is very busy right now. Please try again later, or email hello@clerova-ai.com.");
            return false;
        }

        return true;
    }

    boolean tryAcquireClient(String clientKey) {
        long minute = clock.millis() / 60_000;

        if (clientWindows.size() > MAX_TRACKED_CLIENTS) {
            clientWindows.values().removeIf(window -> window.minute != minute);
        }

        ClientWindow window = clientWindows.compute(clientKey, (key, existing) ->
                existing == null || existing.minute != minute
                        ? new ClientWindow(minute)
                        : existing
        );

        return window.count.incrementAndGet() <= perClientPerMinute;
    }

    /**
     * Counts an AI call Clerova makes on its own (e.g. for a follow-up
     * email) against the shared daily cap. False when the cap is reached.
     */
    public boolean tryAcquireBackground() {
        boolean allowed = tryAcquireDaily();

        if (!allowed) {
            log.warn("Global daily AI request cap of {} reached; skipping background AI", globalPerDay);
        }

        return allowed;
    }

    boolean tryAcquireDaily() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        synchronized (dailyLock) {
            if (!today.equals(currentDay)) {
                currentDay = today;
                dailyCount = 0;
            }

            if (dailyCount >= globalPerDay) {
                return false;
            }

            dailyCount++;
            return true;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"RATE_LIMITED\",\"message\":\"" + message + "\"}"
        );
    }

    private static final class ClientWindow {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private ClientWindow(long minute) {
            this.minute = minute;
        }
    }
}

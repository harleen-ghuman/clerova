package com.clerova.service;

import java.util.UUID;

/** An organization reached 80% or 100% of its monthly requests. */
public record UsageThresholdReached(UUID organizationId, int used, int limit) {

    public boolean limitReached() {
        return used >= limit;
    }
}

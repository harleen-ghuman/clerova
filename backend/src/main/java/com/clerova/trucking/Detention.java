package com.clerova.trucking;

import java.time.Duration;
import java.time.Instant;

/**
 * Works out detention at one stop from the logged times and the rate con's
 * terms: time on site past the free time, in whole quarter hours, up to
 * the cap.
 */
final class Detention {

    /** Most rate cons give 2 hours free when they don't say. */
    static final int DEFAULT_FREE_MINUTES = 120;

    record Result(long minutesOnSite, int freeMinutes, long billableMinutes, Long amountCents, Instant freeEndsAt) {
    }

    private Detention() {
    }

    /** Null until the truck has arrived. */
    static Result at(LoadStop stop, Load load, Instant now) {
        if (stop.getArrivedAt() == null) {
            return null;
        }

        int free = load.getDetentionFreeMinutes() != null ? load.getDetentionFreeMinutes() : DEFAULT_FREE_MINUTES;
        Instant end = stop.getLeftAt() != null ? stop.getLeftAt() : now;
        long onSite = Math.max(0, Duration.between(stop.getArrivedAt(), end).toMinutes());
        long billable = Math.max(0, onSite - free) / 15 * 15;
        Long amount = null;

        if (load.getDetentionRateCents() != null) {
            amount = Math.round(load.getDetentionRateCents() * billable / 60.0);

            if (load.getDetentionMaxCents() != null) {
                amount = Math.min(amount, load.getDetentionMaxCents());
            }
        }

        return new Result(onSite, free, billable, amount, stop.getArrivedAt().plus(Duration.ofMinutes(free)));
    }
}

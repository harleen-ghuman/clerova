package com.clerova.dto;

/**
 * Activity over the last 7 days, for the Today page.
 *
 * @param medianReplyMinutes median time from a request arriving to the
 *                           first reply sent; null without replies
 * @param minutesSaved       estimate: {@code minutesPerRequest} for each
 *                           request Clerova organized
 */
public record WeekSummaryResponse(
        long requestsOrganized,
        long emailsReceived,
        long repliesSent,
        long jobsCompleted,
        Long medianReplyMinutes,
        long minutesSaved,
        int minutesPerRequest
) {
}

import { apiRequest } from "./client";

export interface WeekSummary {
  requestsOrganized: number;
  emailsReceived: number;
  repliesSent: number;
  jobsCompleted: number;
  /** null when no replies were sent */
  medianReplyMinutes: number | null;
  minutesSaved: number;
  minutesPerRequest: number;
  /** Since the workspace started. */
  allTimeRequests: number;
  /** Suggestions approved, emailed or copied. */
  allTimeApprovals: number;
}

export function getWeekSummary(): Promise<WeekSummary> {
  return apiRequest("/api/summary");
}

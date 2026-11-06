import type { Me, OnboardingDetails } from "../types/Account";
import { apiRequest } from "./client";

export function getMe(): Promise<Me> {
  return apiRequest("/api/me");
}

export function completeOnboarding(
  details: OnboardingDetails
): Promise<Me> {
  return apiRequest("/api/onboarding", {
    method: "POST",
    body: details,
  });
}

export function updateAccount(details: {
  fullName: string;
  organizationName: string;
}): Promise<Me> {
  return apiRequest("/api/me", {
    method: "PATCH",
    body: details,
  });
}

/** The workspace's time zone, used for invoice dates and the monthly export. */
export function setTimeZone(timeZone: string): Promise<Me> {
  return apiRequest("/api/me/time-zone", { method: "PUT", body: { timeZone } });
}

/** This device's time zone, like America/Chicago, or null if the browser can't tell. */
export function deviceTimeZone(): string | null {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
  } catch {
    return null;
  }
}

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

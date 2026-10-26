import { apiRequest } from "./client";

export type PaidPlan = "STARTER" | "GROWTH";
export type BillingInterval = "month" | "year";

export function getBillingOffer(): Promise<{
  enabled: boolean;
  foundingSpotsLeft: number;
}> {
  return apiRequest("/api/billing/offer");
}

/** Returns the Stripe Checkout page to send the user to. */
export function startCheckout(
  plan: PaidPlan,
  interval: BillingInterval
): Promise<{ url: string }> {
  return apiRequest("/api/billing/checkout", {
    method: "POST",
    body: { plan, interval },
  });
}

export function completeCheckout(sessionId: string): Promise<unknown> {
  return apiRequest(
    `/api/billing/checkout/${encodeURIComponent(sessionId)}/complete`,
    { method: "POST" }
  );
}

/** Returns the Stripe billing portal page. */
export function openBillingPortal(): Promise<{ url: string }> {
  return apiRequest("/api/billing/portal", { method: "POST" });
}

/** Moves a Starter subscription to Growth right away. */
export function upgradeToGrowth(): Promise<unknown> {
  return apiRequest("/api/billing/upgrade", { method: "POST" });
}

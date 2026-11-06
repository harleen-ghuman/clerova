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
  interval: BillingInterval,
  trucks?: number
): Promise<{ url: string }> {
  return apiRequest("/api/billing/checkout", {
    method: "POST",
    body: { plan, interval, trucks },
  });
}

/** A carrier's number of trucks; with a plan, the subscription changes too. */
export function changeTrucks(trucks: number): Promise<unknown> {
  return apiRequest("/api/billing/trucks", { method: "POST", body: { trucks } });
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

/** Cancels at the end of the trial or billing period. */
export function cancelPlan(): Promise<unknown> {
  return apiRequest("/api/billing/cancel", { method: "POST" });
}

/** Undoes a scheduled cancellation. */
export function keepPlan(): Promise<unknown> {
  return apiRequest("/api/billing/keep", { method: "POST" });
}

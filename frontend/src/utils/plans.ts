import type { BillingInterval, PaidPlan } from "../api/billing";

// Must match the prices set up in Stripe (see README, Billing setup).
// Yearly is 11 months: one month free.
export const PLANS: {
  plan: PaidPlan;
  name: string;
  requests: number;
  monthly: number;
  foundingMonthly: number;
}[] = [
  { plan: "STARTER", name: "Starter", requests: 600, monthly: 99, foundingMonthly: 89 },
  { plan: "GROWTH", name: "Growth", requests: 1500, monthly: 189, foundingMonthly: 159 },
];

export function planPrice(
  monthly: number,
  interval: BillingInterval
): { amount: number; per: string } {
  return interval === "year"
    ? { amount: monthly * 11, per: "year" }
    : { amount: monthly, per: "month" };
}

export function planName(plan: string): string {
  return PLANS.find((item) => item.plan === plan)?.name ?? "Free trial";
}

import type { BillingInterval } from "../api/billing";

// Must match the trucking prices in Stripe (lookup keys trucking_starter_*,
// trucking_fleet_* and their founding_ versions). Yearly is 10 months.
export const TRUCKING_PRICES = {
  regular: { starterBase: 99, starterTrucks: 4, starterPerTruck: 25, fleetMinimum: 199, fleetPerTruck: 20 },
  founding: { starterBase: 69, starterTrucks: 4, starterPerTruck: 18, fleetMinimum: 139, fleetPerTruck: 14 },
};

/** From this many trucks, Fleet costs less than Starter (as on the server). */
export const FLEET_FROM_TRUCKS = 9;
export const MAX_TRUCKS = 500;
export const LOADS_PER_TRUCK = 60;

export type TruckingPlan = "STARTER" | "FLEET";

export function truckingPlan(trucks: number): TruckingPlan {
  return trucks >= FLEET_FROM_TRUCKS ? "FLEET" : "STARTER";
}

/** The price for this many trucks, per month or per year. */
export function truckingPrice(
  trucks: number,
  interval: BillingInterval,
  founding: boolean
): { plan: TruckingPlan; amount: number; per: string } {
  const prices = founding ? TRUCKING_PRICES.founding : TRUCKING_PRICES.regular;
  const plan = truckingPlan(trucks);
  const monthly =
    plan === "FLEET"
      ? Math.max(prices.fleetMinimum, prices.fleetPerTruck * trucks)
      : prices.starterBase + Math.max(0, trucks - prices.starterTrucks) * prices.starterPerTruck;

  return interval === "year"
    ? { plan, amount: monthly * 10, per: "year" }
    : { plan, amount: monthly, per: "month" };
}

/** Like "Starter: $99 a month for up to 4 trucks, then $25 a truck". */
export function truckingPlanSummary(plan: TruckingPlan, founding: boolean): string {
  const prices = founding ? TRUCKING_PRICES.founding : TRUCKING_PRICES.regular;
  return plan === "FLEET"
    ? `Fleet: $${prices.fleetPerTruck} a truck a month, $${prices.fleetMinimum} at least`
    : `Starter: $${prices.starterBase} a month for up to ${prices.starterTrucks} trucks, then $${prices.starterPerTruck} a truck`;
}

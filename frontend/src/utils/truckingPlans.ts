import type { BillingInterval } from "../api/billing";

// Must match the trucking prices in Stripe (lookup keys trucking_starter_*,
// trucking_fleet_* and their founding_ versions). Yearly is 10 months.
// Starter: a price per truck, 1 to 7 trucks.
// Fleet: a flat price for 8 to 20 trucks, then a price per truck up to 49.
export const TRUCKING_PRICES = {
  regular: { starterPerTruck: 25, fleetBase: 199, fleetBaseTrucks: 20, fleetPerTruck: 12 },
  founding: { starterPerTruck: 17.5, fleetBase: 139, fleetBaseTrucks: 20, fleetPerTruck: 8.4 },
};

/** From this many trucks, a carrier is on Fleet (as on the server). */
export const FLEET_FROM_TRUCKS = 8;
/** Self-serve plans go up to this many trucks; more is a custom plan by email. */
export const MAX_TRUCKS = 49;
/** Rate cons (loads) a month per truck, shared by the whole fleet. */
export const LOADS_PER_TRUCK = 20;
export const CONTACT_EMAIL = "hello@clerova-ai.com";

export type TruckingPlan = "STARTER" | "FLEET";

export function truckingPlan(trucks: number): TruckingPlan {
  return trucks >= FLEET_FROM_TRUCKS ? "FLEET" : "STARTER";
}

/** CA$ for Canadian carriers' plans (billed in CAD), $ otherwise. Set once by the app. */
let planSymbol = "$";

export function setPlanCurrency(currency: "USD" | "CAD") {
  planSymbol = currency === "CAD" ? "CA$" : "$";
}

/** Dollars as shown: whole dollars stay whole, cents only when there are some. */
export function dollars(amount: number): string {
  const rounded = Math.round(amount * 100) / 100;
  return Number.isInteger(rounded)
    ? `${planSymbol}${rounded.toLocaleString("en-US")}`
    : `${planSymbol}${rounded.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
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
      ? prices.fleetBase + Math.max(0, trucks - prices.fleetBaseTrucks) * prices.fleetPerTruck
      : prices.starterPerTruck * trucks;
  const rounded = (value: number) => Math.round(value * 100) / 100;

  return interval === "year"
    ? { plan, amount: rounded(monthly * 10), per: "year" }
    : { plan, amount: rounded(monthly), per: "month" };
}

/** Like "Starter: $25 a truck a month, 1 to 7 trucks". */
export function truckingPlanSummary(plan: TruckingPlan, founding: boolean): string {
  const prices = founding ? TRUCKING_PRICES.founding : TRUCKING_PRICES.regular;
  return plan === "FLEET"
    ? `Fleet: ${dollars(prices.fleetBase)} a month for ${FLEET_FROM_TRUCKS} to ${prices.fleetBaseTrucks} trucks, then ${dollars(prices.fleetPerTruck)} a truck up to ${MAX_TRUCKS}`
    : `Starter: ${dollars(prices.starterPerTruck)} a truck a month, 1 to 7 trucks`;
}

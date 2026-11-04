import type { LoadStatus } from "../types/Load";

export function money(cents: number | null | undefined): string {
  if (cents === null || cents === undefined) {
    return "";
  }

  return (cents / 100).toLocaleString("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: cents % 100 === 0 ? 0 : 2,
  });
}

/** "Sunridge Packing, 1200 S Elm Ave, Fresno, CA 93706" to "Fresno, CA". */
export function city(place: string | null | undefined): string {
  if (!place) {
    return "?";
  }

  const matches = [...place.matchAll(/([A-Za-z][A-Za-z .'-]*?),?\s+([A-Z]{2})\b(?:\s+\d{5}(?:-\d{4})?)?/g)];
  const last = matches.at(-1);

  if (last) {
    const town = last[1].split(",").at(-1)!.trim();
    return `${townCase(town)}, ${last[2]}`;
  }

  return place.length > 32 ? place.slice(0, 32) + "…" : place;
}

/** "$35/hour after 2 hours free", leaving out whatever the rate con doesn't say. */
export function detention(rateCents: number | null, freeMinutes: number | null): string {
  if (rateCents === null && freeMinutes === null) {
    return "None on the rate con";
  }

  const rate = rateCents !== null ? `${money(rateCents)}/hour` : "Rate not stated";
  return freeMinutes !== null ? `${rate} after ${hours(freeMinutes)} free` : rate;
}

/** "STOCKTON" becomes "Stockton"; mixed case is left as written. */
function townCase(town: string): string {
  return town === town.toUpperCase()
    ? town.toLowerCase().replace(/(^|[\s'-])([a-z])/g, (_, start: string, letter: string) => start + letter.toUpperCase())
    : town;
}

export function lane(origin: string | null, destination: string | null): string {
  return `${city(origin)} → ${city(destination)}`;
}

export const STATUS_LABELS: Record<LoadStatus, string> = {
  BOOKED: "Booked",
  IN_TRANSIT: "In transit",
  DELIVERED: "Delivered",
  INVOICED: "Invoiced",
  PAID: "Paid",
  CANCELLED: "Cancelled",
};

export function hours(minutes: number | null): string {
  if (minutes === null) {
    return "";
  }

  const h = minutes / 60;
  return `${Number.isInteger(h) ? h : h.toFixed(1)} ${h === 1 ? "hour" : "hours"}`;
}

export function when(value: string | null): string {
  return value
    ? new Date(value).toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })
    : "";
}

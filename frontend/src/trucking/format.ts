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
    return `${town}, ${last[2]}`;
  }

  return place.length > 32 ? place.slice(0, 32) + "…" : place;
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

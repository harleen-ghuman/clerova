import type { DocumentKind, LoadStatus, LoadSummary } from "../types/Load";

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

export const DOCUMENT_KINDS: { kind: DocumentKind; label: string }[] = [
  { kind: "RATE_CON", label: "Rate con" },
  { kind: "BOL", label: "BOL" },
  { kind: "POD", label: "POD" },
  { kind: "LUMPER_RECEIPT", label: "Lumper receipt" },
  { kind: "SCALE_TICKET", label: "Scale ticket" },
  { kind: "INVOICE", label: "Invoice" },
  { kind: "OTHER", label: "Other" },
];

export function kindLabel(kind: DocumentKind | null): string {
  return DOCUMENT_KINDS.find((item) => item.kind === kind)?.label ?? "Document";
}

/** "#88213 · Fresno, CA → Phoenix, AZ", for picking a load from a list. */
export function loadLabel(load: Pick<LoadSummary, "loadNumber" | "origin" | "destination">): string {
  return `${load.loadNumber ? `#${load.loadNumber}` : "No load number"} · ${lane(load.origin, load.destination)}`;
}

/** Where a long email may wrap: after the "@" and before dots, never mid-word. */
export function emailBreaks(email: string): string[] {
  const at = email.indexOf("@");
  return at < 0 ? [email] : [email.slice(0, at + 1), ...email.slice(at + 1).split(/(?=\.)/)];
}

/** "6:52 AM" today, or "Nov 5, 6:52 AM" on another day. */
export function clock(value: string | null): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  const today = new Date().toDateString() === date.toDateString();
  return date.toLocaleString([], today
    ? { hour: "numeric", minute: "2-digit" }
    : { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

/** A time for an email to the broker, with the time zone: "Nov 5, 6:52 AM PST". */
export function emailTime(value: string): string {
  return new Date(value).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "short",
  });
}

/** 135 minutes as "2h 15m". */
export function duration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = Math.round(minutes % 60);
  return h === 0 ? `${m}m` : m === 0 ? `${h}h` : `${h}h ${m}m`;
}

/** The facility from a stop's place: its first line, before the street. */
export function placeName(place: string | null): string {
  if (!place) {
    return "the stop";
  }

  const first = place.trim().split(/\r?\n/)[0].trim();
  const comma = first.indexOf(",");
  return comma > 3 ? first.slice(0, comma).trim() : first;
}

/** An ISO time as a datetime-local input value, in the viewer's time zone. */
export function toLocalInput(value: string | null): string {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A datetime-local value back to ISO; null when empty or not a time. */
export function fromLocalInput(value: string): string | null {
  if (!value) {
    return null;
  }

  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** "75.00" or "$1,250" to cents; null when it isn't an amount. */
export function parseCents(value: string): number | null {
  const cleaned = value.replace(/[$,\s]/g, "");

  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) {
    return null;
  }

  return Math.round(Number(cleaned) * 100);
}

/** Cents as a plain amount for an input: 7500 to "75.00". */
export function centsInput(cents: number): string {
  return (cents / 100).toFixed(2);
}

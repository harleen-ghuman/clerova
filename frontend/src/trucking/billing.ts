import { fetchFile } from "../api/attachments";
import { viewFile } from "../utils/viewFile";
import type { Invoice } from "../types/Load";

/** "2026-12-05" as "Dec 5, 2026", without time zone shifts. */
export function dueText(date: string): string {
  const [year, month, day] = date.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" });
}

export function isOverdue(invoice: Pick<Invoice, "status" | "dueDate">): boolean {
  if (invoice.status !== "SENT" || !invoice.dueDate) {
    return false;
  }

  const [year, month, day] = invoice.dueDate.split("-").map(Number);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(year, month - 1, day) < today;
}

/** Opens a PDF from the API, like an invoice, in the in-app viewer. */
export function openPdf(path: string, name: string, subtitle?: string) {
  viewFile({ name, subtitle, load: () => fetchFile(path) });
}

/** Saves a file from the API, like the packet or the month's spreadsheet. */
export async function downloadFile(path: string, fileName: string) {
  const blob = await fetchFile(path);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

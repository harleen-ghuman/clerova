import { fetchAttachment, fetchLoadDocument } from "../api/attachments";
import type { LoadDocument } from "../types/Load";

export const ACCEPTED_FILES = ".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp";

/** Opens a document in a new tab (photos and PDFs only, so it can always show). */
export async function openDocument(document: Pick<LoadDocument, "id" | "source">) {
  // Opened right away, while the click still counts, so it isn't blocked
  const tab = window.open("", "_blank");

  try {
    const blob = document.source === "UPLOAD" ? await fetchLoadDocument(document.id) : await fetchAttachment(document.id);
    const url = URL.createObjectURL(blob);

    if (tab) {
      tab.location.href = url;
    }

    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (err) {
    tab?.close();
    throw err;
  }
}

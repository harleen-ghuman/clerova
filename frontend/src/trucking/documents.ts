import { fetchAttachment, fetchLoadDocument } from "../api/attachments";
import { viewFile } from "../utils/viewFile";
import type { LoadDocument } from "../types/Load";
import { kindLabel } from "./format";

export const ACCEPTED_FILES = ".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp";

/** Opens a document in the in-app viewer. */
export function openDocument(document: Pick<LoadDocument, "id" | "source" | "name" | "kind">) {
  viewFile({
    name: document.name || "Unnamed file",
    subtitle: kindLabel(document.kind),
    load: () => (document.source === "UPLOAD" ? fetchLoadDocument(document.id) : fetchAttachment(document.id)),
  });
}

const MAX_BYTES = 10 * 1024 * 1024;

/** Checked before uploading, so a wrong file fails right away. */
export function uploadProblem(file: File): string | null {
  if (!/\.(pdf|jpe?g|png|webp)$/i.test(file.name) && !/^(application\/pdf|image\/(jpeg|png|webp))$/.test(file.type)) {
    return "Upload a PDF or a photo (JPG, PNG or WebP).";
  }

  if (file.size > MAX_BYTES) {
    return "Files can be up to 10 MB.";
  }

  return file.size === 0 ? "That file is empty." : null;
}

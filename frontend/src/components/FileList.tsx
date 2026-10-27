import { useState } from "react";

import { fetchAttachment, VIEWABLE_TYPES } from "../api/attachments";
import { errorMessage } from "../api/client";
import type { AttachmentFile } from "../types/Email";

function size(bytes: number | null): string | null {
  if (bytes === null || bytes <= 0) {
    return null;
  }

  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }

  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function viewable(file: AttachmentFile): boolean {
  return VIEWABLE_TYPES.includes((file.contentType ?? "").split(";")[0].trim().toLowerCase());
}

/** Files that came with an email: photos and PDFs open, others download. */
export function FileList({ files }: { files: AttachmentFile[] }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (files.length === 0) {
    return null;
  }

  async function open(file: AttachmentFile) {
    const name = file.fileName || "file";
    // Opened right away, while the click still counts, so it isn't blocked
    const tab = viewable(file) ? window.open("", "_blank") : null;

    try {
      setBusy(file.id);
      setError(null);
      const url = URL.createObjectURL(await fetchAttachment(file.id));

      if (tab) {
        tab.location.href = url;
      } else {
        const link = document.createElement("a");
        link.href = url;
        link.download = name;
        document.body.appendChild(link);
        link.click();
        link.remove();
      }

      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      tab?.close();
      setError(errorMessage(err, "Couldn't open this file. Please try again."));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="file-list">
      <ul aria-label="Attachments">
        {files.map((file) => {
          const name = file.fileName || "Unnamed file";
          const details = [size(file.sizeBytes)].filter(Boolean).join(" · ");

          return (
            <li key={file.id}>
              {file.available ? (
                <button
                  type="button"
                  className="file-chip"
                  onClick={() => void open(file)}
                  disabled={busy !== null}
                  aria-label={`${viewable(file) ? "Open" : "Download"} ${name}`}
                >
                  <span className="file-chip-name">{name}</span>
                  <span className="file-chip-action">
                    {busy === file.id ? "Opening..." : viewable(file) ? "Open" : "Download"}
                    {details && busy !== file.id ? ` · ${details}` : ""}
                  </span>
                </button>
              ) : (
                <span className="file-chip file-chip-missing">
                  <span className="file-chip-name">{name}</span>
                  <span className="file-chip-action">In the original email</span>
                </span>
              )}
            </li>
          );
        })}
      </ul>
      {error && <p className="action-error">{error}</p>}
    </div>
  );
}

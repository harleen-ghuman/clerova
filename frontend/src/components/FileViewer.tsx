import { useEffect, useRef, useState, type KeyboardEvent } from "react";

import { errorMessage } from "../api/client";

/** A file to show: its name, a short line under it, and how to fetch it. */
export interface ViewerFile {
  name: string;
  subtitle?: string;
  load: () => Promise<Blob>;
}

type Kind = "pdf" | "image" | "other";

const ZOOMS = [1, 1.5, 2, 3];

async function kindOf(blob: Blob, name: string): Promise<Kind> {
  const type = blob.type.toLowerCase();

  if (type.startsWith("image/") && type !== "image/svg+xml") {
    return "image";
  }

  if (type === "application/pdf" || /\.pdf$/i.test(name)) {
    return "pdf";
  }

  // Some servers send PDFs as octet-stream: look at the first bytes
  const head = new Uint8Array(await blob.slice(0, 5).arrayBuffer());
  return String.fromCharCode(...head) === "%PDF-" ? "pdf" : "other";
}

export function FileViewer({ file, onClosed }: { file: ViewerFile; onClosed: () => void }) {
  const [url, setUrl] = useState<string | null>(null);
  const [blob, setBlob] = useState<Blob | null>(null);
  const [kind, setKind] = useState<Kind | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pages, setPages] = useState<{ shown: number; total: number } | null>(null);
  const [zoom, setZoom] = useState(0);
  const dialog = useRef<HTMLDivElement>(null);
  const body = useRef<HTMLDivElement>(null);
  const sheet = useRef<HTMLDivElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const closing = useRef(false);

  // Fetch the file
  useEffect(() => {
    let current = true;
    let objectUrl: string | null = null;

    file
      .load()
      .then(async (result) => {
        const detected = await kindOf(result, file.name);

        if (current) {
          objectUrl = URL.createObjectURL(result);
          setBlob(result);
          setUrl(objectUrl);
          setKind(detected);
        }
      })
      .catch((err) => {
        if (current) {
          setError(errorMessage(err, "Couldn't open this file. Please try again."));
        }
      });

    return () => {
      current = false;

      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [file]);

  // Back closes the viewer: it gets its own history entry
  useEffect(() => {
    window.history.pushState({ fileViewer: true }, "");

    function onPopState() {
      closing.current = true;
      onClosed();
    }

    window.addEventListener("popstate", onPopState);
    document.body.classList.add("file-viewer-open");
    closeButton.current?.focus();

    return () => {
      window.removeEventListener("popstate", onPopState);
      document.body.classList.remove("file-viewer-open");
    };
  }, [onClosed]);

  function close() {
    if (closing.current) {
      return;
    }

    closing.current = true;

    if ((window.history.state as { fileViewer?: boolean } | null)?.fileViewer) {
      // Takes the viewer's history entry away; popstate then closes it
      window.history.back();
    } else {
      onClosed();
    }
  }

  // Draw the PDF's pages to fit the screen, again when zooming
  useEffect(() => {
    if (kind !== "pdf" || !blob || !sheet.current || !body.current) {
      return;
    }

    let current = true;
    const target = sheet.current;
    const width = Math.max(240, body.current.clientWidth - 24) * ZOOMS[zoom];

    void (async () => {
      try {
        const { openPdf, drawPage, MAX_PAGES } = await import("../utils/pdfRender");
        const { pdf, close: closePdf } = await openPdf(await blob.arrayBuffer());
        const shown = Math.min(pdf.numPages, MAX_PAGES);

        if (!current) {
          return;
        }

        target.replaceChildren();
        setPages({ shown, total: pdf.numPages });

        for (let number = 1; number <= shown && current; number += 1) {
          const canvas = document.createElement("canvas");
          canvas.className = "file-viewer-page";
          canvas.setAttribute("role", "img");
          canvas.setAttribute("aria-label", `Page ${number} of ${pdf.numPages}`);
          target.appendChild(canvas);
          await drawPage(pdf, number, canvas, width);
        }

        closePdf();
      } catch (err) {
        console.error("Failed to show the PDF:", err);

        if (current) {
          setKind("other");
          setError("This PDF can't be shown here. Download it to open it.");
        }
      }
    })();

    return () => {
      current = false;
    };
  }, [kind, blob, zoom]);

  // Keep keyboard focus inside the viewer; Escape closes it
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      close();
      return;
    }

    if (event.key !== "Tab" || !dialog.current) {
      return;
    }

    const focusable = [...dialog.current.querySelectorAll<HTMLElement>("button:not(:disabled), a[href]")];
    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  }

  return (
    <div className="file-viewer" role="dialog" aria-modal="true" aria-label={file.name} ref={dialog} onKeyDown={onKeyDown}>
      <header className="file-viewer-bar">
        <button type="button" className="file-viewer-close" onClick={close} ref={closeButton}>
          <span aria-hidden="true">←</span> Back
        </button>
        <div className="file-viewer-title">
          <strong>{file.name}</strong>
          {(file.subtitle || pages) && (
            <span>
              {[file.subtitle, pages && (pages.total === 1 ? "1 page" : `${pages.total} pages`)].filter(Boolean).join(" · ")}
            </span>
          )}
        </div>
        {url && (
          <a className="rq-button rq-button-small" href={url} download={file.name}>
            Download
          </a>
        )}
      </header>

      {kind === "pdf" && (
        <div className="file-viewer-zoom" role="group" aria-label="Zoom">
          <button type="button" onClick={() => setZoom((value) => Math.max(0, value - 1))} disabled={zoom === 0} aria-label="Zoom out">
            −
          </button>
          <span>{Math.round(ZOOMS[zoom] * 100)}%</span>
          <button
            type="button"
            onClick={() => setZoom((value) => Math.min(ZOOMS.length - 1, value + 1))}
            disabled={zoom === ZOOMS.length - 1}
            aria-label="Zoom in"
          >
            +
          </button>
        </div>
      )}

      <div className="file-viewer-body" ref={body} tabIndex={0} role="region" aria-label="File">
        {!kind && !error && <p className="file-viewer-note">Opening {file.name}...</p>}
        {error && <p className="file-viewer-note action-error">{error}</p>}
        {kind === "image" && url && <img className="file-viewer-image" src={url} alt={file.name} />}
        {kind === "other" && !error && (
          <p className="file-viewer-note">This file can't be shown here. Download it to open it.</p>
        )}
        <div className="file-viewer-sheet" ref={sheet} hidden={kind !== "pdf"} />
        {pages && pages.shown < pages.total && (
          <p className="file-viewer-note">
            Showing the first {pages.shown} of {pages.total} pages. Download it to see them all.
          </p>
        )}
      </div>
    </div>
  );
}

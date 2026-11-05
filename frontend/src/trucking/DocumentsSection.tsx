import { useId, useState, type ChangeEvent } from "react";
import { deleteDocument, updateDocument, uploadDocument } from "../api/loads";
import { errorMessage } from "../api/client";
import { ConfirmInline } from "../components/ConfirmInline";
import type { DocumentKind, LoadDetail, LoadDocument } from "../types/Load";
import { DOCUMENT_KINDS, kindLabel, when } from "./format";
import { ACCEPTED_FILES, openDocument, uploadProblem } from "./documents";

/** The load's paperwork: files from its emails and uploads, ready for the invoice. */
export function DocumentsSection({ load, onChanged }: { load: LoadDetail; onChanged: () => void }) {
  const inputId = useId();
  const [busy, setBusy] = useState<string | null>(null);
  const [progress, setProgress] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const files = [...(event.target.files ?? [])];
    event.target.value = "";

    if (files.length === 0) {
      return;
    }

    setBusy("upload");
    setError(null);
    const failed: string[] = [];

    for (const [index, file] of files.entries()) {
      setProgress(files.length > 1 ? `Uploading ${index + 1} of ${files.length}...` : "Uploading...");

      const problem = uploadProblem(file);

      if (problem) {
        failed.push(`${file.name}: ${problem}`);
        continue;
      }

      try {
        await uploadDocument(file, load.id);
      } catch (err) {
        failed.push(`${file.name}: ${errorMessage(err, "Couldn't upload it.")}`);
      }
    }

    setProgress(null);
    setBusy(null);

    if (failed.length > 0) {
      setError(failed.join(" "));
    }

    onChanged();
  }

  async function act(key: string, action: () => Promise<unknown>, fallback: string) {
    setBusy(key);
    setError(null);

    try {
      await action();
      onChanged();
    } catch (err) {
      setError(errorMessage(err, fallback));
    } finally {
      setBusy(null);
    }
  }

  async function open(document: LoadDocument) {
    setError(null);

    try {
      await openDocument(document);
    } catch (err) {
      setError(errorMessage(err, "Couldn't open this file. Please try again."));
    }
  }

  return (
    <section className="tk-panel" aria-label="Documents">
      <h2>
        Documents <span>{load.documents.length}</span>
      </h2>
      <p className="tk-sub">
        Rate con, BOL, POD and receipts for this load, from its emails and your uploads. Clerova reads each file to
        tell what it is.
      </p>

      {load.documents.length > 0 && (
        <ul className="tk-docs">
          {load.documents.map((document) => (
            <li key={`${document.source}-${document.id}`}>
              <span className="tk-pill tk-pill-info">{kindLabel(document.kind)}</span>
              <span className="tk-doc-main">
                {document.stored ? (
                  <button type="button" className="tk-link" onClick={() => void open(document)}>
                    {document.name || "Unnamed file"}
                  </button>
                ) : (
                  <span>{document.name || "Unnamed file"}</span>
                )}
                <span className="tk-sub">
                  {document.source === "UPLOAD" ? "Uploaded" : "From an email"} · {when(document.at)}
                  {!document.stored && " · only the name was kept"}
                </span>
              </span>

              {document.source === "UPLOAD" && (
                <span className="tk-doc-actions">
                  <select
                    aria-label={`What is ${document.name || "this file"}?`}
                    value={document.kind ?? "OTHER"}
                    disabled={busy !== null}
                    onChange={(event) =>
                      void act(
                        `kind-${document.id}`,
                        () => updateDocument(document.id, { kind: event.target.value as DocumentKind }),
                        "Couldn't change it."
                      )
                    }
                  >
                    {DOCUMENT_KINDS.map((item) => (
                      <option key={item.kind} value={item.kind}>
                        {item.label}
                      </option>
                    ))}
                  </select>
                  {removing === document.id ? (
                    <ConfirmInline
                      message="Remove this file?"
                      confirmLabel="Remove"
                      keepLabel="Keep"
                      onConfirm={() => {
                        setRemoving(null);
                        void act(`remove-${document.id}`, () => deleteDocument(document.id), "Couldn't remove it.");
                      }}
                      onKeep={() => setRemoving(null)}
                    />
                  ) : (
                    <button
                      type="button"
                      className="rq-button rq-button-small rq-button-quiet"
                      disabled={busy !== null}
                      onClick={() => setRemoving(document.id)}
                    >
                      Remove
                    </button>
                  )}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="tk-actions">
        {/* The input comes first so its keyboard focus can outline the label */}
        <input
          id={inputId}
          className="visually-hidden tk-file-input"
          type="file"
          accept={ACCEPTED_FILES}
          multiple
          disabled={busy !== null}
          onChange={(event) => void upload(event)}
        />
        <label htmlFor={inputId} className={`rq-button rq-button-small ${busy ? "is-disabled" : ""}`}>
          {progress ?? "Add a document"}
        </label>
        <span className="tk-sub">PDF or photo, up to 10 MB.</span>
      </div>

      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}

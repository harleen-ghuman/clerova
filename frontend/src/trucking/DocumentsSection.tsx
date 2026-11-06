import { useId, useState, type ChangeEvent } from "react";
import { deleteDocument, updateDocument, uploadDocument } from "../api/loads";
import { errorMessage } from "../api/client";
import { ConfirmInline } from "../components/ConfirmInline";
import type { DocumentKind, LoadDetail, LoadDocument } from "../types/Load";
import { DOCUMENT_KINDS, kindLabel, when } from "./format";
import { ACCEPTED_FILES, openDocument, uploadProblem } from "./documents";

/** The load's paperwork: files from its emails and uploads, ready for the invoice. */
/** The same file emailed twice (same name, size and kind) is listed once. */
function distinct(documents: LoadDocument[]): LoadDocument[] {
  const seen = new Set<string>();

  return documents.filter((document) => {
    const key = `${document.name}|${document.sizeBytes}|${document.kind}`;

    if (document.source === "UPLOAD" || !seen.has(key)) {
      seen.add(key);
      return true;
    }

    return false;
  });
}

export function DocumentsSection({ load, onChanged }: { load: LoadDetail; onChanged: () => void }) {
  const documents = distinct(load.documents);
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

  function open(document: LoadDocument) {
    setError(null);
    openDocument(document);
  }

  return (
    <section className="tk-panel" aria-label="Documents">
      <h2>
        Documents <span>{documents.length}</span>
      </h2>
      <p className="tk-sub">Paperwork for this load. Files from its emails are added for you.</p>

      {documents.length > 0 && (
        <ul className="tk-doclist">
          {documents.map((document) => {
            const name = document.name || "Unnamed file";
            const photo = (document.contentType ?? "").startsWith("image/") || /\.(jpe?g|png|webp|gif|heic)$/i.test(name);

            return (
              <li key={`${document.source}-${document.id}`} className="tk-doc">
                <button
                  type="button"
                  className="tk-doc-open"
                  onClick={() => open(document)}
                  disabled={!document.stored}
                >
                  <span className={`tk-doc-icon ${photo ? "photo" : ""}`} aria-hidden="true">
                    {photo ? "IMG" : "PDF"}
                  </span>
                  <span className="tk-doc-text">
                    <strong>{kindLabel(document.kind)}</strong>
                    <span className="tk-doc-name">{name}</span>
                    <span className="tk-doc-from">
                      {document.stored
                        ? `${document.source === "UPLOAD" ? "Uploaded" : "From an email"}${document.at ? ` · ${when(document.at)}` : ""}`
                        : "Only the name was kept"}
                    </span>
                  </span>
                  {document.stored && (
                    <span className="tk-doc-view" aria-hidden="true">
                      View <span>›</span>
                    </span>
                  )}
                </button>

                {document.source === "UPLOAD" && (
                  <div className="tk-doc-manage">
                    <label>
                      <span>Type</span>
                      <select
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
                    </label>
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
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}

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
      <label htmlFor={inputId} className={`tk-doc-add ${busy ? "is-disabled" : ""}`}>
        <strong>{progress ?? "+ Add a document"}</strong>
        <span>BOL, POD, receipt or lumper slip. PDF or photo, up to 10 MB.</span>
      </label>

      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}

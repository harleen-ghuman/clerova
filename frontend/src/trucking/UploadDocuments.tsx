import { useEffect, useId, useState, type ChangeEvent } from "react";
import { deleteDocument, getUnfiledDocuments, updateDocument, uploadDocument } from "../api/loads";
import { errorMessage } from "../api/client";
import type { LoadSummary, UploadedDocument } from "../types/Load";
import { ACCEPTED_FILES, openDocument, uploadProblem } from "./documents";
import { LoadPicker } from "./LoadPicker";
import { kindLabel, loadLabel } from "./format";

interface UploadDocumentsProps {
  loads: LoadSummary[];
  refreshKey: number;
  onOpenLoad: (id: string) => void;
  /** A document was filed or made a load; refresh the board. */
  onChanged: () => void;
}

/**
 * Upload paperwork from anywhere (a driver's photo, a broker portal).
 * Clerova reads each file and files it under the load it names; the rest
 * wait here for the dispatcher to pick the load.
 */
export function UploadDocuments({ loads, refreshKey, onOpenLoad, onChanged }: UploadDocumentsProps) {
  const inputId = useId();
  const [items, setItems] = useState<UploadedDocument[]>([]);
  const [progress, setProgress] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [moving, setMoving] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let current = true;

    getUnfiledDocuments()
      .then((unfiled) => {
        if (current) {
          // Keep this visit's uploads, and add any still waiting from before
          setItems((existing) => [
            ...existing,
            ...unfiled.filter((document) => !existing.some((item) => item.id === document.id)),
          ]);
        }
      })
      .catch(() => {
        // The board still works; the waiting list just isn't shown
      });

    return () => {
      current = false;
    };
  }, [refreshKey]);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const files = [...(event.target.files ?? [])];
    event.target.value = "";

    if (files.length === 0) {
      return;
    }

    setError(null);
    const failed: string[] = [];

    for (const [index, file] of files.entries()) {
      setProgress(
        files.length > 1 ? `Reading ${index + 1} of ${files.length}...` : "Reading the document..."
      );

      const problem = uploadProblem(file);

      if (problem) {
        failed.push(`${file.name}: ${problem}`);
        continue;
      }

      try {
        const uploaded = await uploadDocument(file);
        setItems((current) => [uploaded, ...current.filter((item) => item.id !== uploaded.id)]);
      } catch (err) {
        failed.push(`${file.name}: ${errorMessage(err, "Couldn't upload it.")}`);
      }
    }

    setProgress(null);

    if (failed.length > 0) {
      setError(failed.join(" "));
    }

    onChanged();
  }

  async function file(document: UploadedDocument, loadId: string) {
    setBusyId(document.id);
    setError(null);

    try {
      const updated = await updateDocument(document.id, { loadId });
      setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setMoving(null);
      onChanged();
    } catch (err) {
      setError(errorMessage(err, "Couldn't file it. Please try again."));
    } finally {
      setBusyId(null);
    }
  }

  async function remove(document: UploadedDocument) {
    setBusyId(document.id);
    setError(null);

    try {
      await deleteDocument(document.id);
      setItems((current) => current.filter((item) => item.id !== document.id));
    } catch (err) {
      setError(errorMessage(err, "Couldn't remove it. Please try again."));
    } finally {
      setBusyId(null);
    }
  }

  async function open(document: UploadedDocument) {
    try {
      await openDocument({ id: document.id, source: "UPLOAD" });
    } catch (err) {
      setError(errorMessage(err, "Couldn't open this file. Please try again."));
    }
  }

  const byId = new Map(loads.map((load) => [load.id, load]));

  return (
    <section className="tk-panel tk-upload" aria-label="Upload documents">
      <div className="tk-upload-top">
        <div>
          <h2>Upload documents</h2>
          <p className="tk-sub">
            A BOL, POD or receipt from a driver's phone or a broker portal. Clerova reads it and files it under its
            load. A rate con makes a new load.
          </p>
        </div>
        <div className="tk-actions">
          <input
            id={inputId}
            className="visually-hidden tk-file-input"
            type="file"
            accept={ACCEPTED_FILES}
            multiple
            disabled={progress !== null}
            onChange={(event) => void upload(event)}
          />
          <label htmlFor={inputId} className={`rq-button rq-button-small rq-button-primary ${progress ? "is-disabled" : ""}`}>
            {progress ?? "Upload documents"}
          </label>
        </div>
      </div>

      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}

      {items.length > 0 && (
        <ul className="tk-docs" aria-live="polite">
          {items.map((document) => {
            const load = document.loadId ? byId.get(document.loadId) : undefined;

            return (
              <li key={document.id}>
                <span className="tk-pill tk-pill-info">{kindLabel(document.kind)}</span>
                <span className="tk-doc-main">
                  <button type="button" className="tk-link" onClick={() => void open(document)}>
                    {document.name}
                  </button>
                  <span className="tk-sub">
                    {document.newLoad
                      ? "Made a new load from this rate con"
                      : document.loadId
                        ? `Filed under ${load ? loadLabel(load) : "a load"}`
                        : "Clerova couldn't tell which load this is for"}
                  </span>
                  {(moving === document.id || !document.loadId) && (
                    <LoadPicker
                      loads={loads.filter((item) => item.id !== document.loadId)}
                      label={document.loadId ? "Move it to" : "Which load is this for?"}
                      confirmLabel="File it"
                      busy={busyId === document.id}
                      onPick={(loadId) => void file(document, loadId)}
                      onCancel={document.loadId ? () => setMoving(null) : undefined}
                    />
                  )}
                </span>
                <span className="tk-doc-actions">
                  {document.loadId && moving !== document.id && (
                    <>
                      <button
                        type="button"
                        className="rq-button rq-button-small"
                        onClick={() => onOpenLoad(document.loadId!)}
                      >
                        Open load
                      </button>
                      {!document.newLoad && (
                        <button
                          type="button"
                          className="rq-button rq-button-small rq-button-quiet"
                          onClick={() => setMoving(document.id)}
                        >
                          Move
                        </button>
                      )}
                    </>
                  )}
                  {!document.loadId && (
                    <button
                      type="button"
                      className="rq-button rq-button-small rq-button-quiet"
                      disabled={busyId === document.id}
                      onClick={() => void remove(document)}
                    >
                      Remove
                    </button>
                  )}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

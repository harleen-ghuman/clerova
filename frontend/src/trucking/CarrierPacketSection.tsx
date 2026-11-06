import { useEffect, useId, useState, type ChangeEvent } from "react";
import { fetchFile } from "../api/attachments";
import { errorMessage } from "../api/client";
import {
  CARRIER_DOCUMENT_KINDS,
  deleteCarrierDocument,
  listCarrierDocuments,
  updateCarrierDocument,
  uploadCarrierDocument,
  type CarrierDocument,
  type CarrierDocumentKind,
} from "../api/carrierPacket";
import { ConfirmInline } from "../components/ConfirmInline";
import { viewFile } from "../utils/viewFile";
import { downloadFile } from "./billing";
import { ACCEPTED_FILES, uploadProblem } from "./documents";
import { SendCarrierPacket } from "./SendCarrierPacket";

const SOON_DAYS = 30;

/** In packet order, as the server sends them: authority, insurance, W-9... */
function inPacketOrder(documents: CarrierDocument[]): CarrierDocument[] {
  const order = CARRIER_DOCUMENT_KINDS.map((item) => item.kind);
  return [...documents].sort((a, b) => order.indexOf(a.kind) - order.indexOf(b.kind));
}

function expiryText(document: CarrierDocument): { text: string; tone: "fix" | "note" | "" } | null {
  if (!document.expiresOn) {
    return null;
  }

  const ends = new Date(`${document.expiresOn}T12:00:00`);
  const label = ends.toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" });
  const days = Math.ceil((ends.getTime() - Date.now()) / 86_400_000);

  return document.expired
    ? { text: `Expired ${label}`, tone: "fix" }
    : days <= SOON_DAYS
      ? { text: `Ends ${label}, in ${days} ${days === 1 ? "day" : "days"}`, tone: "note" }
      : { text: `Valid until ${label}`, tone: "" };
}

/** The carrier packet brokers ask for before the first load: kept once, sent in one click. */
export function CarrierPacketSection({ sendingEnabled }: { sendingEnabled: boolean }) {
  const id = useId();
  const [documents, setDocuments] = useState<CarrierDocument[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [kind, setKind] = useState<CarrierDocumentKind>("AUTHORITY");
  const [expiresOn, setExpiresOn] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    listCarrierDocuments()
      .then(setDocuments)
      .catch((err) => setLoadError(errorMessage(err, "Couldn't load your carrier packet. Refresh to try again.")));
  }, []);

  const missing = CARRIER_DOCUMENT_KINDS.filter(
    (item) => ["AUTHORITY", "INSURANCE", "W9"].includes(item.kind) && !(documents ?? []).some((document) => document.kind === item.kind)
  );

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";

    if (!file) {
      return;
    }

    const problem = uploadProblem(file);

    if (problem) {
      setError(problem);
      return;
    }

    setBusy("upload");
    setError(null);

    try {
      const saved = await uploadCarrierDocument(file, kind, expiresOn || null);
      setDocuments((current) => inPacketOrder([...(current ?? []), saved]));
      setExpiresOn("");
    } catch (err) {
      setError(errorMessage(err, "Couldn't upload the file. Please try again."));
    } finally {
      setBusy(null);
    }
  }

  async function change(document: CarrierDocument, update: { kind?: CarrierDocumentKind; expiresOn?: string }) {
    setBusy(document.id);
    setError(null);

    try {
      const saved = await updateCarrierDocument(document.id, update);
      setDocuments((current) => inPacketOrder((current ?? []).map((item) => (item.id === saved.id ? saved : item))));
    } catch (err) {
      setError(errorMessage(err, "Couldn't change the document. Please try again."));
    } finally {
      setBusy(null);
    }
  }

  async function remove(document: CarrierDocument) {
    setBusy(document.id);
    setError(null);

    try {
      await deleteCarrierDocument(document.id);
      setDocuments((current) => (current ?? []).filter((item) => item.id !== document.id));
      setRemoving(null);
    } catch (err) {
      setError(errorMessage(err, "Couldn't remove the document. Please try again."));
    } finally {
      setBusy(null);
    }
  }

  async function download() {
    setBusy("download");
    setError(null);

    try {
      await downloadFile("/api/carrier/packet", "Carrier packet.pdf");
    } catch (err) {
      setError(errorMessage(err, "Couldn't make the packet. Please try again."));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="settings-section" id="carrier-packet" aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`}>Carrier packet</h2>
      <p>
        Brokers ask for these before your first load with them. Add them once, then send the whole packet to any broker
        in one click, as one PDF.
      </p>

      {loadError && <p className="action-error">{loadError}</p>}

      {documents && documents.length > 0 && (
        <ul className="tk-doclist carrier-docs">
          {documents.map((document) => {
            const expiry = expiryText(document);

            return (
              <li key={document.id} className="tk-doc">
                <button
                  type="button"
                  className="tk-doc-open"
                  onClick={() =>
                    viewFile({
                      name: document.name,
                      subtitle: document.kindLabel,
                      load: () => fetchFile(`/api/carrier/documents/${document.id}/file`),
                    })
                  }
                >
                  <span className="tk-doc-text">
                    <strong>{document.kindLabel}</strong>
                    <span className="tk-sub">{document.name}</span>
                  </span>
                  <span className="tk-doc-view">View</span>
                </button>
                <div className="carrier-doc-meta">
                  {expiry && <span className={`tk-pill ${expiry.tone ? `tk-pill-${expiry.tone}` : "tk-pill-good"}`}>{expiry.text}</span>}
                  <label>
                    <span className="visually-hidden">What {document.name} is</span>
                    <select
                      value={document.kind}
                      disabled={busy !== null}
                      onChange={(event) => void change(document, { kind: event.target.value as CarrierDocumentKind })}
                    >
                      {CARRIER_DOCUMENT_KINDS.map((item) => (
                        <option key={item.kind} value={item.kind}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    <span className="visually-hidden">When {document.name} ends</span>
                    <input
                      type="date"
                      value={document.expiresOn ?? ""}
                      disabled={busy !== null}
                      onChange={(event) => void change(document, { expiresOn: event.target.value })}
                    />
                  </label>
                  {removing !== document.id && (
                    <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={() => setRemoving(document.id)}>
                      Remove
                    </button>
                  )}
                </div>
                {removing === document.id && (
                  <ConfirmInline
                    message={`Remove ${document.kindLabel} from the carrier packet?`}
                    confirmLabel="Remove"
                    keepLabel="Keep it"
                    busy={busy === document.id}
                    onConfirm={() => void remove(document)}
                    onKeep={() => setRemoving(null)}
                  />
                )}
              </li>
            );
          })}
        </ul>
      )}

      {documents && missing.length > 0 && (
        <p className="tk-hint">Most brokers ask for: {missing.map((item) => item.label).join(", ")}.</p>
      )}

      <div className="carrier-upload" role="group" aria-label="Add a document">
        <label>
          What it is
          <select value={kind} onChange={(event) => setKind(event.target.value as CarrierDocumentKind)} disabled={busy !== null}>
            {CARRIER_DOCUMENT_KINDS.map((item) => (
              <option key={item.kind} value={item.kind}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Ends on (optional)
          <input type="date" value={expiresOn} onChange={(event) => setExpiresOn(event.target.value)} disabled={busy !== null} />
        </label>
        <label className="rq-button rq-button-small carrier-upload-file">
          {busy === "upload" ? "Uploading..." : "Add the file"}
          <input type="file" accept={ACCEPTED_FILES} onChange={(event) => void upload(event)} disabled={busy !== null} className="visually-hidden" />
        </label>
      </div>
      <p className="tk-hint">{CARRIER_DOCUMENT_KINDS.find((item) => item.kind === kind)?.hint}. PDF or photo, up to 10 MB.</p>

      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}

      {documents && documents.length > 0 && !sending && (
        <div className="settings-actions">
          <button type="button" className="rq-button rq-button-small rq-button-primary" onClick={() => setSending(true)} disabled={busy !== null}>
            Email it to a broker
          </button>
          <button type="button" className="rq-button rq-button-small" onClick={() => void download()} disabled={busy !== null}>
            {busy === "download" ? "Making it..." : "Download packet"}
          </button>
        </div>
      )}
      {sending && <SendCarrierPacket sendingEnabled={sendingEnabled} onDone={() => setSending(false)} />}
    </section>
  );
}

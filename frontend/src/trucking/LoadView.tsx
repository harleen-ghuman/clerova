import { useEffect, useState, type FormEvent } from "react";
import { dismissReply, getLoad, resolveFlag, sendReply, updateLoad } from "../api/loads";
import { errorMessage } from "../api/client";
import { AutoTextarea } from "../components/AutoTextarea";
import { ConfirmInline } from "../components/ConfirmInline";
import { FileList } from "../components/FileList";
import { DocumentsSection } from "./DocumentsSection";
import { Email } from "./Email";
import type { LoadDetail, LoadStatus } from "../types/Load";
import { STATUS_LABELS, city, detention, money, when } from "./format";

interface LoadViewProps {
  loadId: string;
  sendingEnabled: boolean;
  onBack: () => void;
  /** Something changed; refresh the lists. */
  onChanged: () => void;
}

/** Broker emails can be long (quoted history, signatures): show the start first. */
const LONG_EMAIL = 900;

function EmailBody({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  const long = text.length > LONG_EMAIL;
  const shown = long && !open ? text.slice(0, LONG_EMAIL).replace(/\s+\S*$/, "") + "…" : text;

  return (
    <>
      <p className="tk-msg-body">{shown}</p>
      {long && (
        <button type="button" className="tk-msg-more" aria-expanded={open} onClick={() => setOpen(!open)}>
          {open ? "Show less" : "Show the whole email"}
        </button>
      )}
    </>
  );
}

/** What the dispatcher does next, by where the load is. */
function nextStep(load: LoadDetail): { text: string; to?: LoadStatus; label?: string } {
  const open = load.flags.filter((flag) => !flag.resolvedAt);

  switch (load.status) {
    case "BOOKED":
      return open.length > 0
        ? {
            text: `Check the ${open.length === 1 ? "item" : `${open.length} items`} below before you sign the rate con.`,
            to: "IN_TRANSIT",
            label: "Mark picked up",
          }
        : {
            text: "The rate con looks right. Sign it, send it back, and mark the load picked up when the driver loads.",
            to: "IN_TRANSIT",
            label: "Mark picked up",
          };
    case "IN_TRANSIT":
      return { text: "On the way. Keep the latest update current so replies to the broker are right.", to: "DELIVERED", label: "Mark delivered" };
    case "DELIVERED":
      return { text: "Delivered. Get the signed POD from the driver. Invoicing comes in the next update." };
    case "CANCELLED":
      return { text: "This load was cancelled." };
    default:
      return { text: `This load is ${STATUS_LABELS[load.status].toLowerCase()}.` };
  }
}

export function LoadView({ loadId, sendingEnabled, onBack, onChanged }: LoadViewProps) {
  const [load, setLoad] = useState<LoadDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [draft, setDraft] = useState("");
  const [confirmCancel, setConfirmCancel] = useState(false);

  useEffect(() => {
    let current = true;

    getLoad(loadId)
      .then((result) => {
        if (current) {
          setLoad(result);
          setNote(result.statusNote ?? "");
          setDraft(result.draft?.body ?? "");
          setError(null);
        }
      })
      .catch((err) => {
        if (current) {
          setError(errorMessage(err, "Couldn't open this load."));
        }
      });

    return () => {
      current = false;
    };
  }, [loadId]);

  async function run(key: string, action: () => Promise<LoadDetail>, fallback: string) {
    setBusy(key);
    setError(null);

    try {
      const result = await action();
      setLoad(result);
      setNote(result.statusNote ?? "");
      setDraft(result.draft?.body ?? "");
      onChanged();
    } catch (err) {
      setError(errorMessage(err, fallback));
    } finally {
      setBusy(null);
    }
  }

  function saveNote(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void run("note", () => updateLoad(loadId, { statusNote: note }), "Couldn't save the update.");
  }

  if (!load) {
    return (
      <div className="tk-page">
        <button type="button" className="tk-backlink" onClick={onBack}>
          <span aria-hidden="true">←</span> Back to loads
        </button>
        {error ? <p className="action-error" role="alert">{error}</p> : <p className="tk-none">Loading the load...</p>}
      </div>
    );
  }

  const step = nextStep(load);
  const origin = load.stops.find((stop) => stop.kind === "PICKUP")?.place ?? load.stops[0]?.place ?? null;
  const destination = [...load.stops].reverse().find((stop) => stop.kind === "DELIVERY")?.place ?? null;
  const noteChanged = note.trim() !== (load.statusNote ?? "");

  return (
    <div className="tk-page">
      <button type="button" className="tk-backlink" onClick={onBack}>
        <span aria-hidden="true">←</span> Back to loads
      </button>

      <div className="tk-head">
        <div className="tk-head-main">
          <div className="tk-head-title">
            <h1>
              {city(origin)} → {city(destination)}
            </h1>
            <span className={`tk-pill tk-status tk-status-${load.status.toLowerCase()}`}>{STATUS_LABELS[load.status]}</span>
          </div>
          <p className="tk-meta">
            <span>{load.loadNumber ? `Load #${load.loadNumber}` : "No load number"}</span>
            {load.broker && <span>{load.broker.name}</span>}
            {load.rateCents !== null && <span className="tk-money">{money(load.rateCents)}</span>}
          </p>
        </div>
      </div>

      {error && <p className="action-error" role="alert">{error}</p>}

      <div className="tk-detail">
        <div className="tk-stack">
          <section className="tk-panel tk-next" aria-label="Next step">
            <p>{step.text}</p>
            <div className="tk-actions">
              {step.to && (
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-primary"
                  disabled={busy !== null}
                  onClick={() => void run("status", () => updateLoad(loadId, { status: step.to }), "Couldn't update the load.")}
                >
                  {step.label}
                </button>
              )}
              {(load.status === "BOOKED" || load.status === "IN_TRANSIT") && !confirmCancel && (
                <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={() => setConfirmCancel(true)}>
                  Cancel load
                </button>
              )}
            </div>
            {confirmCancel && (
              <ConfirmInline
                message="Cancel this load? It moves to Closed."
                confirmLabel="Cancel load"
                keepLabel="Keep it"
                busy={busy !== null}
                onConfirm={() => {
                  setConfirmCancel(false);
                  void run("status", () => updateLoad(loadId, { status: "CANCELLED" }), "Couldn't cancel the load.");
                }}
                onKeep={() => setConfirmCancel(false)}
              />
            )}

            <form className="tk-note-form" onSubmit={saveNote}>
              <label className="visually-hidden" htmlFor="load-note">
                Latest update
              </label>
              <AutoTextarea
                id="load-note"
                rows={1}
                maxLength={500}
                placeholder="Latest update, like: Loaded 9:40, ETA Phoenix 6 AM"
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
              <button type="submit" className="rq-button rq-button-small" disabled={busy !== null || !noteChanged}>
                {busy === "note" ? "Saving..." : "Save update"}
              </button>
            </form>
            <p className="tk-hint">Drafted replies to the broker use this update, and nothing else about where the truck is.</p>
          </section>

          {load.draft && (
            <section className="tk-panel tk-draft" aria-label="Drafted reply">
              <h2>Reply to approve</h2>
              {load.draft.summary && <p className="tk-sub">{load.draft.summary}</p>}
              <label htmlFor="draft-reply">
                To <Email value={load.draft.toEmail} />. Edit it if you like. Your company name is added at the end.
              </label>
              <AutoTextarea
                id="draft-reply"
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                disabled={busy !== null}
              />
              <div className="tk-actions" style={{ marginTop: 8 }}>
                {sendingEnabled ? (
                  <button
                    type="button"
                    className="rq-button rq-button-small rq-button-primary"
                    disabled={busy !== null || !draft.trim()}
                    onClick={() => void run("send", () => sendReply(loadId, load.draft!.id, draft), "Couldn't send the reply.")}
                  >
                    {busy === "send" ? "Sending..." : "Approve and send"}
                  </button>
                ) : (
                  <span className="tk-sub">Sending isn't set up yet. Copy the reply into your own email.</span>
                )}
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-quiet"
                  disabled={busy !== null}
                  onClick={() => void run("dismiss", () => dismissReply(loadId, load.draft!.id), "Couldn't dismiss the draft.")}
                >
                  Dismiss
                </button>
              </div>
            </section>
          )}

          {load.flags.length > 0 && (
            <section className="tk-panel" aria-label="Rate con check">
              <h2>Rate con check</h2>
              {load.flags.map((flag) => (
                <div key={flag.id} className={`tk-flag ${flag.resolvedAt ? "done" : ""}`}>
                  <span className={`tk-pill tk-pill-${flag.resolvedAt ? "good" : flag.level === "FIX" ? "fix" : "note"}`}>
                    {flag.resolvedAt ? "Done" : flag.level === "FIX" ? "Fix" : "Note"}
                  </span>
                  <div>
                    <strong>{flag.title}</strong>
                    {flag.detail && <p>{flag.detail}</p>}
                  </div>
                  {flag.sourceText && <blockquote>{flag.sourceText}</blockquote>}
                  {!flag.resolvedAt && (
                    <div className="tk-actions">
                      <button
                        type="button"
                        className="rq-button rq-button-small"
                        disabled={busy !== null}
                        onClick={() => void run(flag.id, () => resolveFlag(loadId, flag.id), "Couldn't update this item.")}
                      >
                        {flag.level === "FIX" ? "It's sorted" : "Got it"}
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </section>
          )}

          <DocumentsSection
            load={load}
            onChanged={() => void run("documents", () => getLoad(loadId), "Couldn't refresh this load.")}
          />

          <section className="tk-panel" aria-label="Emails">
            <h2>Emails</h2>
            <div className="tk-thread">
              {load.conversation.length === 0 && <p className="tk-none">No emails on this load yet.</p>}
              {load.conversation.map((entry) => (
                <article key={entry.id} className={`tk-msg ${entry.incoming ? "" : "out"}`}>
                  <div className="tk-msg-head">
                    <span>{entry.incoming ? entry.from : `You → ${entry.to}`}</span>
                    <span>{when(entry.at)}</span>
                  </div>
                  {entry.subject && <strong className="tk-msg-subject">{entry.subject}</strong>}
                  {entry.body && <EmailBody text={entry.body.trim()} />}
                  {entry.files.length > 0 && (
                    <FileList
                      files={entry.files.map((file) => ({
                        id: file.id,
                        fileName: file.name,
                        contentType: file.contentType,
                        sizeBytes: file.sizeBytes,
                        available: file.stored,
                      }))}
                    />
                  )}
                </article>
              ))}
            </div>
          </section>
        </div>

        <div className="tk-stack">
          <section className="tk-panel" aria-label="Stops">
            <h2>Stops</h2>
            <ol className="tk-stops">
              {load.stops.map((stop, index) => (
                <li key={index} className={`tk-stop tk-stop-${stop.kind === "PICKUP" ? "pickup" : "delivery"}`}>
                  <span className="tk-eyebrow">{stop.kind === "PICKUP" ? "Pickup" : "Delivery"}</span>
                  <span className="tk-stop-place">{stop.place}</span>
                  {stop.appointment && <span className="tk-stop-time">{stop.appointment}</span>}
                </li>
              ))}
              {load.stops.length === 0 && <li className="tk-none">The rate con's stops couldn't be read.</li>}
            </ol>
          </section>

          <section className="tk-panel" aria-label="Rate and terms">
            <h2>Rate and terms</h2>
            <dl className="tk-kv">
              <dt>Rate</dt>
              <dd>{load.rateCents !== null ? money(load.rateCents) : "Not read"}</dd>
              {load.agreedRateCents !== null && (
                <>
                  <dt>Email said</dt>
                  <dd>{money(load.agreedRateCents)}</dd>
                </>
              )}
              <dt>Detention</dt>
              <dd>
                {detention(load.detentionRateCents, load.detentionFreeMinutes)}
                {load.detentionRule && <div className="tk-sub">{load.detentionRule}</div>}
              </dd>
              {load.tonuCents !== null && (
                <>
                  <dt>TONU</dt>
                  <dd>{money(load.tonuCents)}</dd>
                </>
              )}
              {load.paperworkRule && (
                <>
                  <dt>Paperwork</dt>
                  <dd>{load.paperworkRule}</dd>
                </>
              )}
              {load.paymentTerms && (
                <>
                  <dt>Payment</dt>
                  <dd>{load.paymentTerms}</dd>
                </>
              )}
              {load.equipment && (
                <>
                  <dt>Equipment</dt>
                  <dd>{load.equipment}</dd>
                </>
              )}
              {load.rateConFile && (
                <>
                  <dt>Rate con</dt>
                  <dd>{load.rateConFile}</dd>
                </>
              )}
            </dl>
          </section>

          {load.broker && (
            <section className="tk-panel" aria-label="Broker">
              <h2>Broker</h2>
              <dl className="tk-kv">
                <dt>Name</dt>
                <dd>{load.broker.name}</dd>
                <dt>MC</dt>
                <dd>{load.broker.mcNumber ?? "Missing"}</dd>
                <dt>FMCSA</dt>
                <dd>
                  {load.broker.fmcsaStatus === "ACTIVE"
                    ? "Active broker authority"
                    : load.broker.fmcsaStatus === "NOT_AUTHORIZED"
                      ? "No active broker authority"
                      : load.broker.fmcsaStatus === "NOT_FOUND"
                        ? "Not found"
                        : "Not checked"}
                </dd>
                {load.broker.email && (
                  <>
                    <dt>Email</dt>
                    <dd>
                      <Email value={load.broker.email} />
                    </dd>
                  </>
                )}
                {load.broker.phone && (
                  <>
                    <dt>Phone</dt>
                    <dd>{load.broker.phone}</dd>
                  </>
                )}
              </dl>
            </section>
          )}
        </div>
      </div>
    </div>
  );
}

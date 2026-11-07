import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { dismissReply, getLoad, resolveFlag, sendLoadMessage, sendReply, updateLoad } from "../api/loads";
import { errorMessage } from "../api/client";
import { AutoTextarea } from "../components/AutoTextarea";
import { ConfirmInline } from "../components/ConfirmInline";
import { FileList } from "../components/FileList";
import { ChargesPanel } from "./ChargesPanel";
import { DocumentsSection } from "./DocumentsSection";
import { distinctDocuments } from "./documents";
import { flashSection, onLoadTab, showLoadTab, type LoadTab, type OpenLoadOptions } from "./loadTabs";
import { InvoicePanel } from "./InvoicePanel";
import { StopsPanel } from "./StopsPanel";
import { MessageComposer } from "./MessageComposer";
import { SendCarrierPacket } from "./SendCarrierPacket";
import { Email } from "./Email";
import type { LoadDetail, LoadStatus } from "../types/Load";
import { STATUS_LABELS, city, detention, kindLabel, money, when } from "./format";

interface LoadViewProps {
  loadId: string;
  sendingEnabled: boolean;
  onBack: () => void;
  /** Something changed; refresh the lists. */
  onChanged: () => void;
  onOpenSettings: () => void;
  /** Open on a tab, or at the rate con check. */
  openWith?: OpenLoadOptions | null;
}

const TABS: { id: LoadTab; label: string }[] = [
  { id: "overview", label: "Overview" },
  { id: "documents", label: "Documents" },
  { id: "emails", label: "Emails" },
];

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
            text: `Go through the rate con check first: ${open.length === 1 ? "1 item is" : `${open.length} items are`} still open. Mark each one done, then sign the rate con and mark the load picked up.`,
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
      return {
        text: load.invoice
          ? "Delivered. Finish the invoice and send it."
          : load.documents.some((document) => document.kind === "POD" || document.kind === "BOL")
            ? "Delivered, and the signed paperwork is in Documents. Create the invoice."
            : "Delivered. Add the signed BOL or POD in the Documents tab, then create the invoice.",
      };
    case "INVOICED":
      return { text: "Invoiced. Mark it paid on the invoice when the money arrives." };
    case "PAID":
      return { text: "Paid. This load is done." };
    case "CANCELLED":
      return { text: "This load was cancelled." };
  }
}

/** A draft made while the truck was on the way may not fit once the load has moved on. */
function draftIsOld(load: LoadDetail): boolean {
  if (!load.draft || load.status === "BOOKED" || load.status === "IN_TRANSIT") {
    return false;
  }

  const last = load.stops[load.stops.length - 1];
  const done = last?.leftAt ?? last?.arrivedAt;
  return load.status === "CANCELLED" || (done != null && Date.parse(load.draft.createdAt) <= Date.parse(done));
}

/** "Net 31" spelled out, so it's clear when the money comes. */
function paymentText(terms: string): string {
  const days = terms.match(/\bnet\s*-?\s*(\d{1,3})\b/i)?.[1];
  return days && !/days? after/i.test(terms) ? `${terms}: paid ${days} days after the invoice` : terms;
}

/** Emails newest first, like an inbox. */
function newestFirst<T extends { at: string | null }>(entries: T[]): T[] {
  const time = (entry: T) => (entry.at ? Date.parse(entry.at) : 0);
  return [...entries].sort((a, b) => time(b) - time(a));
}

/** The load's document for a file on one of its emails, if it's listed under Documents. */
function filedDocument(load: LoadDetail, file: { id: string }) {
  return load.documents.find((document) => document.source === "EMAIL" && document.id === file.id);
}

export function LoadView({ loadId, sendingEnabled, onBack, onChanged, onOpenSettings, openWith }: LoadViewProps) {
  const [load, setLoad] = useState<LoadDetail | null>(null);
  const [tab, setTab] = useState<LoadTab>(openWith?.tab ?? "overview");
  const checksShown = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [draft, setDraft] = useState("");
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [confirmBack, setConfirmBack] = useState(false);
  const [emailingUpdate, setEmailingUpdate] = useState(false);
  const [updateSent, setUpdateSent] = useState<string | null>(null);
  const [showDoneChecks, setShowDoneChecks] = useState(false);
  const [sendingPacket, setSendingPacket] = useState(false);

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

  // Panels can ask for a tab ("See Emails", "Open Documents"); the page then starts at the top
  useEffect(
    () =>
      onLoadTab((next) => {
        setTab(next);
        window.requestAnimationFrame(() => document.getElementById("load-tabs")?.scrollIntoView({ block: "start" }));
      }),
    []
  );

  // Opened from "to fix" or "to check": go straight to the rate con check, highlighted
  useEffect(() => {
    if (load && openWith?.checks && !checksShown.current) {
      checksShown.current = true;
      window.requestAnimationFrame(() => flashSection("load-checks"));
    }
  }, [load, openWith]);

  async function run(key: string, action: () => Promise<LoadDetail>, fallback: string): Promise<boolean> {
    setBusy(key);
    setError(null);

    try {
      apply(await action());
      onChanged();
      return true;
    } catch (err) {
      setError(errorMessage(err, fallback));
      return false;
    } finally {
      setBusy(null);
    }
  }

  /**
   * Shows the load as the server sent it, keeping what the dispatcher is
   * typing: the update box and the draft reply only change when they changed
   * on the server (a new draft, or a saved update).
   */
  function apply(result: LoadDetail) {
    if (!load || result.statusNote !== load.statusNote) {
      setNote(result.statusNote ?? "");
    }

    if (!load || result.draft?.id !== load.draft?.id) {
      setDraft(result.draft?.body ?? "");
    }

    setLoad(result);
  }

  async function reload() {
    const result = await getLoad(loadId);
    apply(result);
    onChanged();
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
  const showInvoice =
    ["DELIVERED", "INVOICED", "PAID"].includes(load.status) ||
    Boolean(load.invoice) ||
    (load.status === "CANCELLED" && load.charges.some((charge) => charge.status === "APPROVED"));
  const origin = load.stops.find((stop) => stop.kind === "PICKUP")?.place ?? load.stops[0]?.place ?? null;
  const destination = [...load.stops].reverse().find((stop) => stop.kind === "DELIVERY")?.place ?? null;
  const noteChanged = note.trim() !== (load.statusNote ?? "");
  const documentCount = distinctDocuments(load.documents).length;
  const openFlags = load.flags.filter((flag) => !flag.resolvedAt).length;
  // One step back, or reopening a cancelled load, when something changed (the server checks the same rules)
  const back: { to: LoadStatus; label: string; question: string } | null =
    load.status === "IN_TRANSIT"
      ? { to: "BOOKED", label: "Move back to Booked", question: "Move this load back to Booked? Use this if it wasn't picked up after all." }
      : load.status === "DELIVERED" && !load.invoice
        ? { to: "IN_TRANSIT", label: "Move back to In transit", question: "Move this load back to In transit? Use this if it wasn't delivered after all." }
        : load.status === "CANCELLED" && !load.invoice
          ? { to: "BOOKED", label: "Reopen load", question: "Reopen this load? It goes back to Booked." }
          : null;

  // Arrow keys move between tabs, as screen reader users expect
  function moveTab(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") {
      return;
    }

    const index = TABS.findIndex((item) => item.id === tab);
    const next = TABS[(index + (event.key === "ArrowRight" ? 1 : TABS.length - 1)) % TABS.length].id;
    setTab(next);
    window.requestAnimationFrame(() => document.getElementById(`load-tab-${next}`)?.focus());
  }

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

      <div className="tk-tabs" role="tablist" aria-label="This load" id="load-tabs" onKeyDown={moveTab}>
        {TABS.map((item) => {
          const count = item.id === "documents" ? documentCount : item.id === "emails" ? load.conversation.length : null;

          return (
            <button
              key={item.id}
              type="button"
              role="tab"
              id={`load-tab-${item.id}`}
              aria-selected={tab === item.id}
              aria-controls={`load-panel-${item.id}`}
              tabIndex={tab === item.id ? 0 : -1}
              className={`tk-tab ${tab === item.id ? "active" : ""}`}
              onClick={() => setTab(item.id)}
            >
              {item.label}
              {count !== null && <span className="tk-tab-count">{count}</span>}
            </button>
          );
        })}
      </div>

      {tab === "documents" && (
        <div role="tabpanel" id="load-panel-documents" aria-labelledby="load-tab-documents" className="tk-tabpanel">
          <DocumentsSection
          load={load}
          onChanged={() => void run("documents", () => getLoad(loadId), "Couldn't refresh this load.")}
          />
        </div>
      )}

      {tab === "emails" && (
        <div role="tabpanel" id="load-panel-emails" aria-labelledby="load-tab-emails" className="tk-tabpanel">
          <section className="tk-panel" aria-label="Emails" id="load-emails">
            <h2>Emails</h2>
            <div className="tk-thread">
              {load.conversation.length === 0 && <p className="tk-none">No emails on this load yet.</p>}
              {newestFirst(load.conversation).map((entry) => (
                <article key={entry.id} className={`tk-msg ${entry.incoming ? "" : "out"}`}>
                  <div className="tk-msg-head">
                    <span>
                      {entry.incoming ? (
                        entry.from
                      ) : (
                        <>
                          <span className="tk-pill tk-pill-info">Sent</span> by you to {entry.to}
                        </>
                      )}
                    </span>
                    <span>{when(entry.at)}</span>
                  </div>
                  {entry.subject && <strong className="tk-msg-subject">{entry.subject}</strong>}
                  {entry.body && <EmailBody text={entry.body.trim()} />}
                  {/* Files already listed under Documents are named here, not shown twice */}
                  {entry.files.some((file) => filedDocument(load, file)) && (
                    <p className="tk-msg-filed">
                      {entry.files
                        .map((file) => filedDocument(load, file))
                        .filter((document) => document !== undefined)
                        .map((document) => `${kindLabel(document.kind)}: ${document.name}`)
                        .join(", ")}{" "}
                      · in the{" "}
                      <button type="button" className="tk-link" onClick={() => showLoadTab("documents")}>
                        Documents tab
                      </button>
                    </p>
                  )}
                  {entry.files.some((file) => !filedDocument(load, file)) && (
                    <FileList
                      files={entry.files.filter((file) => !filedDocument(load, file)).map((file) => ({
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
      )}

      {tab === "overview" && (
      <div className="tk-detail" role="tabpanel" id="load-panel-overview" aria-labelledby="load-tab-overview">
        <div className="tk-stack">
          <section className="tk-panel tk-next" aria-label="Next step">
            <p>
              {step.text}
              {openFlags > 0 && (
                <>
                  {" "}
                  <button type="button" className="tk-link" onClick={() => flashSection("load-checks")}>
                    Show {openFlags === 1 ? "it" : "them"}
                  </button>
                </>
              )}
            </p>
            <div className="tk-actions">
              {step.to && (
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-primary"
                  disabled={busy !== null || (load.status === "BOOKED" && openFlags > 0)}
                  title={load.status === "BOOKED" && openFlags > 0 ? "Finish the rate con check first" : undefined}
                  onClick={() => void run("status", () => updateLoad(loadId, { status: step.to }), "Couldn't update the load.")}
                >
                  {step.label}
                </button>
              )}
              {back && !confirmBack && !confirmCancel && (
                <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={() => setConfirmBack(true)}>
                  {back.label}
                </button>
              )}
              {(load.status === "BOOKED" || load.status === "IN_TRANSIT") && !confirmCancel && !confirmBack && (
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
            {confirmBack && back && (
              <ConfirmInline
                message={back.question}
                confirmLabel={back.label}
                keepLabel="Keep it"
                tone="primary"
                busy={busy !== null}
                onConfirm={() => {
                  setConfirmBack(false);
                  void run("status", () => updateLoad(loadId, { status: back.to }), "Couldn't change the status.");
                }}
                onKeep={() => setConfirmBack(false)}
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
              {!["PAID", "CANCELLED"].includes(load.status) && !emailingUpdate && (
                <button
                  type="button"
                  className="rq-button rq-button-small"
                  disabled={busy !== null}
                  onClick={() => {
                    setEmailingUpdate(true);
                    setUpdateSent(null);
                  }}
                >
                  Email the broker
                </button>
              )}
            </form>
            {emailingUpdate && (
              <MessageComposer
                draft={{
                  title: "Tell the broker what's happening",
                  to: load.broker?.email ?? "",
                  subject: `${load.loadNumber ? `Load ${load.loadNumber}` : "Load"}: update`,
                  body: `Hi,\n\nAn update on ${load.loadNumber ? `load ${load.loadNumber}` : "this load"}: ${note.trim() || "[what happened, like: the truck broke down near Barstow. New ETA tomorrow 10 AM.]"}\n\nThanks,`,
                }}
                sendingEnabled={sendingEnabled}
                busy={busy === "update-email"}
                onSend={(message) =>
                  void (async () => {
                    // Save the update too, so drafted replies know about it
                    if (noteChanged) {
                      await run("note", () => updateLoad(loadId, { statusNote: note }), "Couldn't save the update.");
                    }

                    const sent = await run("update-email", () => sendLoadMessage(loadId, message), "Couldn't send the email.");

                    if (sent) {
                      setEmailingUpdate(false);
                      setUpdateSent(message.to);
                    }
                  })()
                }
                onCancel={() => setEmailingUpdate(false)}
              />
            )}
            {updateSent && (
              <p className="request-success tk-sent-note" role="status">
                Sent to {updateSent}. The broker's reply will show in this load's Emails tab.{" "}
                <button type="button" className="tk-link" onClick={() => showLoadTab("emails")}>
                  See Emails
                </button>
              </p>
            )}
            <p className="tk-hint">
              Something happened, like a breakdown or a delay? Write it here, then Email the broker. Drafted replies use this update too.
            </p>
          </section>

          {load.draft && (
            <section className="tk-panel tk-draft" aria-label="Drafted reply">
              <h2>Reply to approve</h2>
              {load.draft.summary && <p className="tk-sub">{load.draft.summary}</p>}
              {draftIsOld(load) && (
                <p className="tk-callout">
                  This was drafted before the load was {load.status === "CANCELLED" ? "cancelled" : "delivered"}. Check it still fits, or
                  dismiss it.
                </p>
              )}
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

          <StopsPanel load={load} sendingEnabled={sendingEnabled} busy={busy} run={run} />

          <ChargesPanel load={load} sendingEnabled={sendingEnabled} busy={busy} run={run} />

        </div>

        <div className="tk-stack">
          <section className="tk-panel tk-terms" aria-label="Rate and terms">
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
                {load.detentionMaxCents !== null && `, up to ${money(load.detentionMaxCents)}`}
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
                  <dd>
                    {paymentText(load.paymentTerms)}
                    {load.quickPayPercent !== null && <div className="tk-sub">Quick pay: {load.quickPayPercent}% fee</div>}
                  </dd>
                </>
              )}
              {load.invoiceEmail && (
                <>
                  <dt>Invoices to</dt>
                  <dd>
                    <Email value={load.invoiceEmail} />
                  </dd>
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

          {load.flags.length > 0 && (
            <section className="tk-panel" aria-label="Rate con check" id="load-checks">
              <h2>
                Rate con check
                <span className={`tk-pill tk-pill-${openFlags > 0 ? "note" : "good"}`}>
                  {openFlags > 0 ? `${openFlags} open` : "All done"}
                </span>
              </h2>
              {openFlags === 0 && !showDoneChecks && (
                <p className="tk-sub">
                  {load.flags.length === 1 ? "The one item was checked." : `All ${load.flags.length} items were checked.`}{" "}
                  <button type="button" className="tk-link" onClick={() => setShowDoneChecks(true)}>
                    Show them
                  </button>
                </p>
              )}
              {load.flags.filter((flag) => !flag.resolvedAt || showDoneChecks).map((flag) => (
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
              {openFlags > 0 && load.flags.length > openFlags && (
                <button type="button" className="tk-link tk-link-small" onClick={() => setShowDoneChecks((shown) => !shown)}>
                  {showDoneChecks ? "Hide the done items" : `Show ${load.flags.length - openFlags} done`}
                </button>
              )}
              {openFlags === 0 && showDoneChecks && (
                <button type="button" className="tk-link tk-link-small" onClick={() => setShowDoneChecks(false)}>
                  Hide them
                </button>
              )}
            </section>
          )}

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
              {sendingPacket ? (
                <SendCarrierPacket to={load.broker.email ?? ""} sendingEnabled={sendingEnabled} onDone={() => setSendingPacket(false)} />
              ) : (
                <div className="tk-actions">
                  <button type="button" className="rq-button rq-button-small" onClick={() => setSendingPacket(true)}>
                    Send carrier packet
                  </button>
                </div>
              )}
            </section>
          )}
        </div>

        {/* Once delivered, the invoice is the work: full width, first on a computer, after the next step on a phone */}
        {showInvoice && (
          <div className="tk-invoice-row">
            <InvoicePanel load={load} sendingEnabled={sendingEnabled} onChanged={reload} onOpenSettings={onOpenSettings} />
          </div>
        )}

      </div>
      )}
    </div>
  );
}

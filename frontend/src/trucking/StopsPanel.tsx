import { useEffect, useState } from "react";
import { sendLoadMessage, setStopTimes } from "../api/loads";
import type { LoadDetail, LoadStop } from "../types/Load";
import { showLoadTab } from "./loadTabs";
import { MessageComposer, type MessageDraft } from "./MessageComposer";
import { clock, duration, emailTime, fromLocalInput, money, placeName, toLocalInput, when } from "./format";

interface StopsPanelProps {
  load: LoadDetail;
  sendingEnabled: boolean;
  busy: string | null;
  run: (key: string, action: () => Promise<LoadDetail>, fallback: string) => Promise<boolean>;
}

const LOCKED = ["PAID", "CANCELLED"];

function loadName(load: LoadDetail): string {
  return load.loadNumber ? `load ${load.loadNumber}` : "this load";
}

function subjectFor(load: LoadDetail, what: string): string {
  return `${load.loadNumber ? `Load ${load.loadNumber}` : "Load"}: ${what}`;
}

/** The arrival notice many rate cons require for detention. */
function arrivalNotice(load: LoadDetail, stop: LoadStop): MessageDraft {
  const appointment = stop.appointment ? ` The appointment was ${stop.appointment}.` : "";

  return {
    title: "Tell the broker the truck arrived",
    to: load.broker?.email ?? "",
    subject: subjectFor(load, `arrived at ${placeName(stop.place)}`),
    body:
      `Hi,\n\nOur driver arrived at ${placeName(stop.place)} at ${emailTime(stop.arrivedAt!)} for ${loadName(load)}.` +
      `${appointment} Please note the arrival time in case of detention.\n\nThanks,`,
  };
}

/** Tells the broker the truck left, with the time on site, as many rate cons ask for detention. */
function departureNotice(load: LoadDetail, stop: LoadStop, leftAt: string): MessageDraft {
  const onSite = stop.arrivedAt ? Math.max(0, (new Date(leftAt).getTime() - new Date(stop.arrivedAt).getTime()) / 60_000) : null;
  const free = load.detentionFreeMinutes ?? 120;
  const over = onSite !== null ? onSite - free : 0;
  const arrival = stop.arrivedAt && onSite !== null
    ? ` They arrived at ${emailTime(stop.arrivedAt)}, so they were on site ${duration(onSite)}.`
    : "";
  const detention = over > 0
    ? ` That is ${duration(over)} past the ${duration(free)} free time, so detention applies. We'll send the detention claim separately.`
    : "";

  return {
    title: "Tell the broker the truck left",
    to: load.broker?.email ?? "",
    subject: subjectFor(load, `left ${placeName(stop.place)}`),
    body:
      `Hi,\n\nOur driver left ${placeName(stop.place)} at ${emailTime(leftAt)} for ${loadName(load)}.` +
      `${arrival}${detention}\n\nThanks,`,
  };
}

/** A heads-up once the free time is over and the truck is still there. */
function detentionStarted(load: LoadDetail, stop: LoadStop): MessageDraft {
  const rate = load.detentionRateCents !== null ? ` at ${money(load.detentionRateCents)}/hour` : "";

  return {
    title: "Tell the broker detention started",
    to: load.broker?.email ?? "",
    subject: subjectFor(load, `detention started at ${placeName(stop.place)}`),
    body:
      `Hi,\n\nOur driver is still at ${placeName(stop.place)} for ${loadName(load)}. They arrived at ` +
      `${emailTime(stop.arrivedAt!)}, and the free time ended at ${emailTime(stop.detention!.freeEndsAt)}. ` +
      `Detention started then${rate}. Please approve it, or let us know what you need.\n\nThanks,`,
  };
}

/** "Left 12:26 AM", with the date when the truck left on another day than it arrived. */
function leftText(arrivedAt: string, leftAt: string): string {
  const sameDay = new Date(arrivedAt).toDateString() === new Date(leftAt).toDateString();
  return sameDay
    ? new Date(leftAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })
    : new Date(leftAt).toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

/** Opens the load's Emails tab, where sent messages and the broker's replies are. */
function showEmails() {
  showLoadTab("emails");
}

function StopTimesForm({ stop, busy, onSave, onCancel }: {
  stop: LoadStop;
  busy: boolean;
  onSave: (arrivedAt: string | null, leftAt: string | null) => void;
  onCancel: () => void;
}) {
  const [arrived, setArrived] = useState(toLocalInput(stop.arrivedAt));
  const [left, setLeft] = useState(toLocalInput(stop.leftAt));

  return (
    <form
      className="tk-times-form"
      aria-label={`${stop.kind === "PICKUP" ? "Pickup" : "Delivery"} times`}
      onSubmit={(event) => {
        event.preventDefault();
        onSave(fromLocalInput(arrived), fromLocalInput(left));
      }}
    >
      <strong className="tk-times-title">
        {stop.kind === "PICKUP" ? "Pickup" : "Delivery"} times at {placeName(stop.place)}
      </strong>
      <label>
        Arrived
        <input type="datetime-local" value={arrived} onChange={(event) => setArrived(event.target.value)} disabled={busy} />
      </label>
      <label>
        Left
        <input type="datetime-local" value={left} onChange={(event) => setLeft(event.target.value)} disabled={busy} />
      </label>
      <div className="tk-actions">
        <button type="submit" className="rq-button rq-button-small rq-button-primary" disabled={busy}>
          Save times
        </button>
        <button type="button" className="rq-button rq-button-small rq-button-quiet" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

/** The stops, with Arrived and Left to log detention as it happens. */
export function StopsPanel({ load, sendingEnabled, busy, run }: StopsPanelProps) {
  const [now, setNow] = useState(() => Date.now());
  const [editing, setEditing] = useState<string | null>(null);
  const [composing, setComposing] = useState<{ stopId: string; draft: MessageDraft } | null>(null);
  const [sentNote, setSentNote] = useState<{ stopId: string; to: string } | null>(null);
  const locked = LOCKED.includes(load.status);
  const waiting = load.stops.some((stop) => stop.arrivedAt && !stop.leftAt);

  // While a truck waits at a stop, keep the time on site current
  useEffect(() => {
    if (!waiting) {
      return;
    }

    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [waiting]);

  async function log(stop: LoadStop, field: "arrivedAt" | "leftAt") {
    const time = new Date().toISOString();
    const saved = await run(
      `${field}-${stop.id}`,
      () =>
        setStopTimes(load.id, stop.id, {
          arrivedAt: field === "arrivedAt" ? time : stop.arrivedAt,
          leftAt: field === "leftAt" ? time : stop.leftAt,
        }),
      "Couldn't save the time."
    );

    // Many rate cons pay detention only if the broker hears about the arrival
    if (saved && field === "arrivedAt") {
      setComposing({ stopId: stop.id, draft: arrivalNotice(load, { ...stop, arrivedAt: time }) });
    }

    // And the departure, so the time on site is on record with the broker too
    if (saved && field === "leftAt") {
      setComposing({ stopId: stop.id, draft: departureNotice(load, stop, time) });
    }
  }

  return (
    <section className="tk-panel" aria-label="Stops">
      <h2>Stops</h2>
      <ol className="tk-stops">
        {load.stops.map((stop, index) => {
          const detention = stop.detention;
          const onSite = stop.arrivedAt
            ? Math.max(0, ((stop.leftAt ? new Date(stop.leftAt).getTime() : now) - new Date(stop.arrivedAt).getTime()) / 60_000)
            : 0;
          const freeLeft = detention ? (new Date(detention.freeEndsAt).getTime() - now) / 60_000 : 0;
          const previousLeft = index === 0 || load.stops[index - 1].leftAt !== null || load.stops[index - 1].arrivedAt === null;

          return (
            <li key={stop.id} className={`tk-stop tk-stop-${stop.kind === "PICKUP" ? "pickup" : "delivery"}`}>
              <span className="tk-eyebrow">{stop.kind === "PICKUP" ? "Pickup" : "Delivery"}</span>
              <span className="tk-stop-place">{stop.place}</span>
              {stop.appointment && <span className="tk-stop-time">{stop.appointment}</span>}

              {editing === stop.id ? (
                <StopTimesForm
                  stop={stop}
                  busy={busy !== null}
                  onSave={(arrivedAt, leftAt) =>
                    void run(`times-${stop.id}`, () => setStopTimes(load.id, stop.id, { arrivedAt, leftAt }), "Couldn't save the times.")
                      .then((saved) => saved && setEditing(null))
                  }
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <>
                  {stop.arrivedAt && (
                    <span className="tk-stop-log">
                      Arrived {clock(stop.arrivedAt)}
                      {stop.leftAt && ` · Left ${leftText(stop.arrivedAt, stop.leftAt)}`} · {duration(onSite)} on site
                    </span>
                  )}

                  {stop.arrivedAt && !stop.leftAt && detention && (
                    <span className={`tk-stop-detention ${freeLeft <= 0 ? "over" : freeLeft <= 30 ? "soon" : ""}`}>
                      {freeLeft > 0
                        ? `Free time ends ${clock(detention.freeEndsAt)} (${duration(freeLeft)} left)`
                        : `Detention since ${clock(detention.freeEndsAt)}: ${duration(-freeLeft)}`}
                    </span>
                  )}

                  {stop.leftAt && detention && detention.billableMinutes > 0 && (
                    <span className="tk-stop-detention over">
                      Detention: {duration(detention.billableMinutes)} after the free time
                      {detention.amountCents !== null && ` · ${money(detention.amountCents)}`}
                    </span>
                  )}

                  {stop.leftAt && detention && detention.billableMinutes > 0 && detention.amountCents === null && (
                    <span className="tk-stop-log">
                      The rate con has no detention rate. Add the amount under Extra pay.
                    </span>
                  )}

                  {(stop.emails ?? []).length > 0 && (
                    <ul className="tk-stop-sent" aria-label="Emails sent about this stop">
                      {(stop.emails ?? []).map((email, emailIndex) => (
                        <li key={emailIndex}>
                          Emailed {email.toEmail}
                          {email.sentAt ? ` · ${when(email.sentAt)}` : ""}
                          {email.subject ? `: ${email.subject}` : ""}
                        </li>
                      ))}
                    </ul>
                  )}

                  {sentNote?.stopId === stop.id && (
                    <p className="request-success tk-sent-note" role="status">
                      Sent to {sentNote.to}. The broker's reply will show in this load's Emails tab.{" "}
                      <button type="button" className="tk-link" onClick={showEmails}>
                        See Emails
                      </button>
                    </p>
                  )}

                  {!locked && (
                    <div className="tk-actions tk-stop-actions">
                      {!stop.arrivedAt && previousLeft && (
                        <button
                          type="button"
                          className="rq-button rq-button-small rq-button-primary"
                          disabled={busy !== null}
                          onClick={() => void log(stop, "arrivedAt")}
                        >
                          {busy === `arrivedAt-${stop.id}` ? "Saving..." : "Arrived"}
                        </button>
                      )}
                      {stop.arrivedAt && !stop.leftAt && (
                        <button
                          type="button"
                          className="rq-button rq-button-small rq-button-primary"
                          disabled={busy !== null}
                          onClick={() => void log(stop, "leftAt")}
                        >
                          {busy === `leftAt-${stop.id}` ? "Saving..." : "Left"}
                        </button>
                      )}
                      {stop.arrivedAt && !stop.leftAt && detention && composing?.stopId !== stop.id && (
                        <button
                          type="button"
                          className="tk-link tk-link-small"
                          onClick={() =>
                            setComposing({
                              stopId: stop.id,
                              draft: freeLeft <= 0 ? detentionStarted(load, stop) : arrivalNotice(load, stop),
                            })
                          }
                        >
                          {freeLeft <= 0 ? "Tell the broker detention started" : "Tell the broker"}
                        </button>
                      )}
                      <button type="button" className="tk-link tk-link-small" onClick={() => setEditing(stop.id)}>
                        {stop.arrivedAt ? "Edit" : "Enter"} {stop.kind === "PICKUP" ? "pickup" : "delivery"} times
                      </button>
                    </div>
                  )}
                </>
              )}

              {composing?.stopId === stop.id && (
                <MessageComposer
                  draft={composing.draft}
                  sendingEnabled={sendingEnabled}
                  busy={busy === `message-${stop.id}`}
                  onSend={(message) =>
                    void run(
                      `message-${stop.id}`,
                      () => sendLoadMessage(load.id, { ...message, stopId: stop.id }),
                      "Couldn't send the email."
                    ).then((sent) => {
                      if (sent) {
                        setComposing(null);
                        setSentNote({ stopId: stop.id, to: message.to });
                      }
                    })
                  }
                  onCancel={() => setComposing(null)}
                />
              )}
            </li>
          );
        })}
        {load.stops.length === 0 && <li className="tk-none">The rate con's stops couldn't be read.</li>}
      </ol>
      {!locked && load.stops.length > 0 && (
        <p className="tk-hint">
          Tap Arrived and Left at each stop. Clerova works out detention from the rate con:{" "}
          {load.detentionFreeMinutes !== null ? duration(load.detentionFreeMinutes) : "2h (not stated)"} free
          {load.detentionRateCents !== null ? `, then ${money(load.detentionRateCents)}/hour` : ""}
          {load.detentionMaxCents !== null ? `, up to ${money(load.detentionMaxCents)}` : ""}.
        </p>
      )}
    </section>
  );
}

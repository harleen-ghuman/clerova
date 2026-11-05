import { useEffect, useState } from "react";
import { sendLoadMessage, setStopTimes } from "../api/loads";
import type { LoadDetail, LoadStop } from "../types/Load";
import { MessageComposer, type MessageDraft } from "./MessageComposer";
import { clock, duration, emailTime, fromLocalInput, money, placeName, toLocalInput } from "./format";

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
      onSubmit={(event) => {
        event.preventDefault();
        onSave(fromLocalInput(arrived), fromLocalInput(left));
      }}
    >
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
                      {stop.leftAt && ` · Left ${clock(stop.leftAt)}`} · {duration(onSite)} on site
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
                        {stop.arrivedAt ? "Edit times" : "Enter times"}
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
                    void run(`message-${stop.id}`, () => sendLoadMessage(load.id, message), "Couldn't send the email.").then(
                      (sent) => sent && setComposing(null)
                    )
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

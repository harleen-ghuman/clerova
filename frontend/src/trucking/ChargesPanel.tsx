import { useId, useState, type FormEvent } from "react";
import { showLoadTab } from "./loadTabs";
import { addCharge, deleteCharge, sendLoadMessage, updateCharge } from "../api/loads";
import { ConfirmInline } from "../components/ConfirmInline";
import type { ChargeKind, ChargeStatus, LoadCharge, LoadDetail } from "../types/Load";
import { MessageComposer, type MessageDraft } from "./MessageComposer";
import { duration, emailTime, money, parseCents, placeName, when } from "./format";

interface ChargesPanelProps {
  load: LoadDetail;
  sendingEnabled: boolean;
  busy: string | null;
  run: (key: string, action: () => Promise<LoadDetail>, fallback: string) => Promise<boolean>;
}

const KINDS: { kind: ChargeKind; label: string }[] = [
  { kind: "LUMPER", label: "Lumper" },
  { kind: "DETENTION", label: "Detention" },
  { kind: "LAYOVER", label: "Layover" },
  { kind: "TONU", label: "Truck ordered, not used" },
  { kind: "OTHER", label: "Other" },
];

const STATUSES: { status: ChargeStatus; label: string }[] = [
  { status: "TO_CLAIM", label: "To claim" },
  { status: "CLAIMED", label: "Claimed" },
  { status: "APPROVED", label: "Approved" },
  { status: "DENIED", label: "Denied" },
];

const TONE: Record<ChargeStatus, string> = {
  TO_CLAIM: "note",
  CLAIMED: "info",
  APPROVED: "good",
  DENIED: "fix",
};

/** The detention claim email, with the times and the math. */
function detentionClaim(load: LoadDetail, charge: LoadCharge): MessageDraft {
  const stop = load.stops.find((item) => item.id === charge.stopId);
  const detention = stop?.detention;
  const lines: string[] = [];

  if (stop?.arrivedAt) {
    lines.push(`Arrived: ${emailTime(stop.arrivedAt)}`);
  }

  if (stop?.leftAt) {
    lines.push(`Left: ${emailTime(stop.leftAt)}`);
  }

  if (detention) {
    lines.push(`Time on site: ${duration(detention.minutesOnSite)}`);
    lines.push(`Free time: ${duration(detention.freeMinutes)}`);
    lines.push(
      `Detention: ${duration(detention.billableMinutes)}` +
        (load.detentionRateCents !== null ? ` at ${money(load.detentionRateCents)}/hour` : "")
    );
  }

  lines.push(`Amount: ${money(charge.amountCents)}`);
  const where = stop ? ` at ${placeName(stop.place)}` : "";

  return {
    title: "Send the detention claim",
    to: load.broker?.email ?? "",
    subject: `${load.loadNumber ? `Load ${load.loadNumber}` : "Load"}: detention claim${where}`,
    body:
      `Hi,\n\nWe're claiming detention for ${load.loadNumber ? `load ${load.loadNumber}` : "this load"}${where}.\n\n` +
      `${lines.join("\n")}\n\nPlease confirm, and we'll add it to the invoice.\n\nThanks,`,
  };
}

function AddChargeForm({ busy, tonuCents, cancelled, onAdd, onCancel }: {
  busy: boolean;
  /** The TONU on the rate con, filled in when TONU is picked. */
  tonuCents: number | null;
  cancelled: boolean;
  onAdd: (charge: { kind: ChargeKind; description: string; amountCents: number; status: ChargeStatus }) => void;
  onCancel: () => void;
}) {
  const id = useId();
  const [kind, setKind] = useState<ChargeKind>(cancelled ? "TONU" : "LUMPER");
  const [amount, setAmount] = useState(cancelled && tonuCents !== null ? (tonuCents / 100).toFixed(2) : "");
  const [note, setNote] = useState("");
  const [status, setStatus] = useState<ChargeStatus>("APPROVED");
  const [problem, setProblem] = useState<string | null>(null);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const cents = parseCents(amount);

    if (cents === null || cents <= 0) {
      setProblem("Enter the amount, like 75.00.");
      return;
    }

    setProblem(null);
    onAdd({ kind, description: note.trim(), amountCents: cents, status });
  }

  return (
    <form className="tk-charge-form" onSubmit={submit}>
      <label htmlFor={`${id}-kind`}>Charge</label>
      <select id={`${id}-kind`} value={kind} onChange={(event) => {
          const next = event.target.value as ChargeKind;
          setKind(next);

          if (next === "TONU" && tonuCents !== null && !amount.trim()) {
            setAmount((tonuCents / 100).toFixed(2));
          }
        }} disabled={busy}>
        {KINDS.map((item) => (
          <option key={item.kind} value={item.kind}>
            {item.label}
          </option>
        ))}
      </select>
      <label htmlFor={`${id}-amount`}>Amount</label>
      <input
        id={`${id}-amount`}
        inputMode="decimal"
        placeholder="0.00"
        value={amount}
        onChange={(event) => setAmount(event.target.value)}
        disabled={busy}
      />
      <label htmlFor={`${id}-note`}>Note (optional)</label>
      <input
        id={`${id}-note`}
        maxLength={255}
        placeholder="Like: Capstone receipt #5521"
        value={note}
        onChange={(event) => setNote(event.target.value)}
        disabled={busy}
      />
      <label htmlFor={`${id}-status`}>Broker</label>
      <select
        id={`${id}-status`}
        value={status}
        onChange={(event) => setStatus(event.target.value as ChargeStatus)}
        disabled={busy}
      >
        {STATUSES.map((item) => (
          <option key={item.status} value={item.status}>
            {item.label}
          </option>
        ))}
      </select>
      {problem && (
        <p className="action-error" role="alert">
          {problem}
        </p>
      )}
      <div className="tk-actions">
        <button type="submit" className="rq-button rq-button-small rq-button-primary" disabled={busy}>
          Add charge
        </button>
        <button type="button" className="rq-button rq-button-small rq-button-quiet" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

/** Detention, lumpers and other money on top of the rate. Approved ones go on the invoice. */
export function ChargesPanel({ load, sendingEnabled, busy, run }: ChargesPanelProps) {
  const [adding, setAdding] = useState(false);
  const [claiming, setClaiming] = useState<{ chargeId: string; draft: MessageDraft } | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);
  // A cancelled load can still carry a TONU; only a sent invoice fixes the charges
  const locked = load.invoice !== null && load.invoice.status !== "DRAFT";
  const cancelled = load.status === "CANCELLED";
  const approved = load.charges.filter((charge) => charge.status === "APPROVED");

  return (
    <section className="tk-panel" aria-label="Extra pay">
      <h2>
        Extra pay <span>{load.charges.length}</span>
      </h2>
      <p className="tk-sub">
        Detention, lumpers, layovers and TONU. Approved ones go on the invoice
        {approved.length > 0 ? `: ${money(approved.reduce((sum, charge) => sum + charge.amountCents, 0))} so far.` : "."}
      </p>

      {load.charges.length > 0 && (
        <ul className="tk-charges">
          {load.charges.map((charge) => (
            <li key={charge.id}>
              <div className="tk-charge-main">
                <span className="tk-charge-title">
                  {charge.label}
                  <span className="tk-money">{money(charge.amountCents)}</span>
                </span>
                {charge.description && <span className="tk-sub">{charge.description}</span>}
              </div>

              <div className="tk-charge-actions">
                {locked ? (
                  <span className={`tk-pill tk-pill-${TONE[charge.status]}`}>
                    {STATUSES.find((item) => item.status === charge.status)?.label}
                  </span>
                ) : (
                  <>
                    <select
                      aria-label={`Where the ${charge.label.toLowerCase()} charge is with the broker`}
                      className={`tk-status-select tk-status-select-${TONE[charge.status]}`}
                      value={charge.status}
                      disabled={busy !== null}
                      onChange={(event) =>
                        void run(
                          `charge-${charge.id}`,
                          () => updateCharge(load.id, charge.id, { status: event.target.value as ChargeStatus }),
                          "Couldn't update the charge."
                        )
                      }
                    >
                      {STATUSES.map((item) => (
                        <option key={item.status} value={item.status}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                    {charge.status === "TO_CLAIM" && claiming?.chargeId !== charge.id && (
                      <button
                        type="button"
                        className="rq-button rq-button-small"
                        disabled={busy !== null}
                        onClick={() =>
                          setClaiming({
                            chargeId: charge.id,
                            draft:
                              charge.kind === "DETENTION"
                                ? detentionClaim(load, charge)
                                : {
                                    title: `Claim the ${charge.label.toLowerCase()}`,
                                    to: load.broker?.email ?? "",
                                    subject: `${load.loadNumber ? `Load ${load.loadNumber}` : "Load"}: ${charge.label.toLowerCase()} ${money(charge.amountCents)}`,
                                    body:
                                      `Hi,\n\nPlease approve ${charge.label.toLowerCase()} of ${money(charge.amountCents)} for ` +
                                      `${load.loadNumber ? `load ${load.loadNumber}` : "this load"}` +
                                      `${charge.description ? ` (${charge.description})` : ""}. We'll add it to the invoice.\n\nThanks,`,
                                  },
                          })
                        }
                      >
                        Send claim
                      </button>
                    )}
                    {removing === charge.id ? (
                      <ConfirmInline
                        message="Remove this charge?"
                        confirmLabel="Remove"
                        keepLabel="Keep"
                        onConfirm={() => {
                          setRemoving(null);
                          void run(`charge-${charge.id}`, () => deleteCharge(load.id, charge.id), "Couldn't remove the charge.");
                        }}
                        onKeep={() => setRemoving(null)}
                      />
                    ) : (
                      <button
                        type="button"
                        className="rq-button rq-button-small rq-button-quiet"
                        disabled={busy !== null}
                        onClick={() => setRemoving(charge.id)}
                      >
                        Remove
                      </button>
                    )}
                  </>
                )}
              </div>

              {charge.claimEmail && (
                <p className="tk-charge-sent">
                  Claim emailed to {charge.claimEmail.toEmail}
                  {charge.claimEmail.sentAt ? ` · ${when(charge.claimEmail.sentAt)}` : ""}.{" "}
                  The broker's reply will show in this load's Emails tab.{" "}
                  <button
                    type="button"
                    className="tk-link"
                    onClick={() => showLoadTab("emails")}
                  >
                    See Emails
                  </button>
                </p>
              )}

              {claiming?.chargeId === charge.id && (
                <MessageComposer
                  draft={claiming.draft}
                  sendingEnabled={sendingEnabled}
                  busy={busy === `claim-${charge.id}`}
                  sendLabel="Send claim"
                  onSend={(message) =>
                    void run(
                      `claim-${charge.id}`,
                      () => sendLoadMessage(load.id, { ...message, chargeId: charge.id }),
                      "Couldn't send the claim."
                    ).then((sent) => sent && setClaiming(null))
                  }
                  onCancel={() => setClaiming(null)}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      {!locked &&
        (adding ? (
          <AddChargeForm
            busy={busy !== null}
            tonuCents={load.tonuCents}
            cancelled={cancelled}
            onAdd={(charge) =>
              void run("add-charge", () => addCharge(load.id, charge), "Couldn't add the charge.").then(
                (added) => added && setAdding(false)
              )
            }
            onCancel={() => setAdding(false)}
          />
        ) : (
          <div className="tk-actions">
            <button type="button" className="rq-button rq-button-small" disabled={busy !== null} onClick={() => setAdding(true)}>
              Add a charge
            </button>
          </div>
        ))}
      {!locked && load.invoice?.status === "DRAFT" && (
        <p className="tk-hint">
          Changed extra pay after making the invoice? Use "Start over from the rate and approved extra pay" on the
          invoice to bring it in.
        </p>
      )}
      {locked && load.invoice && (
        <p className="tk-hint">The invoice was sent, so these can't change. Void it on the invoice to make changes.</p>
      )}
    </section>
  );
}

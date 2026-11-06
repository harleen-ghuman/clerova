import { useEffect, useState } from "react";
import { errorMessage } from "../api/client";
import {
  createInvoice,
  markInvoicePaid,
  markInvoiceSent,
  markInvoiceUnpaid,
  sendInvoice,
  updateInvoice,
  voidInvoice,
} from "../api/loads";
import { AutoTextarea } from "../components/AutoTextarea";
import { ConfirmInline } from "../components/ConfirmInline";
import type { Invoice, InvoiceLine, LoadDetail } from "../types/Load";
import { downloadFile, dueText, isOverdue, openPdf } from "./billing";
import { centsInput, kindLabel, money, parseCents } from "./format";

interface InvoicePanelProps {
  load: LoadDetail;
  sendingEnabled: boolean;
  /** Reloads the load, which carries the invoice. */
  onChanged: () => Promise<void>;
  onOpenSettings: () => void;
}

function defaultMessage(load: LoadDetail, invoice: Invoice): string {
  const lane = load.loadNumber ? `load ${load.loadNumber}` : "this load";
  return invoice.sendTo === "FACTOR"
    ? `Hi,\n\nAttached is invoice ${invoice.number} for ${lane}${load.broker ? ` (${load.broker.name})` : ""}, ` +
        `with the rate con and signed paperwork, for ${money(invoice.totalCents)}. Please fund it.\n\nThanks,`
    : `Hi,\n\nAttached is invoice ${invoice.number} for ${lane}, for ${money(invoice.totalCents - (invoice.quickPayFeeCents ?? 0))}` +
        `${invoice.quickPay ? " (quick pay requested)" : ""}, with the rate con and signed paperwork.\n\nThanks,`;
}

/** Lines being edited, as text so a half-typed amount isn't lost. */
type EditLine = { description: string; amount: string };

function LinesEditor({ invoice, busy, onSave, onDirtyChange }: {
  invoice: Invoice;
  busy: boolean;
  onSave: (lines: InvoiceLine[]) => Promise<boolean>;
  /** Unsaved edits: the invoice can't go out until they're saved or undone. */
  onDirtyChange: (dirty: boolean) => void;
}) {
  const [lines, setLines] = useState<EditLine[]>(
    invoice.lines.map((line) => ({ description: line.description, amount: centsInput(line.amountCents) }))
  );
  const [problem, setProblem] = useState<string | null>(null);
  const original = JSON.stringify(
    invoice.lines.map((line) => ({ description: line.description, amount: centsInput(line.amountCents) }))
  );
  const changed = JSON.stringify(lines) !== original;
  const total = lines.reduce((sum, line) => sum + (parseCents(line.amount) ?? 0), 0);

  useEffect(() => {
    onDirtyChange(changed);
  }, [changed, onDirtyChange]);

  function update(index: number, change: Partial<EditLine>) {
    setLines((current) => current.map((line, i) => (i === index ? { ...line, ...change } : line)));
  }

  async function save() {
    const parsed: InvoiceLine[] = [];

    for (const line of lines) {
      const cents = parseCents(line.amount);

      if (!line.description.trim()) {
        setProblem("Give each line a short description.");
        return;
      }

      if (cents === null) {
        setProblem(`Enter an amount for "${line.description.trim()}", like 75.00.`);
        return;
      }

      parsed.push({ description: line.description.trim(), amountCents: cents });
    }

    setProblem(null);
    await onSave(parsed);
  }

  return (
    <div className="tk-lines">
      {lines.map((line, index) => (
        <div className="tk-line" key={index}>
          <input
            aria-label={`Line ${index + 1} description`}
            maxLength={255}
            value={line.description}
            onChange={(event) => update(index, { description: event.target.value })}
            disabled={busy}
          />
          <input
            aria-label={`Line ${index + 1} amount`}
            className="tk-line-amount"
            inputMode="decimal"
            value={line.amount}
            onChange={(event) => update(index, { amount: event.target.value })}
            disabled={busy}
          />
          <button
            type="button"
            className="tk-line-remove"
            aria-label={`Remove line ${index + 1}`}
            disabled={busy || lines.length === 1}
            onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
          >
            ×
          </button>
        </div>
      ))}
      <div className="tk-lines-foot">
        <button
          type="button"
          className="tk-link tk-link-small"
          disabled={busy || lines.length >= 20}
          onClick={() => setLines((current) => [...current, { description: "", amount: "" }])}
        >
          Add a line
        </button>
        <strong className="tk-money">Total {money(total)}</strong>
      </div>
      {problem && (
        <p className="action-error" role="alert">
          {problem}
        </p>
      )}
      {changed && (
        <div className="tk-actions">
          <button type="button" className="rq-button rq-button-small rq-button-primary" disabled={busy} onClick={() => void save()}>
            Save lines
          </button>
          <button
            type="button"
            className="rq-button rq-button-small rq-button-quiet"
            disabled={busy}
            onClick={() => {
              setLines(JSON.parse(original) as EditLine[]);
              setProblem(null);
            }}
          >
            Undo changes
          </button>
        </div>
      )}
    </div>
  );
}

/** Making, sending and getting paid for the load's invoice. */
export function InvoicePanel({ load, sendingEnabled, onChanged, onOpenSettings }: InvoicePanelProps) {
  const invoice = load.invoice;
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<{ text: string; settings?: boolean } | null>(null);
  const [emailing, setEmailing] = useState<{ to: string; message: string } | null>(null);
  const [paying, setPaying] = useState<string | null>(null);
  const [confirmVoid, setConfirmVoid] = useState(false);
  const [linesDirty, setLinesDirty] = useState(false);

  async function act(key: string, action: () => Promise<Invoice | void>, fallback: string): Promise<boolean> {
    setBusy(key);
    setError(null);

    try {
      const result = await action();

      if (result) {
        await onChanged();
      }

      return true;
    } catch (err) {
      const text = errorMessage(err, fallback);
      setError({ text, settings: text.includes("in Settings") });
      return false;
    } finally {
      setBusy(null);
    }
  }

  const delivered = ["DELIVERED", "INVOICED", "PAID", "CANCELLED"].includes(load.status);


  const errorBox = error && (
    <div className="action-error tk-error" role="alert">
      <span>{error.text}</span>
      {error.settings && (
        <button type="button" className="rq-button rq-button-small" onClick={onOpenSettings}>
          Open Settings
        </button>
      )}
    </div>
  );

  if (!invoice) {
    return (
      <section className="tk-panel" aria-label="Invoice">
        <h2>Invoice</h2>
        <p className="tk-sub">
          {load.status === "CANCELLED"
            ? "Bill the approved charges on this cancelled load, like a TONU."
            : delivered
              ? "Make the invoice from the rate and approved extra pay. You can change the lines before it goes out."
              : "Once the load is delivered, make the invoice here."}
        </p>
        {errorBox}
        <div className="tk-actions">
          <button
            type="button"
            className={`rq-button rq-button-small ${delivered ? "rq-button-primary" : ""}`}
            disabled={busy !== null}
            onClick={() => void act("create", () => createInvoice(load.id), "Couldn't make the invoice.")}
          >
            {busy === "create" ? "Making it..." : "Create invoice"}
          </button>
        </div>
      </section>
    );
  }

  const draft = invoice.status === "DRAFT";
  // Unsaved line edits hold back anything that uses the saved invoice
  const held = busy !== null || (draft && linesDirty);
  const due = invoice.totalCents - (invoice.quickPayFeeCents ?? 0);
  const overdue = isOverdue(invoice);
  const missing = invoice.status === "PAID" ? [] : (invoice.missingPaperwork ?? []);
  const toFactor = invoice.sendTo === "FACTOR";

  return (
    <section className="tk-panel tk-invoice" aria-label="Invoice">
      <h2>
        Invoice {invoice.number}
        <span
          className={`tk-pill tk-pill-${invoice.status === "PAID" ? "good" : overdue ? "fix" : invoice.status === "SENT" ? "info" : "note"}`}
        >
          {invoice.status === "PAID" ? "Paid" : overdue ? "Overdue" : invoice.status === "SENT" ? "Sent" : "Draft"}
        </span>
      </h2>

      {errorBox}

      {draft && (
        <>
          {invoice.factoring && (
            <div className="tk-sendto" role="group" aria-label="Who gets the invoice">
              <span className="tk-eyebrow">Send to</span>
              <div className="tk-chips">
                {(["FACTOR", "BROKER"] as const).map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={`tk-chip ${invoice.sendTo === option ? "active" : ""}`}
                    aria-pressed={invoice.sendTo === option}
                    disabled={held}
                    onClick={() =>
                      invoice.sendTo !== option &&
                      void act("sendto", () => updateInvoice(invoice.id, { sendTo: option }), "Couldn't change it.")
                    }
                  >
                    {option === "FACTOR" ? "Factoring company" : "Broker"}
                  </button>
                ))}
              </div>
            </div>
          )}

          <LinesEditor
            key={JSON.stringify(invoice.lines)}
            invoice={invoice}
            busy={busy !== null}
            onSave={(lines) => act("lines", () => updateInvoice(invoice.id, { lines }), "Couldn't save the lines.")}
            onDirtyChange={setLinesDirty}
          />

          <button
            type="button"
            className="tk-link tk-link-small"
            disabled={held}
            onClick={() =>
              void act("rebuild", () => updateInvoice(invoice.id, { rebuild: true }), "Couldn't update the lines.")
            }
          >
            Start over from the rate and approved extra pay
          </button>

          {!toFactor && load.quickPayPercent !== null && (
            <label className="tk-check">
              <input
                type="checkbox"
                checked={invoice.quickPay}
                disabled={held}
                onChange={(event) =>
                  void act("quickpay", () => updateInvoice(invoice.id, { quickPay: event.target.checked }), "Couldn't change it.")
                }
              />
              Ask for quick pay ({load.quickPayPercent}% fee
              {invoice.quickPay && invoice.quickPayFeeCents !== null
                ? `: ${money(invoice.quickPayFeeCents)}, you get ${money(due)}`
                : ""}
              )
            </label>
          )}
        </>
      )}

      {!draft && (
        <dl className="tk-kv">
          <dt>Total</dt>
          <dd className="tk-money">
            {money(invoice.totalCents)}
            {invoice.quickPayFeeCents !== null && <div className="tk-sub">Quick pay: {money(due)} after the fee</div>}
          </dd>
          <dt>Sent</dt>
          <dd>
            {invoice.sentAt && new Date(invoice.sentAt).toLocaleDateString([], { month: "short", day: "numeric" })}
            {invoice.sentVia === "EMAIL" && invoice.sentEmail ? ` to ${invoice.sentEmail}` : toFactor ? " to the factoring company" : ""}
          </dd>
          {invoice.status === "SENT" && (
            <>
              <dt>{toFactor ? "Payment" : "Due"}</dt>
              <dd className={overdue ? "tk-overdue" : ""}>
                {invoice.dueDate
                  ? `${dueText(invoice.dueDate)}${overdue ? " (overdue)" : ""}`
                  : toFactor
                    ? "Waiting on the factoring company"
                    : "Payment terms weren't on the rate con"}
              </dd>
            </>
          )}
          {invoice.status === "PAID" && (
            <>
              <dt>Paid</dt>
              <dd>
                {money(invoice.paidCents)}
                {invoice.paidAt && ` on ${new Date(invoice.paidAt).toLocaleDateString([], { month: "short", day: "numeric" })}`}
              </dd>
            </>
          )}
        </dl>
      )}

      {/* The packet: what the factor or broker gets */}
      <div className="tk-packet">
        <span className="tk-eyebrow">Packet</span>
        <ol>
          <li>Invoice {invoice.number}</li>
          {(invoice.packet ?? []).map((document, index) => (
            <li key={index}>
              {kindLabel(document.kind)}
              <span className="tk-sub"> · {document.name || "Unnamed file"}</span>
            </li>
          ))}
        </ol>
        {missing.length > 0 && (
          <p className="tk-callout">
            No {missing.join(", ")} on this load yet. Add {missing.length === 1 ? "it" : "them"} under Documents
            {draft ? " before you send the invoice." : "."}
          </p>
        )}
        <div className="tk-actions">
          <button
            type="button"
            className="rq-button rq-button-small"
            disabled={held}
            onClick={() => openPdf(`/api/invoices/${invoice.id}/pdf`, `Invoice ${invoice.number}.pdf`, "Invoice")}
          >
            View invoice
          </button>
          <button
            type="button"
            className="rq-button rq-button-small"
            disabled={held}
            onClick={() =>
              void act(
                "packet",
                () => downloadFile(`/api/invoices/${invoice.id}/packet`, `Invoice ${invoice.number} packet.pdf`),
                "Couldn't make the packet."
              )
            }
          >
            {busy === "packet" ? "Making it..." : "Download packet"}
          </button>
        </div>
      </div>

      {/* Sending */}
      {invoice.status !== "PAID" && (
        <div className="tk-invoice-send">
          {emailing ? (
            <div className="tk-composer">
              <strong className="tk-composer-title">
                Email the packet to {toFactor ? "the factoring company" : "the broker"}
              </strong>
              <label htmlFor="invoice-to">To</label>
              <input
                id="invoice-to"
                type="email"
                maxLength={320}
                value={emailing.to}
                onChange={(event) => setEmailing({ ...emailing, to: event.target.value })}
                disabled={busy !== null}
              />
              <label htmlFor="invoice-message">Message</label>
              <AutoTextarea
                id="invoice-message"
                value={emailing.message}
                onChange={(event) => setEmailing({ ...emailing, message: event.target.value })}
                disabled={busy !== null}
              />
              <p className="tk-hint">The packet is attached as one PDF.</p>
              <div className="tk-actions">
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-primary"
                  disabled={busy !== null || !emailing.to.trim() || !emailing.message.trim()}
                  onClick={() =>
                    void act("email", () => sendInvoice(invoice.id, emailing.to.trim(), emailing.message), "Couldn't send the invoice.").then(
                      (sent) => sent && setEmailing(null)
                    )
                  }
                >
                  {busy === "email" ? "Sending..." : "Send invoice"}
                </button>
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-quiet"
                  disabled={busy !== null}
                  onClick={() => setEmailing(null)}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : draft ? (
            <>
              <p className="tk-sub">
                {toFactor
                  ? "Upload the packet on your factoring company's website, then mark it sent. Or email it, if your factor takes email."
                  : "Email the packet to the broker, or send it your own way and mark it sent."}
              </p>
              {linesDirty && <p className="tk-callout">Save or undo your line changes first.</p>}
              <div className="tk-actions">
                {toFactor && (
                  <button
                    type="button"
                    className="rq-button rq-button-small rq-button-primary"
                    disabled={held}
                    onClick={() => void act("sent", () => markInvoiceSent(invoice.id), "Couldn't update the invoice.")}
                  >
                    I uploaded it
                  </button>
                )}
                {sendingEnabled && (
                  <button
                    type="button"
                    className={`rq-button rq-button-small ${toFactor ? "" : "rq-button-primary"}`}
                    disabled={held}
                    onClick={() => setEmailing({ to: invoice.suggestedEmail ?? "", message: defaultMessage(load, invoice) })}
                  >
                    {toFactor ? "Email it instead" : "Email to broker"}
                  </button>
                )}
                {!toFactor && (
                  <button
                    type="button"
                    className="rq-button rq-button-small rq-button-quiet"
                    disabled={held}
                    onClick={() => void act("sent", () => markInvoiceSent(invoice.id), "Couldn't update the invoice.")}
                  >
                    I sent it myself
                  </button>
                )}
              </div>
            </>
          ) : paying !== null ? (
            <div className="tk-pay-form">
              <label htmlFor="paid-amount">Amount received</label>
              <input
                id="paid-amount"
                inputMode="decimal"
                value={paying}
                onChange={(event) => setPaying(event.target.value)}
                disabled={busy !== null}
              />
              <div className="tk-actions">
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-primary"
                  disabled={busy !== null}
                  onClick={() => {
                    const cents = parseCents(paying);

                    if (cents === null) {
                      setError({ text: "Enter the amount you received, like 2150.00." });
                      return;
                    }

                    void act("paid", () => markInvoicePaid(invoice.id, cents), "Couldn't mark it paid.").then(
                      (done) => done && setPaying(null)
                    );
                  }}
                >
                  Mark paid
                </button>
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-quiet"
                  disabled={busy !== null}
                  onClick={() => setPaying(null)}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <div className="tk-actions">
              <button
                type="button"
                className="rq-button rq-button-small rq-button-primary"
                disabled={busy !== null}
                onClick={() => setPaying(centsInput(due))}
              >
                Mark paid
              </button>
              {sendingEnabled && (
                <button
                  type="button"
                  className="rq-button rq-button-small"
                  disabled={busy !== null}
                  onClick={() =>
                    setEmailing({ to: invoice.sentEmail ?? invoice.suggestedEmail ?? "", message: defaultMessage(load, invoice) })
                  }
                >
                  Email it again
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {invoice.status === "PAID" && (
        <div className="tk-actions">
          <button
            type="button"
            className="rq-button rq-button-small rq-button-quiet"
            disabled={busy !== null}
            onClick={() => void act("unpaid", () => markInvoiceUnpaid(invoice.id), "Couldn't update the invoice.")}
          >
            Mark unpaid
          </button>
        </div>
      )}

      {invoice.status !== "PAID" &&
        (confirmVoid ? (
          <ConfirmInline
            message={`Void invoice ${invoice.number}? Its number isn't reused, and you can make a new invoice.`}
            confirmLabel="Void it"
            keepLabel="Keep it"
            busy={busy !== null}
            onConfirm={() => {
              setConfirmVoid(false);
              void act("void", () => voidInvoice(invoice.id), "Couldn't void the invoice.");
            }}
            onKeep={() => setConfirmVoid(false)}
          />
        ) : (
          <button type="button" className="tk-link tk-link-small tk-void" onClick={() => setConfirmVoid(true)}>
            Void invoice
          </button>
        ))}
    </section>
  );
}

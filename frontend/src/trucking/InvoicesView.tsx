import { useState } from "react";
import type { OpenLoadOptions } from "./loadTabs";
import { errorMessage } from "../api/client";
import { createInvoice } from "../api/loads";
import type { Invoice, LoadSummary } from "../types/Load";
import { downloadFile, dueText, isOverdue } from "./billing";
import { lane, money } from "./format";

interface InvoicesViewProps {
  loads: LoadSummary[];
  invoices: Invoice[];
  loading: boolean;
  error: string | null;
  onOpenLoad: (id: string, options?: OpenLoadOptions) => void;
  onRetry?: () => void;
  /** Refreshes loads and invoices after one is made here. */
  onChanged?: () => void;
}

type Filter = "todo" | "waiting" | "overdue" | "paid";

function thisMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

/** Every invoice: what to send, what's owed, what's late, what's paid. */
export function InvoicesView({ loads, invoices, loading, error, onOpenLoad, onRetry, onChanged }: InvoicesViewProps) {
  const [filter, setFilter] = useState<Filter>("todo");
  const [month, setMonth] = useState(thisMonth);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);
  const [creating, setCreating] = useState<string | null>(null);
  const [createError, setCreateError] = useState<{ loadId: string; text: string } | null>(null);
  const [packing, setPacking] = useState<string | null>(null);
  const [packetError, setPacketError] = useState<{ invoiceId: string; text: string } | null>(null);

  const invoiced = new Set(invoices.map((invoice) => invoice.loadId));
  const toInvoice = loads.filter((load) => load.status === "DELIVERED" && !invoiced.has(load.id));
  const drafts = invoices.filter((invoice) => invoice.status === "DRAFT");
  const waiting = invoices.filter((invoice) => invoice.status === "SENT");
  const overdue = waiting.filter(isOverdue);
  const paid = invoices.filter((invoice) => invoice.status === "PAID");
  const owed = waiting.reduce((sum, invoice) => sum + invoice.totalCents - (invoice.quickPayFeeCents ?? 0), 0);

  const chips: { key: Filter; label: string; count: number }[] = [
    { key: "todo", label: "To send", count: toInvoice.length + drafts.length },
    { key: "waiting", label: "Waiting on payment", count: waiting.length },
    { key: "overdue", label: "Overdue", count: overdue.length },
    { key: "paid", label: "Paid", count: paid.length },
  ];

  // Makes the invoice from the rate and approved extra pay, then opens it to check and send
  async function create(loadId: string) {
    setCreating(loadId);
    setCreateError(null);

    try {
      await createInvoice(loadId);
      onChanged?.();
      onOpenLoad(loadId);
    } catch (err) {
      setCreateError({ loadId, text: errorMessage(err, "Couldn't make the invoice. Please try again.") });
    } finally {
      setCreating(null);
    }
  }

  // Invoice, rate con and signed paperwork in one PDF, as sent to the broker or factor
  async function downloadPacket(invoice: Invoice) {
    setPacking(invoice.id);
    setPacketError(null);

    try {
      await downloadFile(`/api/invoices/${invoice.id}/packet`, `Invoice ${invoice.number} packet.pdf`);
    } catch (err) {
      setPacketError({ invoiceId: invoice.id, text: errorMessage(err, "Couldn't make the packet. Please try again.") });
    } finally {
      setPacking(null);
    }
  }

  async function exportMonth() {
    setExporting(true);
    setExportError(null);

    try {
      await downloadFile(`/api/invoices/export?month=${encodeURIComponent(month)}`, `Clerova invoices ${month}.csv`);
    } catch (err) {
      setExportError(errorMessage(err, "Couldn't make the spreadsheet. Please try again."));
    } finally {
      setExporting(false);
    }
  }

  const listed = filter === "waiting" ? waiting : filter === "overdue" ? overdue : filter === "paid" ? paid : drafts;

  return (
    <div className="tk-page">
      <header className="page-header">
        <h1>Invoices</h1>
        <p>Send invoices for delivered loads, and see what's still owed.</p>
      </header>

      {error && (
        <div className="action-error tk-error" role="alert">
          <span>{error}</span>
          {onRetry && (
            <button type="button" className="rq-button rq-button-small" onClick={onRetry}>
              Try again
            </button>
          )}
        </div>
      )}

      <div className="tk-summary">
        <div>
          <span className="tk-eyebrow">Owed to you</span>
          <strong className="tk-money">{money(owed) || "$0"}</strong>
        </div>
        <div>
          <span className="tk-eyebrow">Overdue</span>
          <strong className={`tk-money ${overdue.length > 0 ? "tk-overdue" : ""}`}>
            {money(overdue.reduce((sum, invoice) => sum + invoice.totalCents, 0)) || "$0"}
          </strong>
        </div>
      </div>

      <div className="tk-chips" role="group" aria-label="Show">
        {chips.map((chip) => (
          <button
            key={chip.key}
            type="button"
            className={`tk-chip ${filter === chip.key ? "active" : ""}`}
            aria-pressed={filter === chip.key}
            onClick={() => setFilter(chip.key)}
          >
            {chip.label} <span>{chip.count}</span>
          </button>
        ))}
      </div>

      {loading && invoices.length === 0 && loads.length === 0 ? (
        <p className="tk-none">Loading...</p>
      ) : (
        <section className="tk-panel tk-invoice-list">
          {filter === "todo" &&
            toInvoice.map((load) => (
              <div key={load.id} className="tk-row tk-row-split">
                <button type="button" className="tk-row-open" onClick={() => onOpenLoad(load.id)}>
                  <span className="tk-row-main">
                    <span className="tk-lane">{lane(load.origin, load.destination)}</span>
                    <span className="tk-sub" style={{ display: "block" }}>
                      {load.loadNumber ? `#${load.loadNumber} · ` : ""}
                      {load.brokerName ?? "Broker"} · {money(load.rateCents)} · delivered, no invoice yet
                    </span>
                  </span>
                </button>
                <button
                  type="button"
                  className="rq-button rq-button-small rq-button-primary"
                  disabled={creating !== null}
                  aria-label={`Create invoice for ${load.loadNumber ? `load ${load.loadNumber}` : lane(load.origin, load.destination)}`}
                  onClick={() => void create(load.id)}
                >
                  {creating === load.id ? "Making it..." : "Create invoice"}
                </button>
                {createError?.loadId === load.id && (
                  <p className="action-error tk-row-error" role="alert">
                    {createError.text}
                  </p>
                )}
              </div>
            ))}

          {listed.map((invoice) => {
            const late = isOverdue(invoice);

            return (
              <div key={invoice.id} className="tk-row tk-row-split">
                <button type="button" className="tk-row-open tk-row-invoice" onClick={() => onOpenLoad(invoice.loadId)}>
                <span className="tk-row-main">
                  <span className="tk-lane">
                    {invoice.number} · {lane(invoice.origin, invoice.destination)}
                  </span>
                  <span className="tk-sub" style={{ display: "block" }}>
                    {invoice.loadNumber ? `#${invoice.loadNumber} · ` : ""}
                    {invoice.brokerName ?? "Broker"} · {money(invoice.totalCents)}
                    {invoice.status === "SENT" &&
                      (invoice.dueDate
                        ? ` · due ${dueText(invoice.dueDate)}`
                        : invoice.sendTo === "FACTOR"
                          ? " · with the factoring company"
                          : "")}
                    {invoice.status === "PAID" && invoice.paidCents !== null && ` · paid ${money(invoice.paidCents)}`}
                  </span>
                </span>
                <span
                  className={`tk-pill tk-pill-${invoice.status === "PAID" ? "good" : late ? "fix" : invoice.status === "SENT" ? "info" : "note"}`}
                >
                  {invoice.status === "PAID" ? "Paid" : late ? "Overdue" : invoice.status === "SENT" ? "Sent" : "Draft"}
                </span>
                </button>
                {/* The paperwork goes with the invoice: open it, or download the packet */}
                <span className="tk-row-actions">
                  <button
                    type="button"
                    className="rq-button rq-button-small"
                    aria-label={`Documents for invoice ${invoice.number}`}
                    onClick={() => onOpenLoad(invoice.loadId, { tab: "documents" })}
                  >
                    Documents
                  </button>
                  <button
                    type="button"
                    className="rq-button rq-button-small"
                    aria-label={`Packet for invoice ${invoice.number}`}
                    disabled={packing !== null}
                    onClick={() => void downloadPacket(invoice)}
                  >
                    {packing === invoice.id ? "Making it..." : "Packet"}
                  </button>
                </span>
                {packetError?.invoiceId === invoice.id && (
                  <p className="action-error tk-row-error" role="alert">
                    {packetError.text}
                  </p>
                )}
              </div>
            );
          })}

          {(filter === "todo" ? toInvoice.length + drafts.length : listed.length) === 0 && (
            <p className="tk-none">
              {filter === "todo"
                ? "Nothing to send. Delivered loads show up here."
                : filter === "waiting"
                  ? "No invoices waiting on payment."
                  : filter === "overdue"
                    ? "Nothing overdue."
                    : "No paid invoices yet."}
            </p>
          )}
        </section>
      )}

      <section className="tk-panel" aria-label="Export">
        <h2>For your bookkeeper</h2>
        <p className="tk-sub">A spreadsheet of the month's sent invoices. Line haul, detention, lumper, TONU, layover and other pay each get a column, with quick pay fees and payments.</p>
        <div className="tk-actions">
          <label className="visually-hidden" htmlFor="export-month">
            Month
          </label>
          <input
            id="export-month"
            className="tk-month"
            type="month"
            value={month}
            max={thisMonth()}
            onChange={(event) => setMonth(event.target.value || thisMonth())}
          />
          <button type="button" className="rq-button rq-button-small" disabled={exporting} onClick={() => void exportMonth()}>
            {exporting ? "Making it..." : "Download spreadsheet"}
          </button>
        </div>
        {exportError && (
          <p className="action-error" role="alert">
            {exportError}
          </p>
        )}
      </section>
    </div>
  );
}

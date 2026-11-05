import { useState } from "react";
import { errorMessage } from "../api/client";
import type { Invoice, LoadSummary } from "../types/Load";
import { downloadFile, dueText, isOverdue } from "./billing";
import { lane, money } from "./format";

interface InvoicesViewProps {
  loads: LoadSummary[];
  invoices: Invoice[];
  loading: boolean;
  error: string | null;
  onOpenLoad: (id: string) => void;
  onRetry?: () => void;
}

type Filter = "todo" | "waiting" | "overdue" | "paid";

function thisMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

/** Every invoice: what to send, what's owed, what's late, what's paid. */
export function InvoicesView({ loads, invoices, loading, error, onOpenLoad, onRetry }: InvoicesViewProps) {
  const [filter, setFilter] = useState<Filter>("todo");
  const [month, setMonth] = useState(thisMonth);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

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
        <section className="tk-panel">
          {filter === "todo" &&
            toInvoice.map((load) => (
              <button key={load.id} type="button" className="tk-row" onClick={() => onOpenLoad(load.id)}>
                <span className="tk-row-main">
                  <span className="tk-lane">{lane(load.origin, load.destination)}</span>
                  <span className="tk-sub" style={{ display: "block" }}>
                    {load.loadNumber ? `#${load.loadNumber} · ` : ""}
                    {load.brokerName ?? "Broker"} · {money(load.rateCents)}
                  </span>
                </span>
                <span className="tk-pill tk-pill-note">Create invoice</span>
              </button>
            ))}

          {listed.map((invoice) => {
            const late = isOverdue(invoice);

            return (
              <button key={invoice.id} type="button" className="tk-row" onClick={() => onOpenLoad(invoice.loadId)}>
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
        <p className="tk-sub">A spreadsheet of the month's invoices: amounts, quick pay fees and payments.</p>
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

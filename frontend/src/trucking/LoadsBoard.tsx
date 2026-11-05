import type { LoadStatus, LoadSummary } from "../types/Load";
import { lane, money } from "./format";
import { UploadDocuments } from "./UploadDocuments";

interface LoadsBoardProps {
  loads: LoadSummary[];
  loading: boolean;
  error: string | null;
  onOpenLoad: (id: string) => void;
  refreshKey: number;
  /** An upload filed a document or made a load. */
  onChanged: () => void;
  onRetry?: () => void;
}

const COLUMNS: { title: string; statuses: LoadStatus[] }[] = [
  { title: "Booked", statuses: ["BOOKED"] },
  { title: "In transit", statuses: ["IN_TRANSIT"] },
  { title: "Delivered", statuses: ["DELIVERED"] },
  { title: "Closed", statuses: ["INVOICED", "PAID", "CANCELLED"] },
];

export function LoadsBoard({ loads, loading, error, onOpenLoad, refreshKey, onChanged, onRetry }: LoadsBoardProps) {
  return (
    <div className="tk-page" style={{ maxWidth: "none" }}>
      <header className="page-header">
        <h1>Loads</h1>
        <p>Every load from rate con to delivery. Open one to see its rate con check, emails and replies.</p>
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

      <UploadDocuments loads={loads} refreshKey={refreshKey} onOpenLoad={onOpenLoad} onChanged={onChanged} />
      {loading && loads.length === 0 && <p className="tk-none">Loading your loads...</p>}

      {!(error && loads.length === 0) && (
      <div className="tk-board-wrap">
        <div className="tk-board">
          {COLUMNS.map((column) => {
            const items = loads.filter((load) => column.statuses.includes(load.status));

            return (
              <section key={column.title} className="tk-column" aria-label={column.title}>
                <h2>
                  {column.title} <span>{items.length}</span>
                </h2>
                {items.length === 0 && <p className="tk-none" style={{ textAlign: "center" }}>None</p>}
                {items.map((load) => (
                  <button key={load.id} type="button" className="tk-card" onClick={() => onOpenLoad(load.id)}>
                    <span className="tk-card-top">
                      <span>{load.loadNumber ? `#${load.loadNumber}` : "No load number"}</span>
                      <span>{money(load.rateCents)}</span>
                    </span>
                    <span className="tk-lane">{lane(load.origin, load.destination)}</span>
                    <span className="tk-sub">
                      {load.brokerName ?? "Broker unknown"}
                      {load.status === "CANCELLED" ? " · Cancelled" : ""}
                    </span>
                    {(load.openFlags > 0 || load.draftReplies > 0) && (
                      <span className="tk-tags">
                        {load.fixFlags > 0 && <span className="tk-pill tk-pill-fix">{load.fixFlags} to fix</span>}
                        {load.openFlags - load.fixFlags > 0 && (
                          <span className="tk-pill tk-pill-note">{load.openFlags - load.fixFlags} to check</span>
                        )}
                        {load.draftReplies > 0 && <span className="tk-pill tk-pill-info">Draft ready</span>}
                      </span>
                    )}
                  </button>
                ))}
              </section>
            );
          })}
        </div>
      </div>
      )}
    </div>
  );
}

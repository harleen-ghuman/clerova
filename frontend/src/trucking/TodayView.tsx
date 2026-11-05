import type { Invoice, LoadSummary } from "../types/Load";
import { dueText, isOverdue } from "./billing";
import { lane, money } from "./format";

interface TodayViewProps {
  loads: LoadSummary[];
  invoices: Invoice[];
  loading: boolean;
  error: string | null;
  forwardingAddress: string | null;
  onOpenLoad: (id: string) => void;
  onOpenEmailSetup: () => void;
  onRetry?: () => void;
}

function Row({ load, sub, pill, onOpen }: {
  load: LoadSummary;
  sub: string;
  pill?: { text: string; tone: string };
  onOpen: (id: string) => void;
}) {
  return (
    <button type="button" className="tk-row" onClick={() => onOpen(load.id)}>
      <span className="tk-row-main">
        <span className="tk-lane">{lane(load.origin, load.destination)}</span>
        <span className="tk-sub" style={{ display: "block" }}>
          {load.loadNumber ? `#${load.loadNumber} · ` : ""}
          {sub}
        </span>
      </span>
      {pill && <span className={`tk-pill tk-pill-${pill.tone}`}>{pill.text}</span>}
    </button>
  );
}

function Panel({ title, empty, children, count }: {
  title: string;
  empty: string;
  count: number;
  children: React.ReactNode;
}) {
  return (
    <section className="tk-panel">
      <h2>
        {title} <span>{count}</span>
      </h2>
      {count === 0 ? <p className="tk-none">{empty}</p> : children}
    </section>
  );
}

export function TodayView({
  loads,
  invoices,
  loading,
  error,
  forwardingAddress,
  onOpenLoad,
  onOpenEmailSetup,
  onRetry,
}: TodayViewProps) {
  const check = loads.filter((load) => load.status === "BOOKED" && load.openFlags > 0);
  const drafts = loads.filter((load) => load.draftReplies > 0);
  const onTheRoad = loads.filter((load) => load.status === "IN_TRANSIT");
  const invoiced = new Set(invoices.map((invoice) => invoice.loadId));
  const delivered = loads.filter(
    (load) =>
      load.status === "DELIVERED" &&
      (!invoiced.has(load.id) || invoices.some((invoice) => invoice.loadId === load.id && invoice.status === "DRAFT"))
  );
  const overdue = invoices.filter(isOverdue);
  const byId = new Map(loads.map((load) => [load.id, load]));

  return (
    <div className="tk-page">
      <header className="page-header">
        <h1>Today</h1>
        <p>
          What needs you now. Clerova reads your rate cons and broker emails and drafts the replies.
          Nothing is sent until you approve it.
        </p>
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

      {!loading && !error && loads.length === 0 && (
        <div className="tk-start">
          <strong>Forward your first rate con</strong>
          {forwardingAddress ? (
            <p>
              Forward a broker's rate con email, with its PDF, to <code>{forwardingAddress}</code>. It shows up
              here as a load, with anything to check before you sign.
            </p>
          ) : (
            <p>Set up forwarding first, then forward a rate con.</p>
          )}
          <div className="tk-actions" style={{ marginTop: 10 }}>
            <button type="button" className="rq-button rq-button-small" onClick={onOpenEmailSetup}>
              Email setup
            </button>
          </div>
        </div>
      )}

      {/* Without loads to show, empty panels would wrongly say there's nothing to do */}
      {error && loads.length === 0 ? null : loading && loads.length === 0 ? (
        <p className="tk-none">Loading your loads...</p>
      ) : (
        <div className="tk-sections">
          <Panel title="Check before signing" count={check.length} empty="No rate cons waiting.">
            {check.map((load) => (
              <Row
                key={load.id}
                load={load}
                onOpen={onOpenLoad}
                sub={`${load.brokerName ?? "Broker"} · ${money(load.rateCents)}`}
                pill={
                  load.fixFlags > 0
                    ? { text: `${load.fixFlags} to fix`, tone: "fix" }
                    : { text: `${load.openFlags} to check`, tone: "note" }
                }
              />
            ))}
          </Panel>

          <Panel title="Replies to approve" count={drafts.length} empty="No drafts waiting.">
            {drafts.map((load) => (
              <Row
                key={load.id}
                load={load}
                onOpen={onOpenLoad}
                sub={`${load.brokerName ?? "Broker"} wrote`}
                pill={{ text: "Draft ready", tone: "info" }}
              />
            ))}
          </Panel>

          <Panel title="On the road" count={onTheRoad.length} empty="No loads in transit.">
            {onTheRoad.map((load) => (
              <Row
                key={load.id}
                load={load}
                onOpen={onOpenLoad}
                sub={load.statusNote ?? `Delivery ${load.deliveryAppointment ?? "time not set"}`}
              />
            ))}
          </Panel>

          <Panel title="Ready to invoice" count={delivered.length} empty="No delivered loads waiting.">
            {delivered.map((load) => (
              <Row
                key={load.id}
                load={load}
                onOpen={onOpenLoad}
                sub={`${load.brokerName ?? "Broker"} · ${money(load.rateCents)}`}
                pill={
                  invoiced.has(load.id)
                    ? { text: "Draft invoice", tone: "info" }
                    : { text: "Create invoice", tone: "good" }
                }
              />
            ))}
          </Panel>

          {overdue.length > 0 && (
            <Panel title="Overdue invoices" count={overdue.length} empty="">
              {overdue.map((invoice) => {
                const load = byId.get(invoice.loadId);

                return load ? (
                  <Row
                    key={invoice.id}
                    load={load}
                    onOpen={onOpenLoad}
                    sub={`${invoice.number} · ${money(invoice.totalCents)} · due ${dueText(invoice.dueDate!)}`}
                    pill={{ text: "Overdue", tone: "fix" }}
                  />
                ) : null;
              })}
            </Panel>
          )}
        </div>
      )}
    </div>
  );
}

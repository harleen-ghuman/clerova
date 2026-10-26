import { useEffect, useState } from "react";

import { errorMessage } from "../api/client";
import { getInbox, reprocessEmail } from "../api/email";
import type { InboundMessage, InboundMessageStatus } from "../types/Email";

interface InboxPageProps {
  /** Changes when the app refreshes, so new emails show up. */
  refreshKey: number;
  onOpenWorkItem: (workItemId: string) => void;
  onOpenEmailSetup: () => void;
}

const STATUS_LABELS: Record<InboundMessageStatus, string> = {
  RECEIVED: "Queued",
  PROCESSING: "Processing",
  PROCESSED: "New request",
  ATTACHED: "Added to request",
  IGNORED: "Ignored",
  FAILED: "Failed",
};

const STATUS_CLASSES: Record<InboundMessageStatus, string> = {
  RECEIVED: "status-new",
  PROCESSING: "status-new",
  PROCESSED: "status-approved",
  ATTACHED: "status-approved",
  IGNORED: "status-cancelled",
  FAILED: "status-rejected",
};

export function InboxPage({
  refreshKey,
  onOpenWorkItem,
  onOpenEmailSetup,
}: InboxPageProps) {
  const [messages, setMessages] = useState<InboundMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "attention">("all");
  const [busyId, setBusyId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    getInbox()
      .then((data) => {
        if (!cancelled) {
          setMessages(data);
          setError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(errorMessage(err, "Unable to load the inbox."));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  async function handleReprocess(message: InboundMessage) {
    try {
      setBusyId(message.id);
      setError(null);

      const updated = await reprocessEmail(message.id);

      setMessages((current) =>
        current.map((item) => (item.id === updated.id ? updated : item))
      );
    } catch (err) {
      setError(errorMessage(err, "Unable to process this email."));
    } finally {
      setBusyId(null);
    }
  }

  const needsAttention = messages.filter(
    (message) => message.status === "FAILED" || message.status === "IGNORED"
  );

  const visible = filter === "all" ? messages : needsAttention;

  return (
    <section className="inbox">
      <header className="page-header inbox-heading">
        <div>
          <h1>Inbox</h1>
          <p>
            Every email forwarded to Clerova. New requests get a suggested next
            step automatically and appear in Requests.
          </p>
        </div>

        <button type="button" className="rq-button" onClick={onOpenEmailSetup}>
          Email setup
        </button>
      </header>

      <div className="rq-filters">
        <button
          type="button"
          className={`rq-chip ${filter === "all" ? "active" : ""}`}
          aria-pressed={filter === "all"}
          onClick={() => setFilter("all")}
        >
          All
          <span>{messages.length}</span>
        </button>

        <button
          type="button"
          className={`rq-chip ${filter === "attention" ? "active" : ""}`}
          aria-pressed={filter === "attention"}
          onClick={() => setFilter("attention")}
        >
          Ignored or failed
          <span>{needsAttention.length}</span>
        </button>
      </div>

      {loading && <p>Loading inbox...</p>}

      {error && <p className="action-error">{error}</p>}

      {!loading && visible.length === 0 && (
        <div className="empty-view">

          <h2>
            {filter === "all" ? "No emails yet" : "Nothing needs attention"}
          </h2>

          <p>
            {filter === "all"
              ? "Forward your requests inbox to your Clerova address and new emails will appear here."
              : "Ignored and failed emails will appear here."}
          </p>

          {filter === "all" && (
            <button
              type="button"
              className="primary-button"
              onClick={onOpenEmailSetup}
            >
              Set up forwarding
            </button>
          )}
        </div>
      )}

      {visible.length > 0 && (
        <ul className="inbox-list">
          {visible.map((message) => (
            <li key={message.id} className="inbox-item">
              <div className="inbox-item-top">
                <div className="inbox-sender">
                  <strong>
                    {message.fromName || message.fromEmail || "Unknown sender"}
                  </strong>
                  {message.fromName && <span>{message.fromEmail}</span>}
                </div>

                <span className={`status-badge ${STATUS_CLASSES[message.status]}`}>
                  {STATUS_LABELS[message.status]}
                </span>
              </div>

              <p className="inbox-subject">
                {message.subject || "(no subject)"}
              </p>

              {message.preview && (
                <p className="inbox-preview">{message.preview}</p>
              )}

              {message.attachments.length > 0 && (
                <p className="inbox-attachments">
                  Attachments: {message.attachments.join(", ")}
                </p>
              )}

              <div className="inbox-item-footer">
                <span>
                  {new Date(message.receivedAt).toLocaleString([], {
                    month: "short",
                    day: "numeric",
                    hour: "numeric",
                    minute: "2-digit",
                  })}
                  {message.statusReason && ` · ${message.statusReason}`}
                </span>

                {message.workItemId && (
                  <button
                    type="button"
                    className="secondary-action-button"
                    onClick={() => onOpenWorkItem(message.workItemId!)}
                  >
                    Open request
                  </button>
                )}

                {(message.status === "IGNORED" || message.status === "FAILED") && (
                  <button
                    type="button"
                    className="secondary-action-button"
                    onClick={() => handleReprocess(message)}
                    disabled={busyId === message.id}
                  >
                    {busyId === message.id
                      ? "Queuing..."
                      : message.status === "FAILED"
                        ? "Retry"
                        : "Process anyway"}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

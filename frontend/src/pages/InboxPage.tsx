import { useEffect, useState } from "react";

import { errorMessage } from "../api/client";
import { getInbox, reprocessEmail } from "../api/email";
import { FileList } from "../components/FileList";
import type { InboundMessage, InboundMessageStatus } from "../types/Email";
import type { LoadSummary } from "../types/Load";
import { LoadPicker } from "../trucking/LoadPicker";

interface InboxPageProps {
  /** Changes when the app refreshes, so new emails show up. */
  refreshKey: number;
  onOpenWorkItem: (workItemId: string) => void;
  onOpenEmailSetup: () => void;
  /** A trucking workspace: emails become loads. */
  onOpenLoad?: (loadId: string) => void;
  /** A trucking workspace's loads, to file an email under one by hand. */
  loads?: LoadSummary[];
  onFileEmail?: (messageId: string, loadId: string) => Promise<unknown>;
}

const STATUS_LABELS: Record<InboundMessageStatus, string> = {
  RECEIVED: "Queued",
  PROCESSING: "Processing",
  PROCESSED: "New request",
  ATTACHED: "Reply on a request",
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

/** Matches InboundEmailProcessor.OWN_COPY_REASON on the server. */
const OWN_COPY_REASON = "Your copy of an email sent through Clerova";

/** A copy of an email sent through Clerova that came back via forwarding. */
function isOwnCopy(message: InboundMessage): boolean {
  return message.status === "IGNORED" && message.statusReason === OWN_COPY_REASON;
}

/** Matches InboundEmailProcessor.GMAIL_CONFIRMATION_REASON on the server. */
const GMAIL_CONFIRMATION_REASON = "Gmail forwarding confirmation";

/**
 * Emails from Clerova or Gmail's setup, not from residents: copies of your
 * replies and alerts that your forwarding sent back, and Gmail's check.
 */
function isSetAside(message: InboundMessage): boolean {
  return (
    isOwnCopy(message) ||
    (message.status === "IGNORED" && message.statusReason === GMAIL_CONFIRMATION_REASON)
  );
}

export function InboxPage({
  refreshKey,
  onOpenWorkItem,
  onOpenLoad,
  onOpenEmailSetup,
  loads = [],
  onFileEmail,
}: InboxPageProps) {
  const [messages, setMessages] = useState<InboundMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [itemError, setItemError] = useState<{ id: string; message: string } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"all" | "attention">("all");
  const [showSetAside, setShowSetAside] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [filingId, setFilingId] = useState<string | null>(null);
  const [initialRefreshKey] = useState(refreshKey);

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
        // A failed background refresh keeps what's already shown
        if (!cancelled && refreshKey === initialRefreshKey) {
          setError(errorMessage(err, "Couldn't load the inbox. Refresh to try again."));
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
  }, [refreshKey, initialRefreshKey]);

  async function handleReprocess(message: InboundMessage) {
    try {
      setBusyId(message.id);
      setItemError(null);

      const updated = await reprocessEmail(message.id);

      setMessages((current) =>
        current.map((item) => (item.id === updated.id ? updated : item))
      );
    } catch (err) {
      // Shown on the email itself, not at the top of a long list
      setItemError({
        id: message.id,
        message: errorMessage(err, "Couldn't process this email. Please try again."),
      });
    } finally {
      setBusyId(null);
    }
  }

  async function handleFile(message: InboundMessage, loadId: string) {
    if (!onFileEmail) {
      return;
    }

    try {
      setBusyId(message.id);
      setItemError(null);
      await onFileEmail(message.id, loadId);
      setMessages((current) =>
        current.map((item) =>
          item.id === message.id
            ? { ...item, status: "ATTACHED", loadId, statusReason: "Filed under this load by you." }
            : item
        )
      );
      setFilingId(null);
    } catch (err) {
      setItemError({
        id: message.id,
        message: errorMessage(err, "Couldn't file this email. Please try again."),
      });
    } finally {
      setBusyId(null);
    }
  }

  const fromPeople = messages.filter((message) => !isSetAside(message));
  const setAside = messages.length - fromPeople.length;
  const needsAttention = fromPeople.filter(
    (message) => message.status === "FAILED" || message.status === "IGNORED"
  );

  const visible =
    filter === "attention"
      ? needsAttention
      : showSetAside
        ? messages
        : fromPeople;

  return (
    <section className="inbox">
      <header className="page-header inbox-heading">
        <div>
          <h1>Inbox</h1>
          <p>
            {onOpenLoad
              ? "Every email forwarded to Clerova. A rate con becomes a load; broker emails and paperwork go on the load they're about."
              : "Every email forwarded to Clerova. A new email becomes a request in Requests; a reply is added to the request it belongs to."}
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
          <span>{fromPeople.length}</span>
        </button>

        <button
          type="button"
          className={`rq-chip ${filter === "attention" ? "active" : ""}`}
          aria-pressed={filter === "attention"}
          onClick={() => setFilter("attention")}
        >
          {onOpenLoad ? "Not on a load" : "Ignored or failed"}
          <span>{needsAttention.length}</span>
        </button>
      </div>

      {loading && <p>Loading inbox...</p>}

      {error && <p className="action-error">{error}</p>}

      {!loading && !error && visible.length === 0 && (
        <div className="empty-view">

          <h2>
            {filter === "all" ? "No emails yet" : "Nothing needs attention"}
          </h2>

          <p>
            {filter === "all"
              ? onOpenLoad
                ? "Forward your broker emails and rate cons to your Clerova address and they will appear here."
                : "Forward your requests inbox to your Clerova address and new emails will appear here."
              : onOpenLoad
                ? "Emails Clerova couldn't put on a load will appear here."
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
                  {isOwnCopy(message)
                    ? "From Clerova"
                    : isSetAside(message)
                      ? "Gmail setup"
                      : onOpenLoad && message.status === "PROCESSED"
                        ? "New load"
                        : onOpenLoad && message.status === "ATTACHED"
                          ? "On a load"
                          : onOpenLoad && message.status === "IGNORED"
                            ? "Not on a load"
                            : STATUS_LABELS[message.status]}
                </span>
              </div>

              <p className="inbox-subject">
                {message.subject || "(no subject)"}
              </p>

              {message.preview && (
                <p className="inbox-preview">{message.preview}</p>
              )}

              {message.files && message.files.length > 0 ? (
                <FileList files={message.files} />
              ) : (
                message.attachments.length > 0 && (
                  <p className="inbox-attachments">
                    Attachments: {message.attachments.join(", ")}
                  </p>
                )
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

                <div className="inbox-item-actions">
                {message.loadId && onOpenLoad && (
                  <button
                    type="button"
                    className="secondary-action-button"
                    onClick={() => onOpenLoad(message.loadId!)}
                  >
                    Open load
                  </button>
                )}

                {onFileEmail &&
                  filingId !== message.id &&
                  (message.status === "IGNORED" || message.status === "ATTACHED") && (
                    <button
                      type="button"
                      className="secondary-action-button"
                      onClick={() => {
                        setFilingId(message.id);
                        setItemError(null);
                      }}
                    >
                      {message.loadId ? "Move to another load" : "File under a load"}
                    </button>
                  )}

                {message.workItemId && (
                  <button
                    type="button"
                    className="secondary-action-button"
                    onClick={() => onOpenWorkItem(message.workItemId!)}
                  >
                    Open request
                  </button>
                )}

                {(message.status === "FAILED" ||
                  (message.status === "IGNORED" &&
                    !isSetAside(message) &&
                    // A trucking email with no file has nothing new to read
                    !(onOpenLoad && message.attachments.length === 0))) && (
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
                        : onOpenLoad
                          ? "Read it again"
                          : "Process anyway"}
                  </button>
                )}
                </div>
              </div>

              {filingId === message.id && (
                <LoadPicker
                  loads={loads.filter((load) => load.id !== message.loadId)}
                  label="Which load is this email about?"
                  confirmLabel="File it"
                  busy={busyId === message.id}
                  onPick={(loadId) => void handleFile(message, loadId)}
                  onCancel={() => setFilingId(null)}
                />
              )}

              {itemError?.id === message.id && (
                <p className="action-error" role="alert">
                  {itemError.message}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}

      {!loading && filter === "all" && setAside > 0 && (
        <p className="inbox-set-aside">
          {showSetAside
            ? "Showing emails from Clerova and Gmail's setup too. They never become requests."
            : `${setAside === 1 ? "1 email" : `${setAside} emails`} from Clerova or Gmail's setup ${setAside === 1 ? "is" : "are"} hidden, like copies of your replies and alerts that your forwarding sent back.`}{" "}
          <button type="button" className="jobs-link" onClick={() => setShowSetAside((value) => !value)}>
            {showSetAside ? "Hide them" : "Show them"}
          </button>
        </p>
      )}
    </section>
  );
}

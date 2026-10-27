import { useEffect, useState } from "react";

import { errorMessage } from "../api/client";
import {
  draftReply,
  getConversation,
  getWorkItemEvents,
  markReplyHandled,
  sendMessage,
  sendReply,
} from "../api/email";
import {
  approveProposedAction,
  closeWorkItem,
  generateProposedAction,
  getProposedActions,
  rejectProposedAction,
} from "../api/workItems";
import type { ConversationEntry, WorkItemEvent } from "../types/Email";
import type { ProposedAction } from "../types/ProposedAction";
import type { WorkItem } from "../types/WorkItem";
import { decisionCopy, hasResponseToSend } from "../utils/actionCopy";
import {
  capitalize,
  hasNewReply,
  hasUrgentReply,
  longTime,
  requestBucket,
  requestSender,
  requestStatusLabel,
  requestTitle,
  suggestionTitle,
} from "../utils/requestStatus";
import { ApprovedResponse } from "./ApprovedResponse";

interface RequestDetailProps {
  workItem: WorkItem;
  emailSendingEnabled: boolean;
  /** Opened to send a reminder: the reply box starts with a draft. */
  startReminder?: boolean;
  /** Something changed on the server; reload the request lists. */
  onChanged: () => void;
  onOpenJobs: (workItemId: string) => void;
  onBack: () => void;
}

interface ThreadMessage {
  id: string;
  who: string;
  when: string;
  text: string;
  mine: boolean;
}

function latest(actions: ProposedAction[]): ProposedAction | null {
  return (
    [...actions].sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )[0] ?? null
  );
}

export function RequestDetail({
  workItem,
  emailSendingEnabled,
  startReminder = false,
  onChanged,
  onOpenJobs,
  onBack,
}: RequestDetailProps) {
  const [action, setAction] = useState<ProposedAction | null>(null);
  const [actionLoaded, setActionLoaded] = useState(false);
  const [conversation, setConversation] = useState<ConversationEntry[]>([]);
  const [events, setEvents] = useState<WorkItemEvent[]>([]);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Bumped after our own changes so the details reload
  const [reloadKey, setReloadKey] = useState(0);

  const isEmail = workItem.source === "EMAIL";
  const replyTo =
    emailSendingEnabled && isEmail ? workItem.senderEmail : null;

  useEffect(() => {
    let cancelled = false;

    getProposedActions(workItem.id)
      .then((actions) => {
        if (!cancelled) {
          setAction(latest(actions));
          setActionLoaded(true);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(errorMessage(err, "Couldn't load Clerova's suggestion. Refresh to try again."));
          setActionLoaded(true);
        }
      });

    if (isEmail) {
      getConversation(workItem.id)
        .then((entries) => {
          if (!cancelled) {
            setConversation(entries);
          }
        })
        .catch((err) => console.error("Failed to load conversation:", err));
    }

    getWorkItemEvents(workItem.id)
      .then((items) => {
        if (!cancelled) {
          setEvents(items);
        }
      })
      .catch((err) => console.error("Failed to load activity:", err));

    return () => {
      cancelled = true;
    };
  }, [workItem.id, workItem.updatedAt, isEmail, reloadKey]);

  async function run(label: string, task: () => Promise<unknown>) {
    try {
      setBusy(label);
      setError(null);
      await task();
    } catch (err) {
      setError(errorMessage(err, "Something went wrong. Please try again."));
    } finally {
      setBusy(null);
      setReloadKey((key) => key + 1);
      onChanged();
    }
  }

  function approve(current: ProposedAction, response: string) {
    const send = Boolean(replyTo) && current.actionType !== "NO_ACTION";

    return run(send ? "Sending..." : "Approving...", async () => {
      const approved = await approveProposedAction(current.id, response);
      setAction(approved);

      if (send) {
        setAction(await sendReply(approved.id));
      }
    });
  }

  function suggestAgain(current: ProposedAction) {
    return run("Getting a new suggestion...", async () => {
      setAction(await rejectProposedAction(current.id));
      setAction(await generateProposedAction(workItem.id));
    });
  }

  function getSuggestion() {
    return run("Getting a suggestion...", async () => {
      setAction(await generateProposedAction(workItem.id));
    });
  }

  function close(status: "COMPLETED" | "CANCELLED") {
    return run("Updating...", () => closeWorkItem(workItem.id, status));
  }

  /** For spam or anything that needs no reply. */
  function closeWithoutReplying(current: ProposedAction) {
    return run("Closing...", async () => {
      setAction(await rejectProposedAction(current.id));
      await closeWorkItem(workItem.id, "CANCELLED");
    });
  }

  const thread: ThreadMessage[] =
    conversation.length > 0
      ? conversation.map((entry) => ({
          id: entry.id,
          who:
            entry.direction === "INBOUND"
              ? entry.from ?? "Sender"
              : "You (via Clerova)",
          when: longTime(entry.at),
          text: entry.text ?? "",
          mine: entry.direction === "OUTBOUND",
        }))
      : [
          {
            id: "original",
            who: requestSender(workItem),
            when: longTime(workItem.createdAt),
            text: workItem.originalMessage,
            mine: false,
          },
        ];

  const bucket = requestBucket(workItem);
  const closed = workItem.status === "COMPLETED" || workItem.status === "CANCELLED";
  const pending = action?.status === "PENDING_APPROVAL" ? action : null;
  const reviewed = action && !pending ? action : null;
  // Waiting on a suggestion decision or an active job, closing isn't allowed
  const canClose =
    workItem.status === "ESCALATED" ||
    workItem.status === "WAITING_FOR_INFORMATION" ||
    (workItem.status === "NEW" && !pending);
  const newReply = hasNewReply(workItem);
  const urgentReply = hasUrgentReply(workItem);
  // Why Clerova marked it urgent, from the latest activity entry
  const urgentReason = urgentReply
    ? [...events]
        .reverse()
        .find((event) => event.type === "REPLY_URGENT")
        ?.detail?.replace(/^Marked urgent:\s*/, "")
    : undefined;
  // While a job or escalation is underway there's no suggestion to approve,
  // so people can write to the sender directly, or remind a sender who
  // hasn't answered
  const situation: ComposerSituation | null =
    workItem.status === "IN_PROGRESS"
      ? "job"
      : workItem.status === "ESCALATED"
        ? "escalated"
        : workItem.status === "WAITING_FOR_INFORMATION"
          ? "waiting"
          : null;
  const canWrite = Boolean(replyTo) && situation !== null;
  const meta = [
    workItem.address,
    capitalize(workItem.category),
    workItem.priority ? `${capitalize(workItem.priority)} priority` : null,
  ].filter(Boolean);

  return (
    <section className="rq-detail" aria-label="Request details">
      <button type="button" className="rq-back" onClick={onBack}>
        ← All requests
      </button>

      <header className="rq-detail-header">
        <div>
          <h2>{requestTitle(workItem)}</h2>
          <p className="rq-detail-from">
            {requestSender(workItem)}
            {isEmail && workItem.senderName && workItem.senderEmail
              ? ` · ${workItem.senderEmail}`
              : ""}
          </p>
          <p className="rq-detail-meta">{meta.join(" · ")}</p>
        </div>

        <span className="rq-pill">
          <span className={`rq-dot rq-dot-${bucket}`} aria-hidden="true" />
          {requestStatusLabel(workItem)}
        </span>
      </header>

      <div className="rq-thread">
        {thread.map((message) => (
          <article
            key={message.id}
            className={`rq-bubble ${message.mine ? "rq-bubble-mine" : ""}`}
          >
            <div className="rq-bubble-meta">
              <strong>{message.who}</strong>
              <span>{message.when}</span>
            </div>
            <p>{message.text}</p>
          </article>
        ))}
      </div>

      {newReply && (
        <section
          className={`rq-new-reply-banner ${urgentReply ? "urgent" : ""}`}
          aria-label={urgentReply ? "Urgent reply" : "New reply"}
          role={urgentReply ? "alert" : undefined}
        >
          <div>
            <strong>
              {urgentReply ? "Urgent reply" : "New reply"} from {requestSender(workItem)}
            </strong>
            <span>
              {urgentReply
                ? `This looks urgent${urgentReason ? `: ${urgentReason}` : "."} Read it and act now.`
                : "They wrote again while the job is in progress. The job hasn't changed."}
              {!replyTo &&
                " Reply from your own email, then mark it as handled."}
            </span>
          </div>
          <button
            type="button"
            className="rq-button"
            onClick={() =>
              run("Updating...", () => markReplyHandled(workItem.id))
            }
            disabled={busy !== null}
          >
            {busy === "Updating..." ? busy : "Mark as handled"}
          </button>
        </section>
      )}

      {canWrite && replyTo && situation && (
        <ReplyComposer
          key={workItem.id}
          workItemId={workItem.id}
          sendTo={replyTo}
          recipient={workItem.senderName || replyTo}
          startOpen={newReply || (startReminder && situation === "waiting")}
          autoDraft={startReminder && situation === "waiting"}
          situation={situation}
          disabled={busy !== null}
          onSent={() => {
            setReloadKey((key) => key + 1);
            onChanged();
          }}
        />
      )}

      {error && <p className="action-error">{error}</p>}

      {pending && (
        <SuggestionCard
          key={pending.id}
          action={pending}
          recipient={workItem.senderName || workItem.senderEmail}
          canEmail={Boolean(replyTo)}
          busy={busy}
          onApprove={(response) => approve(pending, response)}
          onSuggestAgain={() => suggestAgain(pending)}
          onCloseWithoutReplying={() => closeWithoutReplying(pending)}
        />
      )}

      {actionLoaded && !action && bucket === "review" && (
        <section className="rq-card rq-card-empty">
          <p>Clerova hasn't suggested a next step for this request yet.</p>
          <button
            type="button"
            className="rq-button rq-button-primary"
            onClick={getSuggestion}
            disabled={busy !== null}
          >
            {busy ?? "Get a suggestion"}
          </button>
        </section>
      )}

      {reviewed && (
        <section className={`rq-result rq-result-${reviewed.status.toLowerCase()}`}>
          {closed && reviewed.status === "REJECTED" ? (
            <>
              <strong>Closed without replying</strong>
              <span>Nothing was sent. If the sender writes again, it comes back for review.</span>
            </>
          ) : (
            <>
              <strong>{decisionCopy(reviewed, Boolean(replyTo)).title}</strong>
              <span>{decisionCopy(reviewed, Boolean(replyTo)).description}</span>
            </>
          )}

          <div className="rq-result-actions">
            {reviewed.status === "REJECTED" && !closed && (
              <button
                type="button"
                className="rq-button rq-button-primary"
                onClick={getSuggestion}
                disabled={busy !== null}
              >
                {busy ?? "Get a new suggestion"}
              </button>
            )}

            {reviewed.actionType === "CREATE_MAINTENANCE_REQUEST" &&
              reviewed.status !== "REJECTED" && (
                <button
                  type="button"
                  className="rq-button"
                  onClick={() => onOpenJobs(workItem.id)}
                >
                  View job
                </button>
              )}
          </div>
        </section>
      )}

      {/* Sent replies already show in the conversation */}
      {reviewed && reviewed.status !== "EXECUTED" && hasResponseToSend(reviewed) && (
        <ApprovedResponse
          action={reviewed}
          sendTo={replyTo}
          onSent={(updated) => {
            setAction(updated);
            onChanged();
          }}
        />
      )}

      {canClose && (
        <div className="rq-close-actions">
          <button
            type="button"
            className="rq-button rq-button-primary"
            onClick={() => close("COMPLETED")}
            disabled={busy !== null}
          >
            Mark done
          </button>
          <button
            type="button"
            className="rq-button"
            onClick={() => {
              if (
                window.confirm(
                  "Cancel this request? It moves to Done and nothing is sent. If the sender writes again, it comes back."
                )
              ) {
                void close("CANCELLED");
              }
            }}
            disabled={busy !== null}
          >
            Cancel request
          </button>
        </div>
      )}

      {events.length > 0 && (
        <details className="rq-activity">
          <summary>Activity</summary>
          <ol>
            {[...events].reverse().map((event) => (
              <li key={event.id}>
                {event.detail}
                <span>
                  {event.actor ?? "Clerova"} · {longTime(event.createdAt)}
                </span>
              </li>
            ))}
          </ol>
        </details>
      )}
    </section>
  );
}

interface SuggestionCardProps {
  action: ProposedAction;
  recipient: string | null;
  canEmail: boolean;
  busy: string | null;
  onApprove: (response: string) => void;
  onSuggestAgain: () => void;
  onCloseWithoutReplying: () => void;
}

function SuggestionCard({
  action,
  recipient,
  canEmail,
  busy,
  onApprove,
  onSuggestAgain,
  onCloseWithoutReplying,
}: SuggestionCardProps) {
  const [response, setResponse] = useState(action.draftedResponse);
  const hasReply = action.actionType !== "NO_ACTION";
  const sends = canEmail && hasReply;

  return (
    <section className="rq-card" aria-label="Clerova suggestion">
      <div className="rq-card-title">
        <span className="rq-kicker">Clerova suggests</span>
        <strong>{suggestionTitle(action.actionType, canEmail)}</strong>
      </div>

      <p className="rq-reasoning">{action.reasoning}</p>

      {hasReply && (
        <label className="rq-reply">
          {recipient ? `Reply to ${recipient}` : "Reply"}
          <textarea
            value={response}
            rows={6}
            maxLength={10000}
            disabled={busy !== null}
            onChange={(event) => setResponse(event.target.value)}
          />
        </label>
      )}

      <div className="rq-card-actions">
        <button
          type="button"
          className="rq-button rq-button-primary"
          onClick={() => onApprove(response)}
          disabled={busy !== null || (hasReply && response.trim() === "")}
        >
          {busy ?? (sends ? "Approve & send" : "Approve")}
        </button>
        <button
          type="button"
          className="rq-button"
          onClick={onSuggestAgain}
          disabled={busy !== null}
        >
          Suggest something else
        </button>
        <button
          type="button"
          className="rq-button rq-button-quiet"
          onClick={onCloseWithoutReplying}
          disabled={busy !== null}
        >
          Close without replying
        </button>
        {hasReply && !canEmail && (
          <span className="rq-hint">
            This request didn't come by email, so you'll copy the reply after
            approving.
          </span>
        )}
      </div>
    </section>
  );
}

type ComposerSituation = "job" | "escalated" | "waiting";

interface ReplyComposerProps {
  workItemId: string;
  sendTo: string;
  recipient: string;
  startOpen: boolean;
  /** Start with a draft from Clerova (a reminder opened from Today). */
  autoDraft: boolean;
  situation: ComposerSituation;
  disabled: boolean;
  onSent: () => void;
}

/** Write to the sender without changing the request or its job. */
function ReplyComposer({
  workItemId,
  sendTo,
  recipient,
  startOpen,
  autoDraft,
  situation,
  disabled,
  onSent,
}: ReplyComposerProps) {
  const [open, setOpen] = useState(startOpen);
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState<string | null>(autoDraft ? "Drafting..." : null);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const reminder = situation === "waiting";

  useEffect(() => {
    if (!autoDraft) {
      return;
    }

    let cancelled = false;

    draftReply(workItemId)
      .then((result) => {
        if (!cancelled) {
          setMessage((current) => (current.trim() === "" ? result.reply : current));
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(errorMessage(err, "Couldn't draft a reminder. Write your own or try again."));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setBusy(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [autoDraft, workItemId]);

  if (!open) {
    return (
      <div className="rq-compose-open">
        {sentTo && <span className="rq-hint">Sent to {sentTo}.</span>}
        <button
          type="button"
          className="rq-button"
          onClick={() => {
            setOpen(true);
            setSentTo(null);

            if (reminder && message.trim() === "") {
              void draft();
            }
          }}
          disabled={disabled}
        >
          {reminder ? `Send a reminder to ${recipient}` : `Write to ${recipient}`}
        </button>
      </div>
    );
  }

  async function draft() {
    try {
      setBusy("Drafting...");
      setError(null);
      const result = await draftReply(workItemId);
      setMessage(result.reply);
    } catch (err) {
      setError(errorMessage(err, "Couldn't draft a message. Write your own or try again."));
    } finally {
      setBusy(null);
    }
  }

  async function send() {
    try {
      setBusy("Sending...");
      setError(null);
      await sendMessage(workItemId, message.trim());
      setMessage("");
      setSentTo(sendTo);
      setOpen(false);
      onSent();
    } catch (err) {
      setError(errorMessage(err, "Couldn't send the reply. Please try again."));
    } finally {
      setBusy(null);
    }
  }

  const locked = disabled || busy !== null;

  return (
    <section className="rq-card" aria-label={`${reminder ? "Reminder" : "Reply"} to ${recipient}`}>
      <label className="rq-reply">
        {reminder ? "Reminder" : "Reply"} to {recipient}
        <textarea
          value={message}
          rows={6}
          maxLength={10000}
          disabled={locked}
          placeholder={
            busy === "Drafting..."
              ? "Clerova is drafting..."
              : `Write a ${reminder ? "reminder" : "reply"}, or let Clerova draft one.`
          }
          onChange={(event) => setMessage(event.target.value)}
        />
      </label>

      {error && <p className="action-error">{error}</p>}

      <div className="rq-card-actions">
        <button
          type="button"
          className="rq-button rq-button-primary"
          onClick={send}
          disabled={locked || message.trim() === ""}
        >
          {busy === "Sending..." ? busy : "Send"}
        </button>
        <button
          type="button"
          className="rq-button"
          onClick={draft}
          disabled={locked}
        >
          {busy === "Drafting..."
            ? busy
            : message.trim() === ""
              ? "Draft with Clerova"
              : "Replace with a draft"}
        </button>
        <button
          type="button"
          className="rq-button rq-button-quiet"
          onClick={() => {
            setOpen(false);
            setMessage("");
            setError(null);
          }}
          disabled={busy !== null}
        >
          Cancel
        </button>
        <span className="rq-hint">
          Goes to {sendTo}.{" "}
          {situation === "job"
            ? "The job doesn't change."
            : situation === "escalated"
              ? "The request doesn't change."
              : `It stays waiting on ${recipient}.`}
        </span>
      </div>
    </section>
  );
}

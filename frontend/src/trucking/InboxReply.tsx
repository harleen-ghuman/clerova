import { useEffect, useId, useRef, useState } from "react";
import { errorMessage } from "../api/client";
import { draftInboxReply, replyToInboxEmail } from "../api/email";
import { AutoTextarea } from "../components/AutoTextarea";
import type { InboundMessage } from "../types/Email";

interface InboxReplyProps {
  message: InboundMessage;
  sendingEnabled: boolean;
  onSent: (updated: InboundMessage) => void;
  onCancel: () => void;
}

/** A reply to any Inbox email: Clerova drafts it, the dispatcher edits and sends it from Clerova. */
export function InboxReply({ message, sendingEnabled, onSent, onCancel }: InboxReplyProps) {
  const id = useId();
  const [to, setTo] = useState(message.fromEmail ?? "");
  const [body, setBody] = useState("");
  const startWithDraft = (message.replyDraftsLeft ?? 10) > 0;
  const [drafting, setDrafting] = useState(startWithDraft);
  const [sending, setSending] = useState(false);
  const [draftsLeft, setDraftsLeft] = useState(message.replyDraftsLeft ?? 10);
  const [error, setError] = useState<string | null>(null);

  function draft() {
    setDrafting(true);
    setError(null);
    draftInboxReply(message.id)
      .then((result) => {
        setBody(result.draft);
        setDraftsLeft(result.draftsLeft);
      })
      .catch((err) => setError(errorMessage(err, "Couldn't write a draft. Write your own, or try again.")))
      .finally(() => setDrafting(false));
  }

  // Start with a draft when there are drafts left; once, even when React runs effects twice in development
  const asked = useRef(false);

  useEffect(() => {
    if (!startWithDraft || asked.current) {
      return;
    }

    asked.current = true;
    draftInboxReply(message.id)
      .then((result) => {
        setBody((current) => current || result.draft);
        setDraftsLeft(result.draftsLeft);
      })
      .catch((err) => setError(errorMessage(err, "Couldn't write a draft. Write your own, or try again.")))
      .finally(() => setDrafting(false));
  }, [message.id, startWithDraft]);

  async function send() {
    setSending(true);
    setError(null);

    try {
      onSent(await replyToInboxEmail(message.id, { to, body }));
    } catch (err) {
      setError(errorMessage(err, "Couldn't send the reply. Please try again."));
    } finally {
      setSending(false);
    }
  }

  const busy = drafting || sending;

  return (
    <div className="tk-composer inbox-reply" role="group" aria-label={`Reply to ${message.fromName || message.fromEmail || "this email"}`}>
      <strong className="tk-composer-title">Reply</strong>
      <label htmlFor={`${id}-to`}>To</label>
      <input id={`${id}-to`} type="email" value={to} onChange={(event) => setTo(event.target.value)} disabled={busy} />
      <label htmlFor={`${id}-body`}>Message</label>
      <AutoTextarea
        id={`${id}-body`}
        value={body}
        placeholder={drafting ? "Writing a draft..." : "Write your reply"}
        onChange={(event) => setBody(event.target.value)}
        disabled={busy}
      />
      <p className="tk-hint">
        Sent from your Clerova address, in the same thread. Your company name is added at the end.
        {message.loadId ? " It also shows in the load's Emails tab." : ""}
      </p>
      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}
      <div className="tk-actions">
        {sendingEnabled ? (
          <button type="button" className="rq-button rq-button-small rq-button-primary" disabled={busy || !body.trim() || !to.trim()} onClick={() => void send()}>
            {sending ? "Sending..." : "Send reply"}
          </button>
        ) : (
          <span className="tk-sub">Sending isn't set up yet. Copy the reply into your own email.</span>
        )}
        <button type="button" className="rq-button rq-button-small" disabled={busy || draftsLeft <= 0} onClick={draft}>
          {drafting ? "Writing..." : draftsLeft > 0 ? `Draft it again (${draftsLeft} left)` : "No drafts left"}
        </button>
        <button type="button" className="rq-button rq-button-small rq-button-quiet" disabled={sending} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}

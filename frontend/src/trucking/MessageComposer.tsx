import { useId, useState } from "react";
import { AutoTextarea } from "../components/AutoTextarea";

export interface MessageDraft {
  /** What the message is, shown above it, like "Tell the broker the truck arrived". */
  title: string;
  to: string;
  subject: string;
  body: string;
}

interface MessageComposerProps {
  draft: MessageDraft;
  sendingEnabled: boolean;
  busy: boolean;
  sendLabel?: string;
  onSend: (message: { to: string; subject: string; body: string }) => void;
  onCancel: () => void;
}

/** An email to the broker, written by Clerova and edited by the dispatcher before it goes. */
export function MessageComposer({ draft, sendingEnabled, busy, sendLabel = "Send", onSend, onCancel }: MessageComposerProps) {
  const id = useId();
  const [to, setTo] = useState(draft.to);
  const [subject, setSubject] = useState(draft.subject);
  const [body, setBody] = useState(draft.body);
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(body);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="tk-composer">
      <strong className="tk-composer-title">{draft.title}</strong>
      <label htmlFor={`${id}-to`}>To</label>
      <input
        id={`${id}-to`}
        type="email"
        value={to}
        maxLength={320}
        placeholder="broker@example.com"
        onChange={(event) => setTo(event.target.value)}
        disabled={busy}
      />
      <label htmlFor={`${id}-subject`}>Subject</label>
      <input
        id={`${id}-subject`}
        value={subject}
        maxLength={200}
        onChange={(event) => setSubject(event.target.value)}
        disabled={busy}
      />
      <label htmlFor={`${id}-body`}>Message</label>
      <AutoTextarea id={`${id}-body`} value={body} onChange={(event) => setBody(event.target.value)} disabled={busy} />
      <p className="tk-hint">Your company name is added at the end. Replies come back to this load.</p>
      <div className="tk-actions">
        {sendingEnabled ? (
          <button
            type="button"
            className="rq-button rq-button-small rq-button-primary"
            disabled={busy || !to.trim() || !body.trim()}
            onClick={() => onSend({ to: to.trim(), subject: subject.trim(), body })}
          >
            {busy ? "Sending..." : sendLabel}
          </button>
        ) : (
          <button type="button" className="rq-button rq-button-small" onClick={() => void copy()}>
            {copied ? "Copied" : "Copy the message"}
          </button>
        )}
        <button type="button" className="rq-button rq-button-small rq-button-quiet" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}

import { useState } from "react";

import { errorMessage } from "../api/client";
import { sendReply } from "../api/email";
import type { ProposedAction } from "../types/ProposedAction";

interface ApprovedResponseProps {
  action: ProposedAction;
  /** Set when the response can be emailed from Clerova to this address. */
  sendTo?: string | null;
  onSent?: (action: ProposedAction) => void;
}

export function ApprovedResponse({
  action,
  sendTo,
  onSent,
}: ApprovedResponseProps) {
  const [copied, setCopied] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const response = action.finalResponse ?? action.draftedResponse;
  const sent = action.status === "EXECUTED";

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(response);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  async function handleSend() {
    try {
      setSending(true);
      setError(null);

      const updated = await sendReply(action.id);
      onSent?.(updated);
    } catch (err) {
      setError(errorMessage(err, "Unable to send the reply."));
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="details-section">
      <div className="approved-response-heading">
        <h3>{sent ? "Sent reply" : "Approved response"}</h3>

        <div className="approved-response-actions">
          <button
            type="button"
            className="secondary-action-button"
            onClick={handleCopy}
          >
            {copied ? "Copied" : "Copy"}
          </button>

          {sendTo && !sent && (
            <button
              type="button"
              className="approve-button"
              onClick={handleSend}
              disabled={sending}
            >
              {sending ? "Sending..." : `Send to ${sendTo}`}
            </button>
          )}
        </div>
      </div>

      <p className="drafted-response">{response}</p>

      {sent && sendTo && (
        <p className="request-success">Sent to {sendTo}</p>
      )}

      {error && <p className="action-error">{error}</p>}
    </div>
  );
}

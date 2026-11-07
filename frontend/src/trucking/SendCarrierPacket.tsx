import { useId, useState } from "react";
import { errorMessage } from "../api/client";
import { sendCarrierPacket } from "../api/carrierPacket";

/** Emails the carrier packet to a broker, as one PDF. */
export function SendCarrierPacket({ to: initialTo = "", sendingEnabled, onDone }: {
  to?: string;
  sendingEnabled: boolean;
  onDone: () => void;
}) {
  const id = useId();
  const [to, setTo] = useState(initialTo);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function send() {
    setSending(true);
    setError(null);

    try {
      await sendCarrierPacket(to.trim());
      setSent(to.trim());
    } catch (err) {
      setError(errorMessage(err, "Couldn't send the packet. Please try again."));
    } finally {
      setSending(false);
    }
  }

  if (sent) {
    return (
      <div className="tk-packet-send">
        <p className="request-success" role="status">
          Carrier packet sent to {sent}.
        </p>
        <button type="button" className="tk-link tk-link-small" onClick={onDone}>
          Done
        </button>
      </div>
    );
  }

  return (
    <div className="tk-packet-send" role="group" aria-label="Email the carrier packet">
      <label htmlFor={`${id}-to`}>Send the carrier packet to</label>
      <input
        id={`${id}-to`}
        type="email"
        placeholder="setup@broker.com"
        value={to}
        onChange={(event) => setTo(event.target.value)}
        disabled={sending}
      />
      <p className="tk-hint">All your carrier documents go as one PDF, with a cover page listing them.</p>
      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}
      <div className="tk-actions">
        {sendingEnabled ? (
          <button
            type="button"
            className="rq-button rq-button-small rq-button-primary"
            disabled={sending || !to.trim()}
            onClick={() => void send()}
          >
            {sending ? "Sending..." : "Send packet"}
          </button>
        ) : (
          <span className="tk-sub">Sending isn't set up yet. Download the packet and attach it to your own email.</span>
        )}
        <button type="button" className="rq-button rq-button-small rq-button-quiet" disabled={sending} onClick={onDone}>
          Cancel
        </button>
      </div>
    </div>
  );
}

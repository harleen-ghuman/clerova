interface ConfirmInlineProps {
  message: string;
  confirmLabel: string;
  /** The safe choice, focused first so Enter never confirms by accident. */
  keepLabel: string;
  busy?: boolean;
  onConfirm: () => void;
  onKeep: () => void;
}

/**
 * Asks before something that can't be undone, on the page itself rather
 * than in a browser pop-up (which some browsers and tools block silently).
 */
export function ConfirmInline({
  message,
  confirmLabel,
  keepLabel,
  busy = false,
  onConfirm,
  onKeep,
}: ConfirmInlineProps) {
  return (
    <div className="confirm-inline" role="group" aria-label="Confirm">
      <p role="status">{message}</p>
      <div className="confirm-inline-actions">
        <button
          type="button"
          className="rq-button rq-button-small rq-button-danger"
          onClick={onConfirm}
          disabled={busy}
        >
          {confirmLabel}
        </button>
        <button
          type="button"
          className="rq-button rq-button-small"
          onClick={onKeep}
          disabled={busy}
          autoFocus
        >
          {keepLabel}
        </button>
      </div>
    </div>
  );
}

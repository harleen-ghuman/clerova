import { useId, useState } from "react";
import type { LoadSummary } from "../types/Load";
import { loadLabel } from "./format";

/** Picks a load from a list, newest first, then confirms. */
export function LoadPicker({
  loads,
  label,
  confirmLabel,
  busy,
  onPick,
  onCancel,
}: {
  loads: LoadSummary[];
  label: string;
  confirmLabel: string;
  busy: boolean;
  onPick: (loadId: string) => void;
  onCancel?: () => void;
}) {
  const id = useId();
  const choices = loads.filter((load) => load.status !== "CANCELLED");
  const [picked, setPicked] = useState("");

  if (choices.length === 0) {
    return <p className="tk-sub">No loads yet. A load appears when its rate con comes in.</p>;
  }

  return (
    <div className="tk-picker">
      <label htmlFor={id}>{label}</label>
      <div className="tk-picker-row">
        <select id={id} value={picked} onChange={(event) => setPicked(event.target.value)} disabled={busy}>
          <option value="">Choose a load</option>
          {choices.map((load) => (
            <option key={load.id} value={load.id}>
              {loadLabel(load)}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="rq-button rq-button-small rq-button-primary"
          disabled={!picked || busy}
          onClick={() => onPick(picked)}
        >
          {busy ? "Saving..." : confirmLabel}
        </button>
        {onCancel && (
          <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={onCancel} disabled={busy}>
            Cancel
          </button>
        )}
      </div>
    </div>
  );
}

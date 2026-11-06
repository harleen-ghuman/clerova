import { useId, useState } from "react";

import { setTimeZone } from "../api/account";
import { errorMessage } from "../api/client";
import type { Me, Organization } from "../types/Account";

/** The U.S. time zones carriers run in, by the names people use. */
const ZONES: { zone: string; label: string }[] = [
  { zone: "America/New_York", label: "Eastern" },
  { zone: "America/Chicago", label: "Central" },
  { zone: "America/Denver", label: "Mountain" },
  { zone: "America/Phoenix", label: "Arizona" },
  { zone: "America/Los_Angeles", label: "Pacific" },
  { zone: "America/Anchorage", label: "Alaska" },
  { zone: "Pacific/Honolulu", label: "Hawaii" },
];

/** The carrier's time zone: it decides invoice dates, due dates and which month an invoice is in. */
export function TimeZoneSection({ organization, onSaved }: { organization: Organization; onSaved: (me: Me) => void }) {
  const id = useId();
  const current = organization.timeZone ?? "America/Los_Angeles";
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const options = ZONES.some((item) => item.zone === current) ? ZONES : [...ZONES, { zone: current, label: current }];

  function change(zone: string) {
    setSaving(true);
    setSaved(false);
    setError(null);
    setTimeZone(zone)
      .then((me) => {
        onSaved(me);
        setSaved(true);
      })
      .catch((err) => setError(errorMessage(err, "Couldn't change the time zone. Please try again.")))
      .finally(() => setSaving(false));
  }

  return (
    <section className="settings-section" aria-labelledby={`${id}-title`}>
      <h2 id={`${id}-title`}>Time zone</h2>
      <p>Invoice dates, due dates and the monthly spreadsheet use this time zone.</p>
      <div className="auth-form settings-form settings-narrow">
        <label>
          Your time zone
          <select value={current} onChange={(event) => change(event.target.value)} disabled={saving}>
            {options.map((item) => (
              <option key={item.zone} value={item.zone}>
                {item.label}
              </option>
            ))}
          </select>
        </label>
        {saved && <span className="request-success">Saved.</span>}
        {error && (
          <p className="action-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}

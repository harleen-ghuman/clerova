import { useEffect, useId, useState, type FormEvent } from "react";
import { getBrokers, setBrokerMc } from "../api/loads";
import { errorMessage } from "../api/client";
import { Email } from "./Email";
import { matchesSearch } from "./search";
import type { BrokerSummary, FmcsaStatus } from "../types/Load";

const FMCSA: Record<FmcsaStatus, { text: string; tone: string }> = {
  ACTIVE: { text: "Active broker", tone: "good" },
  NOT_AUTHORIZED: { text: "No broker authority", tone: "fix" },
  NOT_FOUND: { text: "Not found", tone: "fix" },
};

/** The MC on file, with a way to add or correct it; Clerova then checks it with FMCSA. */
function McNumber({ broker, onSaved }: { broker: BrokerSummary["broker"]; onSaved: () => void }) {
  const inputId = useId();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState(broker.mcNumber ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);

    try {
      await setBrokerMc(broker.id, value);
      setEditing(false);
      onSaved();
    } catch (err) {
      setError(errorMessage(err, "Couldn't save the MC number."));
    } finally {
      setBusy(false);
    }
  }

  if (!editing) {
    return (
      <span className="tk-mc">
        <span>{broker.mcNumber ?? "Not on the rate con"}</span>
        <button type="button" className="tk-link tk-link-small" onClick={() => setEditing(true)}>
          {broker.mcNumber ? "Change" : "Add MC number"}
        </button>
      </span>
    );
  }

  return (
    <form className="tk-mc-form" onSubmit={(event) => void save(event)}>
      <label htmlFor={inputId} className="visually-hidden">
        MC number for {broker.name}
      </label>
      <input
        id={inputId}
        inputMode="numeric"
        autoComplete="off"
        placeholder="555609"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        disabled={busy}
      />
      <button type="submit" className="rq-button rq-button-small rq-button-primary" disabled={busy || !value.trim()}>
        {busy ? "Checking..." : "Save and check"}
      </button>
      <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={() => setEditing(false)} disabled={busy}>
        Cancel
      </button>
      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}

export function BrokersView({ refreshKey }: { refreshKey: number }) {
  const [reloads, setReloads] = useState(0);
  const [brokers, setBrokers] = useState<BrokerSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const query = search.trim();
  const shown = brokers && query
    ? brokers.filter(({ broker }) => matchesSearch(query, [broker.name, broker.mcNumber, broker.email, broker.phone, broker.fmcsaName]))
    : brokers;

  useEffect(() => {
    let current = true;

    getBrokers()
      .then((result) => {
        if (current) {
          setBrokers(result);
          setError(null);
        }
      })
      .catch((err) => {
        if (current) {
          setError(errorMessage(err, "Couldn't load your brokers."));
        }
      });

    return () => {
      current = false;
    };
  }, [refreshKey, reloads]);

  return (
    <div className="tk-page">
      <header className="page-header">
        <h1>Brokers</h1>
        <p>
          Every broker from your rate cons. Clerova checks each MC number against FMCSA's public data when a rate
          con arrives.
        </p>
      </header>

      {error && <p className="action-error" role="alert">{error}</p>}
      {!brokers && !error && <p className="tk-none">Loading brokers...</p>}
      {brokers && brokers.length === 0 && (
        <p className="tk-none">No brokers yet. They appear here when their first rate con comes in.</p>
      )}

      {brokers && brokers.length > 0 && (
        <div className="tk-search-row">
          <label className="rq-search tk-search">
            <svg aria-hidden="true" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <circle cx="11" cy="11" r="7" />
              <path d="M20 20l-3.5-3.5" />
            </svg>
            <span className="visually-hidden">Search brokers</span>
            <input
              type="search"
              placeholder="Broker name, MC number or email"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          {query && (
            <p className="tk-sub" role="status">
              {shown && shown.length === 0
                ? `No brokers match "${query}".`
                : `${shown?.length} ${shown?.length === 1 ? "broker matches" : "brokers match"}.`}{" "}
              <button type="button" className="tk-link" onClick={() => setSearch("")}>
                Clear
              </button>
            </p>
          )}
        </div>
      )}

      {shown && shown.length > 0 && (
        <div className="tk-table-wrap" role="region" aria-label="Brokers table" tabIndex={0}>
          <table className="tk-table">
            <thead>
              <tr>
                <th scope="col">Broker</th>
                <th scope="col">MC number</th>
                <th scope="col">FMCSA check</th>
                <th scope="col">Loads</th>
                <th scope="col">Contact</th>
              </tr>
            </thead>
            <tbody>
              {shown.map(({ broker, loads, openLoads }) => (
                <tr key={broker.id}>
                  <td data-label="Broker">
                    <strong>{broker.name}</strong>
                  </td>
                  <td data-label="MC number">
                    <div>
                      <McNumber broker={broker} onSaved={() => setReloads((count) => count + 1)} />
                    </div>
                  </td>
                  <td data-label="FMCSA check">
                    <div>
                    {broker.fmcsaStatus ? (
                      <span className={`tk-pill tk-pill-${FMCSA[broker.fmcsaStatus].tone}`}>
                        {FMCSA[broker.fmcsaStatus].text}
                      </span>
                    ) : (
                      <span className="tk-sub">Not checked</span>
                    )}
                    {broker.fmcsaName && <div className="tk-sub">FMCSA name: {broker.fmcsaName}</div>}
                    </div>
                  </td>
                  <td data-label="Loads">
                    <div>
                      {loads} {openLoads > 0 && <span className="tk-sub">({openLoads} open)</span>}
                    </div>
                  </td>
                  <td data-label="Contact">
                    <div>
                      <div>{broker.email ? <Email value={broker.email} /> : broker.phone ? "" : "Not on the rate con"}</div>
                      {broker.phone && <div className="tk-sub">{broker.phone}</div>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

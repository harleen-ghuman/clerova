import { useEffect, useState } from "react";
import { getBrokers } from "../api/loads";
import { errorMessage } from "../api/client";
import type { BrokerSummary, FmcsaStatus } from "../types/Load";

const FMCSA: Record<FmcsaStatus, { text: string; tone: string }> = {
  ACTIVE: { text: "Active broker", tone: "good" },
  NOT_AUTHORIZED: { text: "No broker authority", tone: "fix" },
  NOT_FOUND: { text: "Not found", tone: "fix" },
};

export function BrokersView({ refreshKey }: { refreshKey: number }) {
  const [brokers, setBrokers] = useState<BrokerSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

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
  }, [refreshKey]);

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
              {brokers.map(({ broker, loads, openLoads }) => (
                <tr key={broker.id}>
                  <td data-label="Broker">
                    <strong>{broker.name}</strong>
                  </td>
                  <td data-label="MC number">{broker.mcNumber ?? "Missing"}</td>
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
                      <div>{broker.email ?? (broker.phone ? "" : "Not on the rate con")}</div>
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

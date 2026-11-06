import { useEffect, useState } from "react";

import App from "../App";
import { getMe } from "../api/account";
import { TruckingApp } from "../trucking/TruckingApp";
import type { Me } from "../types/Account";
import { resetDemo, simulateNewEmail, simulateUrgentReply } from "./fakeApi";
import { isTruckingDemo } from "./mode";

const WEBSITE = "https://clerova-ai.com";

/** The real app on sample data, with a small bar to try the main flows. */
export function DemoRoot() {
  const [me, setMe] = useState<Me | null>(null);
  const trucking = isTruckingDemo();

  useEffect(() => {
    document.title = trucking ? "Clerova trucking demo" : "Clerova demo";
    document.body.classList.add("demo-mode");

    getMe().then(setMe);

    return () => document.body.classList.remove("demo-mode");
  }, [trucking]);

  if (!me?.organization) {
    return (
      <div className="auth-shell" role="main" aria-busy="true">
        <p className="auth-description">Loading the demo...</p>
      </div>
    );
  }

  return (
    <>
      {trucking ? (
        <TruckingApp
          organization={me.organization}
          userEmail={me.email}
          fullName={me.fullName}
          onAccountUpdated={setMe}
          onSignOut={() => window.location.assign("/")}
        />
      ) : (
        <App
          organization={me.organization}
          userEmail={me.email}
          fullName={me.fullName}
          onAccountUpdated={setMe}
          onSignOut={() => window.location.assign("/")}
        />
      )}
      <DemoBar trucking={trucking} />
    </>
  );
}

type Simulation = { key: string; label: string; busyLabel: string; run: () => Promise<string> };

function DemoBar({ trucking }: { trucking: boolean }) {
  const [open, setOpen] = useState(true);
  // Phones show one short row; "More" opens the rest
  const [more, setMore] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [simulations, setSimulations] = useState<Simulation[]>([]);
  const [reset, setReset] = useState<(() => void) | null>(null);

  // Room below the page so the panel never hides the end of it
  useEffect(() => {
    document.body.classList.toggle("demo-bar-open", open);
    document.body.classList.toggle("demo-bar-more", open && more);
    return () => document.body.classList.remove("demo-bar-open", "demo-bar-more");
  }, [open, more]);

  // The trucking demo's controls load with its sample data
  useEffect(() => {
    if (!trucking) {
      return;
    }

    let current = true;

    void import("./truckingFakeApi").then((api) => {
      if (current) {
        setSimulations([
          { key: "ratecon", label: "Simulate a rate con", busyLabel: "Receiving...", run: api.simulateRateCon },
          { key: "broker", label: "Simulate a broker email", busyLabel: "Receiving...", run: api.simulateBrokerEmail },
        ]);
        setReset(() => api.resetTruckingDemo);
      }
    });

    return () => {
      current = false;
    };
  }, [trucking]);

  const shown: Simulation[] = trucking
    ? simulations
    : [
        { key: "email", label: "Simulate a tenant email", busyLabel: "Receiving...", run: simulateNewEmail },
        { key: "urgent", label: "Simulate an urgent reply", busyLabel: "Receiving...", run: simulateUrgentReply },
      ];

  function simulate(simulation: Simulation) {
    setBusy(simulation.key);
    setNote(null);
    simulation
      .run()
      .then(setNote)
      .finally(() => setBusy(null));
  }

  if (!open) {
    return (
      <button type="button" className="demo-pill" onClick={() => setOpen(true)}>
        Demo controls
      </button>
    );
  }

  return (
    <aside className={`demo-bar ${more ? "more" : ""}`} aria-label="Demo controls">
      <div className="demo-bar-head">
        <strong>
          <span className="demo-tag">Demo</span>
          <span className="demo-title-long">Sample data. Nothing is sent.</span>
          <span className="demo-title-short">Sample data</span>
        </strong>
        <a className="rq-button rq-button-small rq-button-primary demo-phone-only" href={trucking ? "/?utm_source=demo&industry=trucking" : "/?utm_source=demo"}>
          Start free trial
        </a>
        <button
          type="button"
          className="rq-button rq-button-small demo-phone-only"
          aria-expanded={more}
          aria-controls="demo-more"
          onClick={() => setMore((current) => !current)}
        >
          {more ? "Less" : "More"}
        </button>
        <button type="button" className="demo-hide demo-wide-only" onClick={() => setOpen(false)}>
          Hide
        </button>
      </div>
      {note && (
        <p className="demo-note" role="status">
          {note}
        </p>
      )}

      <div className="demo-more" id="demo-more">
      <p className="demo-desc">
        {trucking
          ? "Try it like a dispatcher would. Clerova's drafts here are pre-written examples."
          : "Try it like a property manager would. Clerova's suggestions here are pre-written examples."}
      </p>
      <a className="demo-switch" href={trucking ? "/demo/" : "/demo/trucking/"}>
        {trucking ? "See the property management demo" : "Run a trucking company? See the trucking demo"}
      </a>

      <div className="demo-actions demo-simulate">
        {shown.map((simulation) => (
          <button
            key={simulation.key}
            type="button"
            className="rq-button rq-button-small"
            onClick={() => simulate(simulation)}
            disabled={busy !== null}
          >
            {busy === simulation.key ? simulation.busyLabel : simulation.label}
          </button>
        ))}
      </div>

      <div className="demo-actions">
        <a
          className="rq-button rq-button-small rq-button-primary demo-wide-only"
          href={trucking ? "/?utm_source=demo&industry=trucking" : "/?utm_source=demo"}
        >
          Start free trial
        </a>
        <button
          type="button"
          className="rq-button rq-button-small rq-button-quiet"
          onClick={() => {
            if (trucking) {
              reset?.();
            } else {
              resetDemo();
            }
            setNote("The demo is back to the start.");
          }}
        >
          Reset
        </button>
        <a className="rq-button rq-button-small rq-button-quiet" href={trucking ? `${WEBSITE}/trucking` : WEBSITE}>
          Exit demo
        </a>
        <button type="button" className="rq-button rq-button-small rq-button-quiet demo-phone-only" onClick={() => setOpen(false)}>
          Hide
        </button>
      </div>
      </div>
    </aside>
  );
}

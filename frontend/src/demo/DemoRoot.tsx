import { useEffect, useState } from "react";

import App from "../App";
import { getMe } from "../api/account";
import type { Me } from "../types/Account";
import { resetDemo, simulateNewEmail, simulateUrgentReply } from "./fakeApi";

const WEBSITE = "https://clerova-ai.com";

/** The real app on sample data, with a small bar to try the main flows. */
export function DemoRoot() {
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    document.title = "Clerova demo";
    document.body.classList.add("demo-mode");

    getMe().then(setMe);

    return () => document.body.classList.remove("demo-mode");
  }, []);

  if (!me?.organization) {
    return (
      <div className="auth-shell" role="main" aria-busy="true">
        <p className="auth-description">Loading the demo...</p>
      </div>
    );
  }

  return (
    <>
      <App
        organization={me.organization}
        userEmail={me.email}
        fullName={me.fullName}
        onAccountUpdated={setMe}
        onSignOut={() => window.location.assign(WEBSITE)}
      />
      <DemoBar />
    </>
  );
}

function DemoBar() {
  const [open, setOpen] = useState(true);
  const [busy, setBusy] = useState<"email" | "urgent" | null>(null);
  const [note, setNote] = useState<string | null>(null);

  function simulate(kind: "email" | "urgent") {
    setBusy(kind);
    setNote(null);
    (kind === "email" ? simulateNewEmail() : simulateUrgentReply())
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
    <aside className="demo-bar" aria-label="Demo controls">
      <div className="demo-bar-head">
        <strong>
          <span className="demo-tag">Demo</span> Sample data. Nothing is sent.
        </strong>
        <button type="button" className="demo-hide" onClick={() => setOpen(false)}>
          Hide
        </button>
      </div>
      <p className="demo-desc">
        Try it like a property manager would. Clerova's suggestions here are
        pre-written examples.
      </p>

      <div className="demo-actions demo-simulate">
        <button
          type="button"
          className="rq-button rq-button-small"
          onClick={() => simulate("email")}
          disabled={busy !== null}
        >
          {busy === "email" ? "Receiving..." : "Simulate a tenant email"}
        </button>
        <button
          type="button"
          className="rq-button rq-button-small"
          onClick={() => simulate("urgent")}
          disabled={busy !== null}
        >
          {busy === "urgent" ? "Receiving..." : "Simulate an urgent reply"}
        </button>
      </div>

      {note && (
        <p className="demo-note" role="status">
          {note}
        </p>
      )}

      <div className="demo-actions">
        <a className="rq-button rq-button-small rq-button-primary" href="/?utm_source=demo">
          Start free trial
        </a>
        <button
          type="button"
          className="rq-button rq-button-small rq-button-quiet"
          onClick={() => {
            resetDemo();
            setNote("The demo is back to the start.");
          }}
        >
          Reset
        </button>
        <a className="rq-button rq-button-small rq-button-quiet" href={WEBSITE}>
          Exit demo
        </a>
      </div>
    </aside>
  );
}

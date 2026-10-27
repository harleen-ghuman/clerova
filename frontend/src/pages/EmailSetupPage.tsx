import { useState, type ReactNode } from "react";

import type { EmailSettings } from "../types/Email";

interface EmailSetupPageProps {
  settings: EmailSettings | null;
  error: string | null;
}

interface Step {
  title: string;
  text: ReactNode;
  picture: ReactNode;
}

/** A simplified drawing of a mail settings screen. */
function Screen({ children }: { children: ReactNode }) {
  return (
    <div className="setup-screen" aria-hidden="true">
      <div className="setup-screen-bar">
        <span />
        <span />
        <span />
      </div>
      <div className="setup-screen-body">{children}</div>
    </div>
  );
}

function gmailSteps(address: string): Step[] {
  return [
    {
      title: "Open Gmail settings",
      text: (
        <>
          In Gmail on a computer, click the gear icon at the top right, then{" "}
          <strong>See all settings</strong>.
        </>
      ),
      picture: (
        <Screen>
          <div className="sk-row sk-right">
            <span className="sk-gear">⚙</span>
          </div>
          <div className="sk-menu">
            <span>Quick settings</span>
            <span className="sk-highlight">See all settings</span>
          </div>
        </Screen>
      ),
    },
    {
      title: "Go to Forwarding",
      text: (
        <>
          Open the <strong>Forwarding and POP/IMAP</strong> tab and click{" "}
          <strong>Add a forwarding address</strong>.
        </>
      ),
      picture: (
        <Screen>
          <div className="sk-tabs">
            <span>General</span>
            <span>Labels</span>
            <span className="sk-tab-active">Forwarding and POP/IMAP</span>
          </div>
          <div className="sk-row">
            <span className="sk-highlight">Add a forwarding address</span>
          </div>
        </Screen>
      ),
    },
    {
      title: "Paste your Clerova address",
      text: (
        <>
          Paste <strong>{address}</strong> and click{" "}
          <strong>Next</strong>, then <strong>Proceed</strong>.
        </>
      ),
      picture: (
        <Screen>
          <div className="sk-dialog">
            <span className="sk-label">Add a forwarding address</span>
            <span className="sk-input">{address}</span>
            <span className="sk-button">Next</span>
          </div>
        </Screen>
      ),
    },
    {
      title: "Confirm it",
      text: (
        <>
          Gmail sends a confirmation code to Clerova. It shows up at the top of
          this page within a minute. Enter it in Gmail and click{" "}
          <strong>Verify</strong>.
        </>
      ),
      picture: (
        <Screen>
          <div className="sk-row">
            <span className="sk-input sk-input-short">123456789</span>
            <span className="sk-button">Verify</span>
          </div>
        </Screen>
      ),
    },
    {
      title: "Turn forwarding on",
      text: (
        <>
          Choose <strong>Forward a copy of incoming mail to</strong> your
          Clerova address and <strong>keep Gmail's copy in the Inbox</strong>.
          Click <strong>Save Changes</strong> at the bottom.
        </>
      ),
      picture: (
        <Screen>
          <div className="sk-radio">
            <span className="sk-dot" /> Disable forwarding
          </div>
          <div className="sk-radio sk-highlight">
            <span className="sk-dot sk-dot-on" /> Forward a copy of incoming
            mail to Clerova
          </div>
          <div className="sk-row">
            <span className="sk-button">Save Changes</span>
          </div>
        </Screen>
      ),
    },
  ];
}

export function EmailSetupPage({ settings, error }: EmailSetupPageProps) {
  const [copied, setCopied] = useState<"idle" | "done" | "failed">("idle");

  async function handleCopy(address: string) {
    try {
      await navigator.clipboard.writeText(address);
      setCopied("done");
      window.setTimeout(() => setCopied("idle"), 2000);
    } catch {
      // Some browsers block the clipboard; the address is selectable
      setCopied("failed");
    }
  }

  if (error) {
    return <p className="action-error">{error}</p>;
  }

  if (!settings) {
    return <p>Loading email settings...</p>;
  }

  if (!settings.intakeEnabled || !settings.forwardingAddress) {
    return (
      <div className="empty-view">
        <h2>Email forwarding isn't ready yet</h2>
        <p>
          Your Clerova address is still being set up. In the meantime, add
          requests with "New request" on the Requests page.
        </p>
      </div>
    );
  }

  const address = settings.forwardingAddress;

  return (
    <div className="email-setup">
      <header className="page-header">
        <h1>Email setup</h1>
        <p>
          Forward emails to your Clerova address. Each one becomes a request
          with a suggested next step. You keep your inbox, and Clerova never
          needs your password.
        </p>
      </header>

      <div className="forwarding-address">
        <div>
          <span>Your Clerova address</span>
          <code>{address}</code>
        </div>
        <button
          type="button"
          className="rq-button rq-button-primary"
          onClick={() => handleCopy(address)}
        >
          {copied === "done" ? "Copied" : copied === "failed" ? "Select it to copy" : "Copy address"}
        </button>
      </div>

      {settings.gmailConfirmation && (
        <div className="gmail-confirmation">
          <h3>Gmail sent a confirmation email</h3>
          <p>
            Use the confirmation code or link below to finish step 4 in Gmail.
          </p>
          <pre>{settings.gmailConfirmation.text}</pre>
        </div>
      )}

      <section className="setup-section">
        <h2>Gmail or Google Workspace</h2>
        <ol className="setup-steps">
          {gmailSteps(address).map((step, index) => (
            <li key={step.title}>
              {step.picture}
              <div>
                <h3>
                  <span>{index + 1}</span>
                  {step.title}
                </h3>
                <p>{step.text}</p>
              </div>
            </li>
          ))}
        </ol>
        <p className="setup-tip">
          Only want some emails? Instead of forwarding everything, create a
          filter (for example, mail sent to maintenance@ or from your tenant
          portal) and choose "Forward it to" your Clerova address.
        </p>
        <p className="setup-tip">
          Just one email? Click Forward and send it to your Clerova address.
          Clerova uses the original sender, so your reply goes to the resident,
          not back to you.
        </p>
      </section>

      <section className="setup-section">
        <h2>Outlook or Microsoft 365</h2>
        <ol className="setup-list">
          <li>Open Outlook Settings → Mail → Forwarding.</li>
          <li>Turn on forwarding and paste your Clerova address.</li>
          <li>Check "Keep a copy of forwarded messages" and save.</li>
        </ol>
        <p className="setup-tip">
          Microsoft 365 organizations sometimes block forwarding to outside
          addresses. If emails don't arrive, ask your IT admin to allow it, or
          use an inbox rule that forwards specific emails.
        </p>
      </section>

      <section className="setup-section">
        <h2>Try it now</h2>
        <p>
          Send an email to {address} from any account, for example "The kitchen
          sink at 12 Oak St is leaking." It appears in Requests within a
          minute.
        </p>
        <p className="setup-tip">
          {settings.sendingEnabled
            ? "Approved replies are sent from this address with your company name. When the sender replies, the conversation continues on the same request."
            : "Sending replies from Clerova is not turned on yet. Copy approved replies and send them from your own email."}
        </p>
      </section>
    </div>
  );
}

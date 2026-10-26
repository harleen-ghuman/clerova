import { useState } from "react";

import type { EmailSettings } from "../types/Email";

interface EmailSetupPageProps {
  settings: EmailSettings | null;
  error: string | null;
}

export function EmailSetupPage({ settings, error }: EmailSetupPageProps) {
  const [copied, setCopied] = useState(false);

  async function handleCopy(address: string) {
    try {
      await navigator.clipboard.writeText(address);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  if (error) {
    return (
      <section className="dashboard-section">
        <p className="action-error">{error}</p>
      </section>
    );
  }

  if (!settings) {
    return (
      <section className="dashboard-section">
        <p>Loading email settings...</p>
      </section>
    );
  }

  if (!settings.intakeEnabled || !settings.forwardingAddress) {
    return (
      <section className="dashboard-section">
        <div className="empty-view">
          <div className="review-empty-icon">@</div>
          <h2>Email intake is not available yet</h2>
          <p>
            Email intake has not been turned on for this Clerova
            deployment. You can still enter requests from the dashboard.
          </p>
        </div>
      </section>
    );
  }

  const address = settings.forwardingAddress;

  return (
    <section className="dashboard-section email-setup">
      <div className="section-heading">
        <div>
          <span className="eyebrow">Email Intake</span>
          <h2>Your Clerova forwarding address</h2>
          <p>
            Anything sent or forwarded to this address becomes a request in
            Clerova, with a recommended next step ready for your review.
          </p>
        </div>
      </div>

      <div className="forwarding-address">
        <code>{address}</code>

        <button
          type="button"
          className="primary-button"
          onClick={() => handleCopy(address)}
        >
          {copied ? "Copied" : "Copy address"}
        </button>
      </div>

      {settings.gmailConfirmation && (
        <div className="gmail-confirmation">
          <h3>Gmail sent a confirmation email</h3>
          <p>
            Use the confirmation code or link below to finish adding your
            forwarding address in Gmail.
          </p>
          <pre>{settings.gmailConfirmation.text}</pre>
        </div>
      )}

      <div className="setup-steps">
        <article>
          <h3>Gmail or Google Workspace</h3>
          <ol>
            <li>Open Gmail Settings → See all settings → Forwarding and POP/IMAP.</li>
            <li>Click "Add a forwarding address" and paste your Clerova address.</li>
            <li>Gmail sends a confirmation email. It will appear on this page within a minute. Enter the code in Gmail.</li>
            <li>Choose "Forward a copy of incoming mail" and keep Gmail's copy, then save.</li>
          </ol>
          <p className="setup-tip">
            Only want some emails? Instead of forwarding everything, create a
            filter (for example, mail sent to maintenance@ or from your
            tenant portal) and choose "Forward it to" your Clerova address.
          </p>
        </article>

        <article>
          <h3>Outlook or Microsoft 365</h3>
          <ol>
            <li>Open Outlook Settings → Mail → Forwarding.</li>
            <li>Turn on forwarding and paste your Clerova address.</li>
            <li>Check "Keep a copy of forwarded messages" and save.</li>
          </ol>
          <p className="setup-tip">
            Microsoft 365 organizations sometimes block forwarding to external
            addresses. If emails don't arrive, ask your IT admin to allow it,
            or use an inbox rule that forwards specific emails.
          </p>
        </article>

        <article>
          <h3>Try it now</h3>
          <p>
            Send an email to {address} from any account, for example "The
            kitchen sink at 12 Oak St is leaking." It will appear in your
            Inbox and Work Items within a minute.
          </p>
          <p className="setup-tip">
            {settings.sendingEnabled
              ? "Approved replies are sent from this address. When the sender replies, the conversation continues on the same request in Clerova."
              : "Sending replies from Clerova is not turned on yet. Copy approved responses and send them from your own email."}
          </p>
        </article>
      </div>
    </section>
  );
}

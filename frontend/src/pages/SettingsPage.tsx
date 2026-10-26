import { useEffect, useState, type FormEvent } from "react";

import { updateAccount } from "../api/account";
import { errorMessage } from "../api/client";
import { friendlyError } from "../auth/friendlyError";
import { supabase } from "../auth/supabase";
import type { Me, Organization } from "../types/Account";

interface SettingsPageProps {
  email: string;
  fullName: string | null;
  organization: Organization;
  onSaved: (me: Me) => void;
  onManageBilling: () => void;
  onSignOut: () => void;
}

const MIN_PASSWORD_LENGTH = 8;

export function SettingsPage({
  email,
  fullName,
  organization,
  onSaved,
  onManageBilling,
  onSignOut,
}: SettingsPageProps) {
  const hasBilling = organization.billingEnabled && Boolean(organization.subscriptionStatus);

  return (
    <div className="settings">
      <header className="page-header">
        <h1>Settings</h1>
        <p>Your details, sign-in and billing.</p>
      </header>

      <DetailsSection
        fullName={fullName}
        organizationName={organization.name}
        onSaved={onSaved}
      />
      <EmailSection email={email} />
      <PasswordSection />

      <section className="settings-section">
        {hasBilling ? (
          <>
            <h2>Plan and billing</h2>
            <p>
              Change your card, see invoices or cancel. Cancelling keeps
              Clerova working until the end of the period you paid for.
            </p>
          </>
        ) : (
          <h2>Account</h2>
        )}
        <div className="settings-actions">
          {hasBilling && (
            <button type="button" className="secondary-action-button" onClick={onManageBilling}>
              Manage billing
            </button>
          )}
          <button type="button" className="secondary-action-button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </section>
    </div>
  );
}

function DetailsSection({
  fullName,
  organizationName,
  onSaved,
}: {
  fullName: string | null;
  organizationName: string;
  onSaved: (me: Me) => void;
}) {
  const [name, setName] = useState(fullName ?? "");
  const [company, setCompany] = useState(organizationName);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const changed = name.trim() !== (fullName ?? "") || company.trim() !== organizationName;

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);

    try {
      onSaved(await updateAccount({ fullName: name, organizationName: company }));
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err, "Unable to save your details."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="settings-section">
      <h2>Your details</h2>
      <form className="auth-form settings-form" onSubmit={save}>
        <label>
          Your name
          <input
            autoComplete="name"
            maxLength={255}
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setSaved(false);
            }}
            disabled={saving}
          />
        </label>
        <label>
          Company name
          <input
            autoComplete="organization"
            required
            maxLength={255}
            value={company}
            onChange={(event) => {
              setCompany(event.target.value);
              setSaved(false);
            }}
            disabled={saving}
          />
          <span className="auth-hint">Residents see this name on your replies.</span>
        </label>

        {error && <p className="action-error">{error}</p>}

        <div className="settings-actions">
          <button type="submit" className="primary-button" disabled={saving || !changed}>
            {saving ? "Saving..." : "Save"}
          </button>
          {saved && <span className="request-success">Saved.</span>}
        </div>
      </form>
    </section>
  );
}

function EmailSection({ email }: { email: string }) {
  const [newEmail, setNewEmail] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  async function changeEmail(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const address = newEmail.trim();

    if (address.toLowerCase() === email.toLowerCase()) {
      setError("That's already your email.");
      return;
    }

    setSending(true);
    setError(null);
    setSentTo(null);

    const { error: updateError } = await supabase.auth.updateUser(
      { email: address },
      { emailRedirectTo: window.location.origin }
    );

    setSending(false);

    if (updateError) {
      setError(
        updateError.message.toLowerCase().includes("already")
          ? "There's already an account with that email."
          : friendlyError(updateError.message)
      );
    } else {
      setSentTo(address);
      setNewEmail("");
    }
  }

  return (
    <section className="settings-section">
      <h2>Email</h2>
      <p>
        You sign in and get Clerova emails at <strong>{email}</strong>.
      </p>
      <form className="auth-form settings-form" onSubmit={changeEmail}>
        <label>
          New email
          <input
            type="email"
            autoComplete="email"
            required
            value={newEmail}
            onChange={(event) => setNewEmail(event.target.value)}
            disabled={sending}
          />
          <span className="auth-hint">
            We'll send a link to the new address. The change happens when you
            click it.
          </span>
        </label>

        {error && <p className="action-error">{error}</p>}
        {sentTo && (
          <p className="request-success">
            Check {sentTo} and click the link to finish. Until then, keep using {email}.
          </p>
        )}

        <div className="settings-actions">
          <button type="submit" className="secondary-action-button" disabled={sending}>
            {sending ? "Sending..." : "Change email"}
          </button>
        </div>
      </form>
    </section>
  );
}

function PasswordSection() {
  // null while loading; Google-only accounts have no password to change
  const [hasPassword, setHasPassword] = useState<boolean | null>(null);
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    supabase.auth.getUser().then(({ data }) => {
      const providers = (data.user?.app_metadata.providers as string[] | undefined) ?? [];
      setHasPassword(providers.length === 0 || providers.includes("email"));
    });
  }, []);

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);

    const { error: updateError } = await supabase.auth.updateUser({ password });

    setSaving(false);

    if (updateError) {
      setError(
        updateError.message.toLowerCase().includes("different from the old")
          ? "That's already your password."
          : friendlyError(updateError.message)
      );
    } else {
      setSaved(true);
      setPassword("");
    }
  }

  if (hasPassword === null) {
    return null;
  }

  if (!hasPassword) {
    return (
      <section className="settings-section">
        <h2>Password</h2>
        <p>You sign in with Google, so there's no password to manage here.</p>
      </section>
    );
  }

  return (
    <section className="settings-section">
      <h2>Password</h2>
      <form className="auth-form settings-form" onSubmit={changePassword}>
        <label>
          New password
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={MIN_PASSWORD_LENGTH}
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              setSaved(false);
            }}
            disabled={saving}
          />
          <span className="auth-hint">At least {MIN_PASSWORD_LENGTH} characters</span>
        </label>

        {error && <p className="action-error">{error}</p>}

        <div className="settings-actions">
          <button type="submit" className="secondary-action-button" disabled={saving}>
            {saving ? "Saving..." : "Change password"}
          </button>
          {saved && <span className="request-success">Password changed.</span>}
        </div>
      </form>
    </section>
  );
}

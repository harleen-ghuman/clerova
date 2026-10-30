import { useEffect, useRef, useState, type FormEvent } from "react";

import { updateAccount } from "../api/account";
import { cancelPlan, keepPlan } from "../api/billing";
import { errorMessage } from "../api/client";
import {
  COMPANY_INFO_FIELDS,
  MAX_FIELD_LENGTH,
  MAX_TOTAL_LENGTH,
  getCompanyInfo,
  saveCompanyInfo,
  type CompanyInfo,
  type CompanyInfoKey,
} from "../api/companyInfo";
import { friendlyError } from "../auth/friendlyError";
import { MIN_PASSWORD_LENGTH, PASSWORD_HINT, passwordProblem } from "../auth/passwordRules";
import { supabase } from "../auth/supabase";
import { AutoTextarea } from "../components/AutoTextarea";
import { isDemo } from "../demo/mode";
import type { Me, Organization } from "../types/Account";
import { PLANS, planName, planPrice } from "../utils/plans";

interface SettingsPageProps {
  email: string;
  fullName: string | null;
  organization: Organization;
  onSaved: (me: Me) => void;
  onManageBilling: () => void;
  /** The plan changed (cancelled or kept); reload the organization. */
  onBillingChanged: () => Promise<unknown>;
  onUpgrade: () => void;
  upgrading: boolean;
  /** Start at Company info with the cursor in the first answer. */
  focusCompanyInfo?: boolean;
  onCompanyInfoFocused?: () => void;
  onSignOut: () => void;
}


/** The company's standard answers, used in suggested replies. */
function CompanyInfoSection({
  focus,
  onFocused,
}: {
  focus: boolean;
  onFocused?: () => void;
}) {
  const sectionRef = useRef<HTMLElement>(null);
  const [saved, setSaved] = useState<CompanyInfo | null>(null);
  const [draft, setDraft] = useState<CompanyInfo>({});
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    let cancelled = false;

    getCompanyInfo()
      .then((info) => {
        if (!cancelled) {
          setSaved(info);
          setDraft(info);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(errorMessage(err, "Couldn't load your company info. Refresh to try again."));
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // Once the answers have loaded, so the first one can take the cursor
  useEffect(() => {
    const section = sectionRef.current;

    if (!focus || !section || (saved === null && loadError === null)) {
      return;
    }

    section.scrollIntoView({ block: "start" });
    section.querySelector<HTMLElement>("textarea")?.focus({ preventScroll: true });
    onFocused?.();
  }, [focus, saved, loadError, onFocused]);

  const value = (key: CompanyInfoKey) => draft[key] ?? "";
  const total = COMPANY_INFO_FIELDS.reduce((sum, field) => sum + value(field.key).trim().length, 0);
  const changed =
    saved !== null &&
    COMPANY_INFO_FIELDS.some((field) => value(field.key).trim() !== (saved[field.key] ?? "").trim());

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setDone(false);

    try {
      const info = await saveCompanyInfo(draft);
      setSaved(info);
      setDraft(info);
      setDone(true);
    } catch (err) {
      setError(errorMessage(err, "Couldn't save your company info. Please try again."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <section
      ref={sectionRef}
      className="settings-section"
      id="company-info"
      aria-labelledby="company-info-title"
    >
      <h2 id="company-info-title">Company info</h2>
      <p>
        Clerova uses these answers in suggested replies, so residents get a
        real answer to common questions. Fill in what applies and leave the
        rest blank. Questions about one resident's own lease or balance still
        go to your team.
      </p>

      {loadError ? (
        <p className="action-error">{loadError}</p>
      ) : saved === null ? (
        <p>Loading...</p>
      ) : (
        <form className="auth-form settings-form company-info-form" onSubmit={save}>
          {COMPANY_INFO_FIELDS.map((field) => (
            <label key={field.key}>
              {field.label}
              <AutoTextarea
                rows={2}
                maxLength={MAX_FIELD_LENGTH}
                placeholder={field.example}
                value={value(field.key)}
                onChange={(event) => {
                  setDraft((current) => ({ ...current, [field.key]: event.target.value }));
                  setDone(false);
                }}
                disabled={saving}
              />
            </label>
          ))}

          <p className={`auth-hint ${total > MAX_TOTAL_LENGTH ? "company-info-over" : ""}`}>
            {total.toLocaleString()} of {MAX_TOTAL_LENGTH.toLocaleString()} characters
            {total > MAX_TOTAL_LENGTH ? ". Shorten a few answers to save." : ""}
          </p>

          {error && <p className="action-error">{error}</p>}

          <div className="settings-actions">
            <button
              type="submit"
              className="primary-button"
              disabled={saving || !changed || total > MAX_TOTAL_LENGTH}
            >
              {saving ? "Saving..." : "Save company info"}
            </button>
            {done && <span className="request-success">Saved.</span>}
          </div>
        </form>
      )}
    </section>
  );
}

export function SettingsPage({
  email,
  fullName,
  organization,
  onSaved,
  onManageBilling,
  onBillingChanged,
  onUpgrade,
  upgrading,
  focusCompanyInfo = false,
  onCompanyInfoFocused,
  onSignOut,
}: SettingsPageProps) {
  const hasBilling = organization.billingEnabled && Boolean(organization.subscriptionStatus);

  return (
    <div className="settings">
      <header className="page-header">
        <h1>Settings</h1>
        <p>
          {hasBilling
            ? "Your details, company info, sign-in and billing."
            : "Your details, company info and sign-in."}
        </p>
      </header>

      <DetailsSection
        fullName={fullName}
        organizationName={organization.name}
        onSaved={onSaved}
      />
      <CompanyInfoSection focus={focusCompanyInfo} onFocused={onCompanyInfoFocused} />
      {isDemo() ? (
        <section className="settings-section">
          <h2>Sign-in</h2>
          <p>
            In the demo, the sign-in email and password can't be changed. In
            your own workspace, you change them here.
          </p>
        </section>
      ) : (
        <>
          <EmailSection email={email} />
          <PasswordSection />
        </>
      )}

      {hasBilling && (
        <BillingSection
          organization={organization}
          onManageBilling={onManageBilling}
          onBillingChanged={onBillingChanged}
          onUpgrade={onUpgrade}
          upgrading={upgrading}
        />
      )}

      <section className="settings-section">
        <h2>Account</h2>
        <div className="settings-actions">
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
      setError(errorMessage(err, "Couldn't save your details. Please try again."));
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
          <span className="auth-hint">
            Residents see this name on your replies. Your Clerova address
            stays the same.
          </span>
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

    const weak = passwordProblem(password);

    if (weak) {
      setError(weak);
      setSaved(false);
      return;
    }

    setSaving(true);
    setError(null);
    setSaved(false);

    const { error: updateError } = await supabase.auth.updateUser({ password });

    setSaving(false);

    if (updateError) {
      setError(friendlyError(updateError.message));
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
          <span className="auth-hint">{PASSWORD_HINT}</span>
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

function longDate(value: string | null): string | null {
  return value
    ? new Date(value).toLocaleDateString([], { month: "long", day: "numeric", year: "numeric" })
    : null;
}

/** The plan, what happens next, and cancel or keep it in one step. */
function BillingSection({
  organization,
  onManageBilling,
  onBillingChanged,
  onUpgrade,
  upgrading,
}: {
  organization: Organization;
  onManageBilling: () => void;
  onBillingChanged: () => Promise<unknown>;
  onUpgrade: () => void;
  upgrading: boolean;
}) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState<"cancel" | "keep" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const status = organization.subscriptionStatus;
  const trialing = status === "trialing";
  const cancelling = organization.cancelAtPeriodEnd;
  const canCancel = !cancelling && (trialing || status === "active" || status === "past_due");
  const ends = longDate(trialing ? organization.trialEndsAt ?? organization.currentPeriodEnd : organization.currentPeriodEnd);

  const plan = PLANS.find((item) => item.plan === organization.plan);
  const price = plan
    ? planPrice(
        organization.foundingCustomer ? plan.foundingMonthly : plan.monthly,
        organization.billingInterval === "year" ? "year" : "month"
      )
    : null;
  const priceText = price ? `$${price.amount.toLocaleString()} a ${price.per}` : null;

  const summary = cancelling
    ? trialing
      ? `Your plan is cancelled. Your trial ends${ends ? ` on ${ends}` : ""} and you won't be charged.`
      : `Your plan is cancelled. Clerova keeps working${ends ? ` until ${ends}` : " until the end of this billing period"}. You won't be charged again.`
    : status === "past_due"
      ? "Your last payment didn't go through. Update your card to keep Clerova running."
      : trialing
        ? `${planName(organization.plan)} · free trial${ends ? ` until ${ends}` : ""}.${priceText ? ` Then ${priceText}.` : ""}`
        : `${planName(organization.plan)}${priceText ? ` · ${priceText}` : ""}${ends ? ` · renews on ${ends}` : ""}.`;

  function run(kind: "cancel" | "keep") {
    setBusy(kind);
    setError(null);
    setDone(null);

    (kind === "cancel" ? cancelPlan() : keepPlan())
      .then(() => onBillingChanged())
      .then(() => {
        setConfirming(false);
        // The summary above already says the plan is cancelled
        setDone(kind === "keep" ? "Your plan will continue." : null);
      })
      .catch((err) =>
        setError(
          errorMessage(
            err,
            kind === "cancel"
              ? "Couldn't cancel your plan. Please try again."
              : "Couldn't keep your plan. Please try again."
          )
        )
      )
      .finally(() => setBusy(null));
  }

  return (
    <section className="settings-section" aria-label="Plan and billing">
      <h2>Plan and billing</h2>
      <p>{summary}</p>

      {confirming ? (
        <div className="billing-confirm" role="group" aria-label="Cancel your plan">
          <strong>Cancel your plan?</strong>
          <p>
            {trialing
              ? `Clerova keeps working until your trial ends${ends ? ` on ${ends}` : ""}. You won't be charged.`
              : `Clerova keeps working until ${ends ?? "the end of this billing period"}. You won't be charged again.`}{" "}
            After that, new emails won't be organized. Your requests and
            history stay saved, and you can choose a plan again anytime.
          </p>
          <div className="settings-actions">
            <button
              type="button"
              className="rq-button rq-button-small rq-button-danger"
              onClick={() => run("cancel")}
              disabled={busy !== null}
            >
              {busy === "cancel" ? "Cancelling..." : "Yes, cancel my plan"}
            </button>
            <button
              type="button"
              className="secondary-action-button"
              onClick={() => setConfirming(false)}
              disabled={busy !== null}
            >
              Keep my plan
            </button>
          </div>
        </div>
      ) : (
        <div className="settings-actions">
          {cancelling && (
            <button
              type="button"
              className="rq-button rq-button-small rq-button-primary"
              onClick={() => run("keep")}
              disabled={busy !== null}
            >
              {busy === "keep" ? "Keeping your plan..." : "Keep my plan"}
            </button>
          )}
          {organization.plan === "STARTER" && status !== "past_due" && (
            <button
              type="button"
              className="secondary-action-button"
              onClick={onUpgrade}
              disabled={upgrading || busy !== null}
            >
              {upgrading ? "Upgrading..." : "Upgrade to Growth"}
            </button>
          )}
          <button
            type="button"
            className="secondary-action-button"
            onClick={onManageBilling}
            disabled={busy !== null}
          >
            {status === "past_due" ? "Update card" : "Manage billing"}
          </button>
          {canCancel && (
            <button
              type="button"
              className="rq-button rq-button-small rq-button-quiet"
              onClick={() => {
                setConfirming(true);
                setDone(null);
                setError(null);
              }}
              disabled={busy !== null}
            >
              Cancel plan
            </button>
          )}
        </div>
      )}

      {done && (
        <p className="request-success" role="status">
          {done}
        </p>
      )}
      {error && <p className="action-error">{error}</p>}
    </section>
  );
}

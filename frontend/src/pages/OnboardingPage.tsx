import { useState, type FormEvent } from "react";

import { completeOnboarding } from "../api/account";
import { errorMessage } from "../api/client";
import { getReferralSource } from "../auth/referral";
import type { Me } from "../types/Account";

interface OnboardingPageProps {
  email: string;
  /** From Google, when they signed up with it. */
  suggestedName: string;
  onComplete: (me: Me) => void;
  onSignOut: () => void;
}

const UNITS_MANAGED = ["1-50", "51-200", "201-500", "501-2000", "2000+"];

const REFERRAL_SOURCES = [
  "Instagram",
  "YouTube",
  "LinkedIn",
  "Google search",
  "Referral from someone",
  "Other",
];

export function OnboardingPage({
  email,
  suggestedName,
  onComplete,
  onSignOut,
}: OnboardingPageProps) {
  // "website" only says they came through our site, so still ask
  const storedSource = getReferralSource();
  const trackedSource = storedSource === "website" ? null : storedSource;

  const [fullName, setFullName] = useState(suggestedName);
  const [organizationName, setOrganizationName] = useState("");
  const [companySize, setCompanySize] = useState("");
  const [referralSource, setReferralSource] = useState(
    trackedSource ?? ""
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    try {
      setSubmitting(true);
      setError(null);

      const me = await completeOnboarding({
        fullName,
        organizationName,
        industry: "PROPERTY_MANAGEMENT",
        companySize,
        referralSource: referralSource || storedSource || "",
      });

      onComplete(me);
    } catch (err) {
      setError(errorMessage(err, "Unable to set up your organization."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card auth-card-wide">
        <div className="auth-brand">
          <div className="brand-mark">C</div>
          <strong>Clerova</strong>
        </div>

        <h1>Set up your workspace</h1>
        <p className="auth-description">
          Signed in as {email}. Tell us a little about your property
          management business.
        </p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            Your name
            <input
              autoComplete="name"
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              disabled={submitting}
            />
          </label>

          <label>
            Company name
            <input
              autoComplete="organization"
              required
              value={organizationName}
              onChange={(event) => setOrganizationName(event.target.value)}
              disabled={submitting}
            />
          </label>

          <label>
            Units managed
            <select
              value={companySize}
              onChange={(event) => setCompanySize(event.target.value)}
              disabled={submitting}
            >
              <option value="">Select...</option>
              {UNITS_MANAGED.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </label>

          {!trackedSource && (
            <label>
              How did you hear about Clerova?
              <select
                value={referralSource}
                onChange={(event) => setReferralSource(event.target.value)}
                disabled={submitting}
              >
                <option value="">Select...</option>
                {REFERRAL_SOURCES.map((source) => (
                  <option key={source} value={source}>
                    {source}
                  </option>
                ))}
              </select>
            </label>
          )}

          {error && <p className="action-error">{error}</p>}

          <button
            type="submit"
            className="primary-button auth-submit"
            disabled={submitting}
          >
            {submitting ? "Setting up..." : "Continue"}
          </button>
        </form>

        <div className="auth-links">
          <button type="button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </div>
    </div>
  );
}

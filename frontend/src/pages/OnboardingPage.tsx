import { useState, type FormEvent } from "react";

import { completeOnboarding, getMe } from "../api/account";
import { ApiRequestError, errorMessage } from "../api/client";
import { forgetChosenIndustry, getChosenIndustry, getReferralSource } from "../auth/referral";
import type { Industry, Me } from "../types/Account";
import { TRUCKING_ENABLED } from "../config";

interface OnboardingPageProps {
  email: string;
  /** From Google, when they signed up with it. */
  suggestedName: string;
  onComplete: (me: Me) => void;
  onSignOut: () => void;
}

const UNITS_MANAGED = ["1-50", "51-200", "201-500", "501-2000", "2000+"];
const TRUCKS = ["1-2", "3-9", "10-25", "26-100", "100+"];

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
  // Coming from the trucking page picks trucking; it can still be changed here
  const [industry, setIndustry] = useState<Industry>(
    TRUCKING_ENABLED && getChosenIndustry() === "TRUCKING" ? "TRUCKING" : "PROPERTY_MANAGEMENT"
  );
  const [companySize, setCompanySize] = useState("");
  const trucking = industry === "TRUCKING";
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
        industry,
        companySize,
        referralSource: referralSource || storedSource || "",
      });

      forgetChosenIndustry();
      onComplete(me);
    } catch (err) {
      // Set up meanwhile (another tab or a double click): just continue
      if (err instanceof ApiRequestError && err.status === 409) {
        try {
          const current = await getMe();

          if (current.organization) {
            onComplete(current);
            return;
          }
        } catch {
          // Fall through to the message
        }
      }

      setError(errorMessage(err, "Couldn't set up your workspace. Please try again."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell" role="main">
      <div className="auth-card auth-card-wide">
        <div className="auth-brand">
          <div className="brand-mark">C</div>
          <strong>Clerova</strong>
        </div>

        <h1>Set up your workspace</h1>
        <p className="auth-description">
          {TRUCKING_ENABLED
            ? `Signed in as ${email}. Tell us a little about your business.`
            : `Signed in as ${email}. Tell us a little about your property management business.`}
        </p>

        <form className="auth-form" onSubmit={handleSubmit}>
          {TRUCKING_ENABLED && (
            <fieldset className="industry-options" disabled={submitting}>
              <legend>What does your company do?</legend>
              {(
                [
                  ["PROPERTY_MANAGEMENT", "Property management", "Tenant emails and maintenance requests"],
                  ["TRUCKING", "Trucking", "Rate cons, broker emails and loads"],
                ] as const
              ).map(([value, name, text]) => (
                <label key={value} className={`industry-option ${industry === value ? "selected" : ""}`}>
                  <input
                    type="radio"
                    name="industry"
                    value={value}
                    checked={industry === value}
                    onChange={() => {
                      setIndustry(value);
                      setCompanySize("");
                    }}
                  />
                  <strong>{name}</strong>
                  <span>{text}</span>
                </label>
              ))}
            </fieldset>
          )}
          <label>
            Your name
            <input
              autoComplete="name"
              maxLength={255}
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
              maxLength={255}
              value={organizationName}
              onChange={(event) => setOrganizationName(event.target.value)}
              disabled={submitting}
            />
          </label>

          <label>
            {trucking ? "Trucks" : "Units managed"}
            <select
              value={companySize}
              onChange={(event) => setCompanySize(event.target.value)}
              disabled={submitting}
            >
              <option value="">Select...</option>
              {(trucking ? TRUCKS : UNITS_MANAGED).map((option) => (
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

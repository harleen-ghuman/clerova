import { useState, type FormEvent } from "react";

import { completeOnboarding } from "../api/account";
import { errorMessage } from "../api/client";
import { getReferralSource } from "../auth/referral";
import type { Industry, Me } from "../types/Account";

interface OnboardingPageProps {
  email: string;
  onComplete: (me: Me) => void;
  onSignOut: () => void;
}

const COMPANY_SIZES: Record<Industry, { label: string; options: string[] }> = {
  PROPERTY_MANAGEMENT: {
    label: "Doors / units managed",
    options: ["1-50", "51-200", "201-500", "501-2000", "2000+"],
  },
  TRUCKING: {
    label: "Trucks in your fleet",
    options: ["1", "2-5", "6-20", "21-50", "50+"],
  },
};

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
  onComplete,
  onSignOut,
}: OnboardingPageProps) {
  const trackedSource = getReferralSource();

  const [fullName, setFullName] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [industry, setIndustry] = useState<Industry | "">("");
  const [companySize, setCompanySize] = useState("");
  const [referralSource, setReferralSource] = useState(
    trackedSource ?? ""
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!industry) {
      setError("Please choose your industry.");
      return;
    }

    try {
      setSubmitting(true);
      setError(null);

      const me = await completeOnboarding({
        fullName,
        organizationName,
        industry,
        companySize,
        referralSource,
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
          Signed in as {email}. Tell us a little about your business so
          Clerova can handle your requests the right way.
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

          <fieldset className="industry-options" disabled={submitting}>
            <legend>Industry</legend>

            {(
              [
                ["PROPERTY_MANAGEMENT", "Property management", "Tenant, owner and maintenance requests"],
                ["TRUCKING", "Trucking", "Broker, shipper, driver and repair emails"],
              ] as const
            ).map(([value, title, subtitle]) => (
              <label
                key={value}
                className={`industry-option ${industry === value ? "selected" : ""}`}
              >
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
                <strong>{title}</strong>
                <span>{subtitle}</span>
              </label>
            ))}
          </fieldset>

          {industry && (
            <label>
              {COMPANY_SIZES[industry].label}
              <select
                value={companySize}
                onChange={(event) => setCompanySize(event.target.value)}
                disabled={submitting}
              >
                <option value="">Select...</option>
                {COMPANY_SIZES[industry].options.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </label>
          )}

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
            {submitting ? "Setting up..." : "Start my free trial"}
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

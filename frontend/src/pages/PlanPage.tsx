import { useEffect, useState } from "react";

import { errorMessage } from "../api/client";
import {
  getBillingOffer,
  startCheckout,
  type BillingInterval,
  type PaidPlan,
} from "../api/billing";
import type { Organization } from "../types/Account";
import { getChosenPlan } from "../auth/referral";
import { PLANS, planPrice } from "../utils/plans";

interface PlanPageProps {
  organization: Organization;
  notice: string | null;
  onSignOut: () => void;
}

const TRIAL_DAYS = 14;

/**
 * Shown until the organization has a subscription. Checkout collects a card
 * and starts the free trial.
 */
export function PlanPage({ organization, notice, onSignOut }: PlanPageProps) {
  const [interval, setBillingInterval] = useState<BillingInterval>("month");
  const [foundingSpotsLeft, setFoundingSpotsLeft] = useState(0);
  const [submitting, setSubmitting] = useState<PaidPlan | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [chosenPlan] = useState(getChosenPlan);
  const [trialEnd] = useState(() =>
    new Date(Date.now() + TRIAL_DAYS * 24 * 60 * 60 * 1000).toLocaleDateString(
      [],
      { month: "long", day: "numeric" }
    )
  );

  // A first subscription comes with the free trial
  const firstTime = organization.subscriptionStatus === null;
  const founding = organization.foundingCustomer || foundingSpotsLeft > 0;

  useEffect(() => {
    getBillingOffer()
      .then((offer) => setFoundingSpotsLeft(offer.foundingSpotsLeft))
      .catch((err) => console.error("Failed to load the offer:", err));
  }, []);

  async function choose(plan: PaidPlan) {
    try {
      setSubmitting(plan);
      setError(null);

      const { url } = await startCheckout(plan, interval);
      window.location.assign(url);
    } catch (err) {
      setError(errorMessage(err, "Unable to open checkout. Please try again."));
      setSubmitting(null);
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-card plan-card">
        <div className="auth-brand">
          <span className="brand-mark">C</span>
          Clerova
        </div>

        <h1>{firstTime ? "Start your free trial" : "Choose a plan"}</h1>
        <p className="auth-description">
          {firstTime
            ? `Try Clerova free for ${TRIAL_DAYS} days. You won't be charged until ${trialEnd}. Cancel anytime before then and you pay nothing.`
            : `Choose a plan to keep using Clerova for ${organization.name}.`}
        </p>

        {notice && <p className="plan-notice">{notice}</p>}

        <div className="plan-interval" role="group" aria-label="Billing period">
          <button
            type="button"
            className={interval === "month" ? "active" : ""}
            aria-pressed={interval === "month"}
            onClick={() => setBillingInterval("month")}
          >
            Monthly
          </button>
          <button
            type="button"
            className={interval === "year" ? "active" : ""}
            aria-pressed={interval === "year"}
            onClick={() => setBillingInterval("year")}
          >
            Yearly <span>2 months free</span>
          </button>
        </div>

        {founding && (
          <p className="plan-founding">
            Founding price, locked in for as long as you stay.
            {foundingSpotsLeft > 0 && ` ${foundingSpotsLeft} of 20 spots left.`}
          </p>
        )}

        <div className="plan-options">
          {PLANS.map((item) => {
            const price = planPrice(founding ? item.foundingMonthly : item.monthly, interval);
            const regular = planPrice(item.monthly, interval);

            return (
              <article
                key={item.plan}
                className={`plan-option ${chosenPlan === item.plan ? "plan-option-chosen" : ""}`}
              >
                <h2>
                  {item.name}
                  {chosenPlan === item.plan && <span className="plan-pick">Your pick</span>}
                </h2>
                <p className="plan-price">
                  <strong>${price.amount.toLocaleString()}</strong> / {price.per}
                  {founding && <s>${regular.amount.toLocaleString()}</s>}
                </p>
                <p className="plan-requests">
                  {item.requests.toLocaleString()} requests a month
                </p>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => choose(item.plan)}
                  disabled={submitting !== null}
                >
                  {submitting === item.plan
                    ? "Opening checkout..."
                    : firstTime
                      ? "Start free trial"
                      : `Choose ${item.name}`}
                </button>
              </article>
            );
          })}
        </div>

        <p className="auth-hint">
          A request is one email or message Clerova organizes for you. Payments
          are handled securely by Stripe. See our{" "}
          <a href="https://clerova-ai.com/terms" target="_blank" rel="noreferrer">
            Terms
          </a>
          .
        </p>

        {error && <p className="action-error">{error}</p>}

        <div className="auth-links">
          <button type="button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </div>
    </div>
  );
}

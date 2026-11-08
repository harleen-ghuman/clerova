import { useEffect, useId, useState } from "react";

import { ApiRequestError, errorMessage } from "../api/client";
import { getBillingOffer, startCheckout, type BillingInterval } from "../api/billing";
import type { Organization } from "../types/Account";
import {
  CONTACT_EMAIL,
  MAX_TRUCKS,
  dollars,
  truckingPlanSummary,
  truckingPrice,
  setPlanCurrency,
} from "../utils/truckingPlans";

interface TruckingPlanPageProps {
  organization: Organization;
  notice: string | null;
  onSignOut: () => void;
}

const TRIAL_DAYS = 14;

/** Shown until a carrier has a subscription. Priced by the number of trucks. */
export function TruckingPlanPage({ organization, notice, onSignOut }: TruckingPlanPageProps) {
  // Canadian carriers are billed in CAD
  setPlanCurrency(organization.country === "CA" ? "CAD" : "USD");
  const id = useId();
  const [interval, setBillingInterval] = useState<BillingInterval>("month");
  const [trucks, setTrucks] = useState(String(organization.trucks ?? 3));
  const [foundingSpotsLeft, setFoundingSpotsLeft] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const firstTime = organization.subscriptionStatus === null;
  const ended = ["canceled", "unpaid", "incomplete_expired", "paused"].includes(organization.subscriptionStatus ?? "");
  const founding = organization.foundingCustomer || foundingSpotsLeft > 0;
  const count = Number(trucks);
  const valid = Number.isInteger(count) && count >= 1 && count <= MAX_TRUCKS;
  const price = valid ? truckingPrice(count, interval, founding) : null;
  const bigFleet = Number.isInteger(count) && count > MAX_TRUCKS;
  const regular = valid ? truckingPrice(count, interval, false) : null;

  useEffect(() => {
    getBillingOffer()
      .then((offer) => setFoundingSpotsLeft(offer.foundingSpotsLeft))
      .catch((err) => console.error("Failed to load the offer:", err));
  }, []);

  useEffect(() => {
    // Back from Stripe with the Back button: let them start again
    function onPageShow(event: PageTransitionEvent) {
      if (event.persisted) {
        setSubmitting(false);
      }
    }

    window.addEventListener("pageshow", onPageShow);
    return () => window.removeEventListener("pageshow", onPageShow);
  }, []);

  function step(by: number) {
    // From an empty or too-big number, + and − land between 1 and the most allowed
    const next = Math.min(MAX_TRUCKS, Math.max(1, (Number.isInteger(count) ? Math.min(count, MAX_TRUCKS + 1) : 0) + by));
    setTrucks(String(next));
  }

  async function start() {
    if (!valid) {
      setError(
        bigFleet
          ? `For 50 or more trucks, email ${CONTACT_EMAIL} and we'll set up your plan.`
          : `Enter how many trucks you run, from 1 to ${MAX_TRUCKS}.`
      );
      return;
    }

    try {
      setSubmitting(true);
      setError(null);
      const { url } = await startCheckout("STARTER", interval, count);
      window.location.assign(url);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 409) {
        window.location.reload();
        return;
      }

      setError(errorMessage(err, "Couldn't open checkout. Please try again."));
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell" role="main">
      <div className="auth-card plan-card">
        <div className="auth-brand">
          <span className="brand-mark">C</span>
          <strong>Clerova</strong>
        </div>

        <h1>{firstTime ? "Start your free trial" : "Choose your plan"}</h1>
        <p className="auth-description">
          {firstTime
            ? `Try Clerova free for ${TRIAL_DAYS} days. You won't be charged until your trial ends. Cancel anytime before then and you pay nothing.`
            : ended
              ? `Your plan has ended. Start it again to keep using Clerova for ${organization.name}. Your loads and history are saved.`
              : `Choose a plan to keep using Clerova for ${organization.name}.`}
        </p>

        {notice && <p className="plan-notice">{notice}</p>}

        <label className="plan-trucks-label" htmlFor={`${id}-trucks`}>
          How many trucks do you run?
        </label>
        <div className="plan-trucks">
          <button type="button" aria-label="One truck less" onClick={() => step(-1)} disabled={submitting || (valid && count <= 1)}>
            −
          </button>
          <input
            id={`${id}-trucks`}
            inputMode="numeric"
            value={trucks}
            maxLength={3}
            onChange={(event) => setTrucks(event.target.value.replace(/\D/g, ""))}
            disabled={submitting}
          />
          <button type="button" aria-label="One truck more" onClick={() => step(1)} disabled={submitting || (valid && count >= MAX_TRUCKS) || bigFleet}>
            +
          </button>
        </div>

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
            {foundingSpotsLeft > 0 && ` ${foundingSpotsLeft} ${foundingSpotsLeft === 1 ? "spot" : "spots"} left.`}
          </p>
        )}

        <article className="plan-option plan-option-chosen" aria-live="polite">
          {price && regular ? (
            <>
              <h2>{price.plan === "FLEET" ? "Fleet" : "Starter"}</h2>
              <p className="plan-price">
                <strong>{dollars(price.amount)}</strong> / {price.per}
                {founding && <s>{dollars(regular.amount)}</s>}
              </p>
              <p className="plan-requests">
                {truckingPlanSummary(price.plan, founding)}.
              </p>
            </>
          ) : bigFleet ? (
            <>
              <h2>50 trucks or more</h2>
              <p className="plan-requests">
                We set up larger fleets by hand, with a price for your fleet. Email{" "}
                <a href={`mailto:${CONTACT_EMAIL}?subject=Clerova%20for%20${count}%20trucks`}>{CONTACT_EMAIL}</a> and we'll
                reply within one business day.
              </p>
            </>
          ) : (
            <p className="plan-requests">Enter how many trucks you run to see your price.</p>
          )}
          {!bigFleet && (
            <button type="button" className="primary-button" onClick={() => void start()} disabled={submitting || !valid}>
              {submitting ? "Opening checkout..." : firstTime ? "Start free trial" : "Continue"}
            </button>
          )}
        </article>

        {error && <p className="action-error">{error}</p>}

        <p className="auth-hint">
          Change your trucks anytime in Settings. Payments are handled securely by Stripe. See our{" "}
          <a href="https://clerova-ai.com/terms" target="_blank" rel="noreferrer">
            Terms
          </a>
          .
        </p>

        <div className="auth-links">
          <button type="button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </div>
    </div>
  );
}

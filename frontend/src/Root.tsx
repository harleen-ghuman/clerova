import { useEffect, useState } from "react";
import type { Session } from "@supabase/supabase-js";

import App from "./App";
import { getMe } from "./api/account";
import { completeCheckout } from "./api/billing";
import { errorMessage } from "./api/client";
import { isAuthConfigured, supabase } from "./auth/supabase";
import { AuthPage } from "./pages/AuthPage";
import { OnboardingPage } from "./pages/OnboardingPage";
import { PlanPage } from "./pages/PlanPage";
import type { Me } from "./types/Account";

/** Stripe Checkout sends the user back with ?checkout=success|cancelled. */
function readCheckoutReturn() {
  const params = new URLSearchParams(window.location.search);

  return {
    result: params.get("checkout"),
    sessionId: params.get("session_id"),
  };
}

/**
 * Decides what the visitor sees: sign-in/sign-up, onboarding for a new
 * account, or the app for a user whose organization is set up.
 */
export function Root() {
  // undefined while the stored session is being restored
  const [session, setSession] = useState<Session | null | undefined>(
    undefined
  );
  const [recoveringPassword, setRecoveringPassword] = useState(false);
  const [me, setMe] = useState<Me | null>(null);
  const [meError, setMeError] = useState<string | null>(null);

  const [checkoutReturn] = useState(readCheckoutReturn);

  const userId = session?.user.id;

  useEffect(() => {
    if (checkoutReturn.result) {
      window.history.replaceState(null, "", window.location.pathname);
    }
  }, [checkoutReturn]);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session);
    });

    const { data } = supabase.auth.onAuthStateChange((event, nextSession) => {
      if (event === "PASSWORD_RECOVERY") {
        setRecoveringPassword(true);
      }

      setSession(nextSession);
    });

    return () => data.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!userId) {
      return;
    }

    let cancelled = false;

    // Apply a finished checkout first so the new plan shows right away
    const checkoutDone =
      checkoutReturn.result === "success" && checkoutReturn.sessionId
        ? completeCheckout(checkoutReturn.sessionId).catch((err) =>
            console.error("Failed to confirm checkout:", err)
          )
        : Promise.resolve();

    checkoutDone
      .then(() => getMe())
      .then((result) => {
        if (!cancelled) {
          setMe(result);
          setMeError(null);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setMeError(errorMessage(err, "Unable to load your account."));
        }
      });

    return () => {
      cancelled = true;
    };
  }, [userId, checkoutReturn]);

  // Stripe may confirm a few seconds after the user is sent back, so keep
  // checking instead of asking them to refresh.
  const waitingForPlan =
    checkoutReturn.result === "success" &&
    Boolean(me?.organization?.subscriptionRequired);

  useEffect(() => {
    if (!waitingForPlan) {
      return;
    }

    let cancelled = false;
    let attempts = 0;

    const interval = window.setInterval(() => {
      attempts += 1;

      if (attempts > 15) {
        window.clearInterval(interval);
        return;
      }

      getMe()
        .then((result) => {
          if (!cancelled) {
            setMe(result);
          }
        })
        .catch((err) => console.error("Failed to refresh the account:", err));
    }, 2000);

    return () => {
      cancelled = true;
      window.clearInterval(interval);
    };
  }, [waitingForPlan]);

  async function handleSignOut() {
    await supabase.auth.signOut();
    setMe(null);
  }

  if (!isAuthConfigured) {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <h1>Sign-in is not configured</h1>
          <p className="auth-description">
            Set VITE_SUPABASE_URL and VITE_SUPABASE_ANON_KEY for this
            deployment.
          </p>
        </div>
      </div>
    );
  }

  if (session === undefined) {
    return <div className="auth-shell" aria-busy="true" />;
  }

  if (!session || recoveringPassword) {
    return (
      <AuthPage
        recoveringPassword={recoveringPassword}
        onPasswordReset={() => setRecoveringPassword(false)}
      />
    );
  }

  // Ignore a profile left over from a previous user until the new one loads
  const currentMe = me?.userId === session.user.id ? me : null;

  if (!currentMe) {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          {meError ? (
            <>
              <h1>Something went wrong</h1>
              <p className="action-error">{meError}</p>
              <div className="auth-links">
                <button type="button" onClick={handleSignOut}>
                  Sign out
                </button>
              </div>
            </>
          ) : (
            <p className="auth-description">Loading your workspace...</p>
          )}
        </div>
      </div>
    );
  }

  if (!currentMe.organization) {
    return (
      <OnboardingPage
        email={currentMe.email || session.user.email || ""}
        onComplete={setMe}
        onSignOut={handleSignOut}
      />
    );
  }

  if (currentMe.organization.subscriptionRequired) {
    return (
      <PlanPage
        organization={currentMe.organization}
        notice={
          checkoutReturn.result === "cancelled"
            ? "Checkout was cancelled. Choose a plan when you're ready."
            : checkoutReturn.result === "success"
              ? "Confirming your card with Stripe. This takes a few seconds."
              : null
        }
        onSignOut={handleSignOut}
      />
    );
  }

  return (
    <App
      welcome={checkoutReturn.result === "success"}
      organization={currentMe.organization}
      userEmail={currentMe.email}
      onSignOut={handleSignOut}
    />
  );
}

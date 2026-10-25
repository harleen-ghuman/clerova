import { useEffect, useState } from "react";
import type { Session } from "@supabase/supabase-js";

import App from "./App";
import { getMe } from "./api/account";
import { errorMessage } from "./api/client";
import { isAuthConfigured, supabase } from "./auth/supabase";
import { AuthPage } from "./pages/AuthPage";
import { OnboardingPage } from "./pages/OnboardingPage";
import type { Me } from "./types/Account";

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

  const userId = session?.user.id;

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

    getMe()
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
  }, [userId]);

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

  return (
    <App
      organization={currentMe.organization}
      userEmail={currentMe.email}
      onSignOut={handleSignOut}
    />
  );
}

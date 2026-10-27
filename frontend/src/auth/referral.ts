const STORAGE_KEY = "clerova.referralSource";
const PLAN_KEY = "clerova.chosenPlan";
const EMAIL_KEY = "clerova.prefillEmail";

/**
 * Remembers where a visitor came from (utm_source or ref in the URL, e.g.
 * ?utm_source=instagram) so it can be saved with their organization at
 * onboarding. The first source seen wins.
 */
export function captureReferralSource() {
  try {
    const params = new URLSearchParams(window.location.search);
    const source = params.get("utm_source") ?? params.get("ref");

    if (source && !localStorage.getItem(STORAGE_KEY)) {
      localStorage.setItem(STORAGE_KEY, source.slice(0, 100));
    }

    // The plan picked on the website's pricing section, shown first later
    const plan = params.get("plan")?.toUpperCase();

    if (plan === "STARTER" || plan === "GROWTH") {
      localStorage.setItem(PLAN_KEY, plan);
    }

    const email = params.get("email");

    if (email) {
      sessionStorage.setItem(EMAIL_KEY, email.slice(0, 200));
    }
  } catch {
    // Storage can be unavailable (private mode); referral is optional.
  }

  stripWebsiteParams();
}

/**
 * Removes the website's parameters from the address bar once read, so the
 * email isn't kept in history or sent on to other sites the page loads.
 */
function stripWebsiteParams() {
  try {
    const url = new URL(window.location.href);
    let changed = false;

    for (const name of ["email", "plan", "utm_source", "ref"]) {
      if (url.searchParams.has(name)) {
        url.searchParams.delete(name);
        changed = true;
      }
    }

    if (changed) {
      window.history.replaceState(window.history.state, "", url.pathname + url.search + url.hash);
    }
  } catch {
    // Leave the address as it is
  }
}

export function getReferralSource(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

/** The plan picked on the website, if any. */
export function getChosenPlan(): "STARTER" | "GROWTH" | null {
  try {
    const plan = localStorage.getItem(PLAN_KEY);
    return plan === "STARTER" || plan === "GROWTH" ? plan : null;
  } catch {
    return null;
  }
}

/** Whether the app was opened from a "Sign in" link (?mode=sign-in). */
export function wantsSignIn(): boolean {
  try {
    return new URLSearchParams(window.location.search).get("mode") === "sign-in";
  } catch {
    return false;
  }
}

/** Email typed on the website's sign-up box (?email=), to prefill sign-up. */
export function prefilledEmail(): string {
  try {
    return (
      sessionStorage.getItem(EMAIL_KEY) ??
      new URLSearchParams(window.location.search).get("email")?.slice(0, 200) ??
      ""
    );
  } catch {
    return "";
  }
}

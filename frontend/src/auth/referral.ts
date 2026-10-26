const STORAGE_KEY = "clerova.referralSource";
const PLAN_KEY = "clerova.chosenPlan";

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
  } catch {
    // Storage can be unavailable (private mode); referral is optional.
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

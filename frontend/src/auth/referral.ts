const STORAGE_KEY = "clerova.referralSource";

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

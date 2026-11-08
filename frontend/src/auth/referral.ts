const STORAGE_KEY = "clerova.referralSource";
const PLAN_KEY = "clerova.chosenPlan";
const EMAIL_KEY = "clerova.prefillEmail";
const SIGNING_UP_KEY = "clerova.signingUp";
const INDUSTRY_KEY = "clerova.industry";
const COUNTRY_KEY = "clerova.country";

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

    // Which landing page they came from: picks the demo and the sign-up choice
    const industry = params.get("industry")?.toLowerCase();

    if (industry === "trucking" || industry === "property-management") {
      localStorage.setItem(INDUSTRY_KEY, industry === "trucking" ? "TRUCKING" : "PROPERTY_MANAGEMENT");
    }

    // A Canadian sign-up link (like the QR code for Canadian carriers)
    if (params.get("country")?.toUpperCase() === "CA") {
      localStorage.setItem(COUNTRY_KEY, "CA");
    }

    const email = params.get("email");

    if (email) {
      sessionStorage.setItem(EMAIL_KEY, email.slice(0, 200));
    }

    // Came from a "Start free trial" link, so sign-up is shown even on a
    // browser that has signed in before
    if (plan || email || source) {
      sessionStorage.setItem(SIGNING_UP_KEY, "1");
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

    for (const name of ["email", "plan", "utm_source", "ref", "industry", "country"]) {
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

const HAS_ACCOUNT_KEY = "clerova.hasAccount";
const SESSION_ENDED_KEY = "clerova.sessionEnded";

/**
 * Whether to open on Sign in: a "Sign in" link (?mode=sign-in), or someone
 * who has signed in on this browser before and didn't come to sign up.
 */
export function wantsSignIn(): boolean {
  try {
    const params = new URLSearchParams(window.location.search);

    if (params.get("mode") === "sign-in") {
      return true;
    }

    const signingUp =
      params.get("mode") === "sign-up" || sessionStorage.getItem(SIGNING_UP_KEY) === "1";
    return !signingUp && localStorage.getItem(HAS_ACCOUNT_KEY) === "1";
  } catch {
    return false;
  }
}

/** Remembers that this browser has an account, so it opens on Sign in next time. */
export function rememberAccount(): void {
  try {
    localStorage.setItem(HAS_ACCOUNT_KEY, "1");
  } catch {
    // Opens on Create account instead
  }
}

/** Notes that the server ended the session, to say so on the sign-in page. */
export function noteSessionEnded(): void {
  try {
    sessionStorage.setItem(SESSION_ENDED_KEY, "1");
  } catch {
    // The sign-in page just won't explain why
  }
}

/** "Your session has ended" once, after the server ended the session. */
export function takeSessionEndedMessage(): string | null {
  try {
    if (sessionStorage.getItem(SESSION_ENDED_KEY) === "1") {
      sessionStorage.removeItem(SESSION_ENDED_KEY);
      return "Your session has ended. Please sign in again.";
    }
  } catch {
    // Nothing to show
  }

  return null;
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

/** The kind of business the visitor said they run on the website, if any. */
export function getChosenIndustry(): "PROPERTY_MANAGEMENT" | "TRUCKING" | null {
  try {
    const industry = localStorage.getItem(INDUSTRY_KEY);
    return industry === "TRUCKING" || industry === "PROPERTY_MANAGEMENT" ? industry : null;
  } catch {
    return null;
  }
}

/** Canada's time zones, from the device: no location prompt needed. */
const CANADIAN_ZONES = new Set([
  "America/Edmonton", "America/Calgary", "America/Vancouver", "America/Toronto", "America/Winnipeg",
  "America/Regina", "America/Swift_Current", "America/Halifax", "America/Moncton", "America/Glace_Bay",
  "America/Goose_Bay", "America/St_Johns", "America/Whitehorse", "America/Dawson", "America/Yellowknife",
  "America/Inuvik", "America/Cambridge_Bay", "America/Iqaluit", "America/Rankin_Inlet", "America/Resolute",
  "America/Dawson_Creek", "America/Fort_Nelson", "America/Creston", "America/Atikokan", "America/Blanc-Sablon",
  "America/Montreal", "America/Thunder_Bay", "America/Nipigon", "America/Rainy_River", "America/Pangnirtung",
]);

/**
 * Whether this looks like a Canadian carrier: a Canadian sign-up link, or a
 * phone or computer set to a Canadian time zone or English/French (Canada).
 * Only a starting guess: the sign-up box can still be changed.
 */
export function looksCanadian(): boolean {
  try {
    if (localStorage.getItem(COUNTRY_KEY) === "CA") {
      return true;
    }
  } catch {
    // Storage blocked: go by the device
  }

  try {
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;

    if (zone && CANADIAN_ZONES.has(zone)) {
      return true;
    }
  } catch {
    // No time zone
  }

  const languages = typeof navigator === "undefined" ? [] : navigator.languages ?? [navigator.language];
  return languages.some((language) => /-CA$/i.test(language ?? ""));
}

/** Once the workspace is made, so the next person on this browser starts fresh. */
export function forgetChosenIndustry(): void {
  try {
    localStorage.removeItem(INDUSTRY_KEY);
    localStorage.removeItem(COUNTRY_KEY);
  } catch {
    // Nothing saved
  }
}

/** Once checkout starts, so the next person on this browser starts fresh. */
export function forgetChosenPlan(): void {
  try {
    localStorage.removeItem(PLAN_KEY);
  } catch {
    // Nothing saved
  }
}

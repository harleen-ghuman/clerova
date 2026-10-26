// Links from the website into the app. They carry where the visitor came
// from (e.g. ?utm_source=instagram on the landing page) so the app can save
// it at sign-up, and the plan they picked on the pricing section.

const APP_URL = "https://app.clerova-ai.com/";
const SOURCE_KEY = "clerova.source";

function readSource(): string {
  try {
    const params = new URLSearchParams(window.location.search);
    const fromUrl = params.get("utm_source") ?? params.get("ref");

    if (fromUrl) {
      localStorage.setItem(SOURCE_KEY, fromUrl.slice(0, 100));
      return fromUrl;
    }

    return localStorage.getItem(SOURCE_KEY) ?? "website";
  } catch {
    return "website";
  }
}

const source = readSource();

export function signUpUrl(plan?: "starter" | "growth", email?: string): string {
  const params = new URLSearchParams({ utm_source: source });

  if (plan) {
    params.set("plan", plan);
  }

  // Prefills the sign-up form so only a password is left to choose
  if (email) {
    params.set("email", email.trim());
  }

  return `${APP_URL}?${params}`;
}

export const SIGN_IN_URL = `${APP_URL}?mode=sign-in`;

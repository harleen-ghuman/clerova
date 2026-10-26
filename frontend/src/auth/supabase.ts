import { createClient } from "@supabase/supabase-js";

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL;
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY;

export const isAuthConfigured = Boolean(supabaseUrl && supabaseAnonKey);

// Placeholder values keep the module importable when auth is not
// configured; the app shows a configuration message instead of using it.
const clientUrl = supabaseUrl || "http://localhost:54321";
const clientKey = supabaseAnonKey || "missing-anon-key";

export const supabase = createClient(clientUrl, clientKey);

/**
 * Whether Google sign-in is turned on for the project, so the button only
 * shows once it works. Null when Supabase can't be reached.
 */
export function fetchGoogleSignInEnabled(): Promise<boolean | null> {
  return fetch(`${clientUrl}/auth/v1/settings`, {
    headers: { apikey: clientKey },
  })
    .then((response) => (response.ok ? response.json() : null))
    .then((settings) => (settings ? Boolean(settings.external?.google) : null))
    .catch(() => null);
}

import { supabase } from "../auth/supabase";
import { API_BASE_URL } from "../config";
import { isDemo } from "../demo/mode";

const MAX_REPORTS = 3;
const reported = new Set<string>();

/**
 * Tells Clerova's server that a screen broke in this browser, so the team
 * hears about it without the customer having to write in. Only for signed-in
 * people, a few times per visit, and never from the demo. It never throws:
 * a failed report must not make a broken page worse.
 */
export function reportProblem(error: unknown, where: string): void {
  try {
    if (isDemo() || reported.size >= MAX_REPORTS) {
      return;
    }

    const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);

    if (reported.has(message)) {
      return;
    }

    reported.add(message);

    const details = error instanceof Error && error.stack ? error.stack.split("\n").slice(0, 6).join("\n") : "";
    const page = `${where} | ${window.location.pathname} | ${window.innerWidth}x${window.innerHeight} | ${navigator.userAgent}`;

    void supabase.auth
      .getSession()
      .then(({ data }) => {
        if (!data.session) {
          return;
        }

        return fetch(`${API_BASE_URL}/api/client-problems`, {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${data.session.access_token}` },
          body: JSON.stringify({ message: message.slice(0, 500), where: page.slice(0, 500), details: details.slice(0, 500) }),
          keepalive: true,
        });
      })
      .catch(() => {
        // Nothing more to do
      });
  } catch {
    // Nothing more to do
  }
}

/** Errors outside React's rendering, like a click handler that throws. */
export function watchForProblems(): void {
  window.addEventListener("error", (event) => {
    // Browser extensions and cross-site scripts report as "Script error." with nothing to act on
    if (!event.error || event.message === "Script error.") {
      return;
    }

    reportProblem(event.error, "window error");
  });
}

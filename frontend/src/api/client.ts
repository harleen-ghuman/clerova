import { API_BASE_URL } from "../config";
import { noteSessionEnded } from "../auth/referral";
import { supabase } from "../auth/supabase";
import { isDemo } from "../demo/mode";

export class ApiRequestError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

/**
 * Fetches a Clerova API endpoint as the signed-in user and parses the JSON
 * response. Non-2xx responses throw an ApiRequestError carrying the
 * backend's message when one is provided, so the UI can show it.
 */
export async function apiRequest<T>(
  path: string,
  options: { method?: string; body?: unknown } = {}
): Promise<T> {
  if (isDemo()) {
    // Loaded only in the demo, so the app bundle doesn't carry it
    const { handleDemoRequest } = await import("../demo/fakeApi");
    return handleDemoRequest<T>(path, options);
  }

  const { data } = await supabase.auth.getSession();
  const headers: Record<string, string> = {};

  if (data.session) {
    headers.Authorization = `Bearer ${data.session.access_token}`;
  }

  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body:
      options.body === undefined
        ? undefined
        : JSON.stringify(options.body),
  });

  if (response.status === 401 && data.session) {
    // The session is no longer valid; return to the sign-in screen.
    noteSessionEnded();
    await supabase.auth.signOut();
  }

  if (!response.ok) {
    let message = statusMessage(response.status);

    try {
      const error = await response.json();

      if (typeof error?.message === "string" && error.message) {
        message = error.message;
      }
    } catch {
      // No JSON body (e.g. the server is restarting); keep the plain message.
    }

    throw new ApiRequestError(message, response.status);
  }

  // Some successful responses have no body
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Plain words for an error response that didn't explain itself. */
function statusMessage(status: number): string {
  if (status === 401) {
    return "Your session has ended. Please sign in again.";
  }

  if (status >= 500) {
    return "Clerova is having trouble right now. Please try again in a minute.";
  }

  return "That didn't work. Refresh and try again.";
}

export function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiRequestError) {
    return err.message;
  }

  // fetch rejects with a TypeError when the network is down
  if (err instanceof TypeError) {
    return "Can't reach Clerova. Check your connection and try again.";
  }

  return fallback;
}

import { API_BASE_URL } from "../config";
import { supabase } from "../auth/supabase";

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
    await supabase.auth.signOut();
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;

    try {
      const error = await response.json();

      if (typeof error?.message === "string" && error.message) {
        message = error.message;
      }
    } catch {
      // Response had no JSON body; keep the generic message.
    }

    throw new ApiRequestError(message, response.status);
  }

  return response.json();
}

export function errorMessage(err: unknown, fallback: string): string {
  return err instanceof ApiRequestError ? err.message : fallback;
}

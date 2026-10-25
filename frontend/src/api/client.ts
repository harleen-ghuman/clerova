import { API_BASE_URL } from "../config";

export class ApiRequestError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

/**
 * Fetches a Clerova API endpoint and parses the JSON response. Non-2xx
 * responses throw an ApiRequestError carrying the backend's message when
 * one is provided, so the UI can show it.
 */
export async function apiRequest<T>(
  path: string,
  options: { method?: string; body?: unknown } = {}
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers:
      options.body === undefined
        ? undefined
        : { "Content-Type": "application/json" },
    body:
      options.body === undefined
        ? undefined
        : JSON.stringify(options.body),
  });

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

import { API_BASE_URL } from "../config";
import { supabase } from "../auth/supabase";
import { isDemo } from "../demo/mode";
import { ApiRequestError } from "./client";

/** Photos and PDFs open in the browser; other files download. */
export const VIEWABLE_TYPES = [
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "application/pdf",
];

/** A file that came with an email, as the signed-in user. */
export async function fetchAttachment(id: string): Promise<Blob> {
  if (isDemo()) {
    const { demoAttachment } = await import("../demo/fakeApi");
    return demoAttachment(id);
  }

  const { data } = await supabase.auth.getSession();
  const response = await fetch(`${API_BASE_URL}/api/attachments/${id}`, {
    headers: data.session
      ? { Authorization: `Bearer ${data.session.access_token}` }
      : {},
  });

  if (!response.ok) {
    let message =
      response.status === 404
        ? "This file isn't available."
        : "Couldn't open this file. Please try again.";

    try {
      const error = await response.json();

      if (typeof error?.message === "string" && error.message) {
        message = error.message;
      }
    } catch {
      // No JSON body; keep the plain message
    }

    throw new ApiRequestError(message, response.status);
  }

  return response.blob();
}

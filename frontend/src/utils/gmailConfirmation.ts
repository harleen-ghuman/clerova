/** What a person needs from Gmail's forwarding confirmation email. */
export interface GmailConfirmationParts {
  /** The Gmail account asking to forward, e.g. office@gmail.com. */
  from: string | null;
  /** Gmail's confirmation code, from the subject "(#123456789) Gmail Forwarding Confirmation". */
  code: string | null;
  /** The link that confirms forwarding (never the one that cancels it). */
  confirmUrl: string | null;
}

// Only Google's own confirm links; the email is untrusted text
const CONFIRM_LINK = /https:\/\/mail(?:-settings)?\.google\.com\/mail\/vf-/;

export function parseGmailConfirmation(
  subject: string | null,
  text: string | null
): GmailConfirmationParts {
  const body = text ?? "";

  const code =
    subject?.match(/\(#(\d{6,12})\)/)?.[1] ??
    body.match(/confirmation code:?\s*(\d{6,12})/i)?.[1] ??
    null;

  const from =
    body.match(/^\s*(\S+@\S+?)\s+has requested to automatically forward/m)?.[1] ??
    subject?.match(/Receive Mail from (\S+@\S+)/i)?.[1] ??
    null;

  return { from, code, confirmUrl: confirmLink(body) };
}

/** The confirm link, joined back together if the email wrapped it over lines. */
function confirmLink(body: string): string | null {
  const start = body.search(CONFIRM_LINK);

  if (start < 0) {
    return null;
  }

  // The link runs to the next blank line; wrapping only adds line breaks
  const rest = body.slice(start);
  const end = rest.search(/\n\s*\n/);
  const joined = (end < 0 ? rest : rest.slice(0, end)).replace(/\s+/g, "");
  const url = joined.match(/^[A-Za-z0-9\-._~%:/?#[\]@!$&'()*+,;=]+/)?.[0] ?? null;

  if (!url) {
    return null;
  }

  try {
    const parsed = new URL(url);
    const google = parsed.hostname === "mail-settings.google.com" || parsed.hostname === "mail.google.com";
    return google && parsed.protocol === "https:" && parsed.pathname.startsWith("/mail/vf-") ? url : null;
  } catch {
    return null;
  }
}

/** Supabase's messages, in plain words, with what to do next. */
export function friendlyError(message: string): string {
  const lower = message.toLowerCase();

  if (lower.includes("invalid login credentials")) {
    return "That email and password don't match. Try again, or reset your password.";
  }

  if (lower.includes("email not confirmed")) {
    return "Please confirm your email first. We can send the link again.";
  }

  if (lower.includes("already registered")) {
    return "There's already an account with this email. Sign in instead.";
  }

  if (lower.includes("for security purposes") || lower.includes("rate limit")) {
    return "Please wait a minute before trying again.";
  }

  return message;
}

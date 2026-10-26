/** Supabase's messages, in plain words, with what to do next. */
export function friendlyError(message: string): string {
  const lower = message.toLowerCase();

  if (lower.includes("invalid login credentials")) {
    return "That email and password don't match. Try again, or reset your password.";
  }

  if (lower.includes("email not confirmed")) {
    return "Please confirm your email first. We can send the link again.";
  }

  if (lower.includes("already registered") || lower.includes("already been registered")) {
    return "There's already an account with this email. Sign in instead.";
  }

  if (lower.includes("different from the old")) {
    return "That's already your password.";
  }

  if (lower.includes("reauthenticat")) {
    return "For your security, sign out and sign back in, then try again.";
  }

  if (lower.includes("weak") || lower.includes("pwned") || lower.includes("breach")) {
    return "Choose a stronger password. This one is too common or has appeared in a data breach.";
  }

  if (lower.includes("password should be at least")) {
    return "Use at least 8 characters.";
  }

  if (lower.includes("invalid format") || (lower.includes("email address") && lower.includes("invalid"))) {
    return "Please enter a valid email.";
  }

  if (lower.includes("session missing") || lower.includes("jwt expired")) {
    return "Your session has ended. Sign in again to continue.";
  }

  if (lower.includes("failed to fetch") || lower.includes("network")) {
    return "Can't reach Clerova. Check your connection and try again.";
  }

  if (lower.includes("for security purposes") || lower.includes("rate limit")) {
    return "Please wait a minute before trying again.";
  }

  return message;
}

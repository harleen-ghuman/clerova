/** Supabase's messages, in plain words, with what to do next. */
export function friendlyError(message: string): string {
  const lower = message.toLowerCase();

  if (lower.includes("invalid login credentials")) {
    return "That email and password don't match. Try again or reset your password. If you signed up with Google, use Continue with Google.";
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

  // Supabase lists each required group, e.g. "abc...XYZ, 0123456789" or,
  // with stricter settings, lowercase, uppercase, digits and symbols apart
  if (lower.includes("should contain at least one character")) {
    const groups = message.slice(message.indexOf(":") + 1).split(", ").map((group) => group.trim());
    const needs = [
      groups.some((group) => /^[a-z]+$/.test(group)) && "a lowercase letter",
      groups.some((group) => /^[A-Z]+$/.test(group)) && "an uppercase letter",
      groups.some((group) => /^[a-zA-Z]+$/.test(group) && /[a-z]/.test(group) && /[A-Z]/.test(group)) && "a letter",
      groups.some((group) => /^\d+$/.test(group)) && "a number",
      groups.some((group) => group !== "" && /^[^a-zA-Z\d]+$/.test(group)) && "a symbol",
    ].filter(Boolean) as string[];

    return needs.length > 0
      ? `Your password needs ${needs.length > 1 ? `${needs.slice(0, -1).join(", ")} and ${needs[needs.length - 1]}` : needs[0]}.`
      : "Use both letters and numbers in your password.";
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

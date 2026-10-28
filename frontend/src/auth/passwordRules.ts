/**
 * Mirrors the password rules set in Supabase Auth (8 characters, letters
 * and digits), so people hear about a weak password before it's sent.
 */
export const MIN_PASSWORD_LENGTH = 8;

export const PASSWORD_HINT = `At least ${MIN_PASSWORD_LENGTH} characters, with letters and numbers`;

/** What's wrong with a new password, in plain words, or null if it's fine. */
export function passwordProblem(password: string): string | null {
  if (password.length < MIN_PASSWORD_LENGTH) {
    return `Use at least ${MIN_PASSWORD_LENGTH} characters.`;
  }

  if (!/\p{L}/u.test(password) || !/\d/.test(password)) {
    return "Use both letters and numbers in your password.";
  }

  return null;
}

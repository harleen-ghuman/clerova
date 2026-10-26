import { useState, type FormEvent } from "react";

import { prefilledEmail, wantsSignIn } from "../auth/referral";
import { supabase } from "../auth/supabase";

type AuthMode =
  | "sign-in"
  | "sign-up"
  | "check-email"
  | "forgot-password"
  | "reset-password";

interface AuthPageProps {
  /** Set when the user arrived from a password reset email. */
  recoveringPassword: boolean;
  onPasswordReset: () => void;
}

const MIN_PASSWORD_LENGTH = 8;

/** Supabase's messages, in plain words, with what to do next. */
function friendlyError(message: string): string {
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

export function AuthPage({
  recoveringPassword,
  onPasswordReset,
}: AuthPageProps) {
  const [mode, setMode] = useState<AuthMode>(() =>
    recoveringPassword ? "reset-password" : wantsSignIn() ? "sign-in" : "sign-up"
  );
  const [email, setEmail] = useState(prefilledEmail);
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [resending, setResending] = useState(false);

  function switchMode(next: AuthMode) {
    setMode(next);
    setError(null);
    setNotice(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setSubmitting(true);
    setError(null);
    setNotice(null);

    try {
      if (mode === "sign-up") {
        const { data, error: signUpError } = await supabase.auth.signUp({
          email,
          password,
          options: { emailRedirectTo: window.location.origin },
        });

        if (signUpError) {
          throw signUpError;
        }

        // With email confirmation on, the user signs in by clicking the link
        if (!data.session) {
          setMode("check-email");
        }
      }

      if (mode === "sign-in") {
        const { error: signInError } =
          await supabase.auth.signInWithPassword({ email, password });

        if (signInError) {
          throw signInError;
        }
      }

      if (mode === "forgot-password") {
        const { error: resetError } =
          await supabase.auth.resetPasswordForEmail(email, {
            redirectTo: window.location.origin,
          });

        if (resetError) {
          throw resetError;
        }

        setNotice("If that email has an account, a reset link is on its way.");
      }

      if (mode === "reset-password") {
        const { error: updateError } = await supabase.auth.updateUser({
          password,
        });

        if (updateError) {
          throw updateError;
        }

        onPasswordReset();
      }
    } catch (err) {
      setError(
        err instanceof Error && err.message
          ? friendlyError(err.message)
          : "Something went wrong. Please try again."
      );
    } finally {
      setSubmitting(false);
    }
  }

  async function resendConfirmation() {
    setResending(true);
    setError(null);
    setNotice(null);

    const { error: resendError } = await supabase.auth.resend({
      type: "signup",
      email,
      options: { emailRedirectTo: window.location.origin },
    });

    setResending(false);

    if (resendError) {
      setError(friendlyError(resendError.message));
    } else {
      setNotice("Sent. Check your inbox and spam folder.");
    }
  }

  if (mode === "check-email") {
    return (
      <div className="auth-shell">
        <div className="auth-card">
          <div className="auth-brand">
            <div className="brand-mark">C</div>
            <strong>Clerova</strong>
          </div>

          <h1>Check your email</h1>
          <p className="auth-description">
            We sent a link to <strong>{email}</strong>. Click it to confirm
            your account and you'll go straight to setting up your workspace.
          </p>
          <p className="auth-description">
            It usually arrives within a minute. If not, check your spam folder.
          </p>

          {error && <p className="action-error">{error}</p>}
          {notice && <p className="request-success">{notice}</p>}

          <div className="auth-links">
            <button type="button" onClick={resendConfirmation} disabled={resending}>
              {resending ? "Sending..." : "Send the link again"}
            </button>
            <button type="button" onClick={() => switchMode("sign-up")}>
              Use a different email
            </button>
          </div>
        </div>
      </div>
    );
  }

  const copy = {
    "check-email": { title: "", description: "", submit: "" },
    "sign-up": {
      title: "Create your account",
      description:
        "Turn your property requests inbox into organized work, with replies ready for you to approve. 14 days free, and you can cancel anytime before it ends.",
      submit: "Create account",
    },
    "sign-in": {
      title: "Welcome back",
      description: "Sign in to your Clerova workspace.",
      submit: "Sign in",
    },
    "forgot-password": {
      title: "Reset your password",
      description: "We'll email you a link to choose a new password.",
      submit: "Send reset link",
    },
    "reset-password": {
      title: "Choose a new password",
      description: "Enter a new password for your account.",
      submit: "Update password",
    },
  }[mode];

  const needsEmail = mode !== "reset-password";
  const needsPassword = mode !== "forgot-password";

  return (
    <div className="auth-shell">
      <div className="auth-card">
        <div className="auth-brand">
          <div className="brand-mark">C</div>
          <strong>Clerova</strong>
        </div>

        <h1>{copy.title}</h1>
        <p className="auth-description">{copy.description}</p>

        <form className="auth-form" onSubmit={handleSubmit}>
          {needsEmail && (
            <label>
              Email
              <input
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                disabled={submitting}
              />
            </label>
          )}

          {needsPassword && (
            <label>
              Password
              <input
                type="password"
                autoComplete={
                  mode === "sign-in" ? "current-password" : "new-password"
                }
                required
                // Arriving from the website with the email filled in
                autoFocus={mode === "sign-up" && prefilledEmail() !== ""}
                minLength={mode === "sign-in" ? undefined : MIN_PASSWORD_LENGTH}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={submitting}
              />
              {mode !== "sign-in" && (
                <span className="auth-hint">
                  At least {MIN_PASSWORD_LENGTH} characters
                </span>
              )}
            </label>
          )}

          {error && <p className="action-error">{error}</p>}
          {notice && <p className="request-success">{notice}</p>}

          {mode === "sign-in" && error?.startsWith("Please confirm your email") && (
            <button
              type="button"
              className="secondary-action-button"
              onClick={resendConfirmation}
              disabled={resending}
            >
              {resending ? "Sending..." : "Send the confirmation link again"}
            </button>
          )}

          <button
            type="submit"
            className="primary-button auth-submit"
            disabled={submitting}
          >
            {submitting ? "Please wait..." : copy.submit}
          </button>

          {mode === "sign-up" && (
            <p className="auth-legal">
              By creating an account, you agree to the{" "}
              <a href="https://clerova-ai.com/terms" target="_blank" rel="noreferrer">
                Terms of Service
              </a>{" "}
              and{" "}
              <a href="https://clerova-ai.com/privacy" target="_blank" rel="noreferrer">
                Privacy Policy
              </a>
              .
            </p>
          )}
        </form>

        <div className="auth-links">
          {mode === "sign-up" && (
            <span>
              Already have an account?{" "}
              <button type="button" onClick={() => switchMode("sign-in")}>
                Sign in
              </button>
            </span>
          )}

          {mode === "sign-in" && (
            <>
              <span>
                New to Clerova?{" "}
                <button type="button" onClick={() => switchMode("sign-up")}>
                  Sign up
                </button>
              </span>

              <button
                type="button"
                onClick={() => switchMode("forgot-password")}
              >
                Forgot password?
              </button>
            </>
          )}

          {mode === "forgot-password" && (
            <button type="button" onClick={() => switchMode("sign-in")}>
              Back to sign in
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

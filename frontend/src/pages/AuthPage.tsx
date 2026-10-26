import { useState, type FormEvent } from "react";

import { supabase } from "../auth/supabase";

type AuthMode = "sign-in" | "sign-up" | "forgot-password" | "reset-password";

interface AuthPageProps {
  /** Set when the user arrived from a password reset email. */
  recoveringPassword: boolean;
  onPasswordReset: () => void;
}

const MIN_PASSWORD_LENGTH = 8;

export function AuthPage({
  recoveringPassword,
  onPasswordReset,
}: AuthPageProps) {
  const [mode, setMode] = useState<AuthMode>(
    recoveringPassword ? "reset-password" : "sign-up"
  );
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

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

        if (!data.session) {
          setNotice(
            "Check your email to confirm your account, then sign in."
          );
          setMode("sign-in");
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
          ? err.message
          : "Something went wrong. Please try again."
      );
    } finally {
      setSubmitting(false);
    }
  }

  const copy = {
    "sign-up": {
      title: "Start your free trial",
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

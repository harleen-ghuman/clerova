import { useEffect, useRef, useState } from "react";

import { friendlyError } from "../auth/friendlyError";
import { supabase } from "../auth/supabase";

// Minimal types for Google Identity Services (accounts.google.com/gsi/client)
interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleIdentity {
  initialize(options: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
    nonce: string;
    ux_mode: "popup";
    itp_support: boolean;
    use_fedcm_for_button: boolean;
  }): void;
  renderButton(
    parent: HTMLElement,
    options: {
      type: "standard";
      theme: "outline";
      size: "large";
      text: "signin_with" | "continue_with";
      shape: "rectangular";
      logo_alignment: "center";
      width: number;
    }
  ): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleIdentity } };
  }
}

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;
const SCRIPT_URL = "https://accounts.google.com/gsi/client";
const GENERIC_ERROR = "Google sign-in didn't work. Try again, or use your email.";

/** Plain words for known problems, a general message for the rest. */
function googleError(message: string): string {
  const friendly = friendlyError(message);
  return friendly === message ? GENERIC_ERROR : friendly;
}

let scriptLoading: Promise<GoogleIdentity> | null = null;

function loadGoogleIdentity(): Promise<GoogleIdentity> {
  scriptLoading ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () =>
      window.google ? resolve(window.google.accounts.id) : reject(new Error("not loaded"));
    script.onerror = () => {
      scriptLoading = null;
      reject(new Error("blocked"));
    };
    document.head.appendChild(script);
  });

  return scriptLoading;
}

/** A random nonce, and its SHA-256 hash for Google. */
async function makeNonce(): Promise<{ raw: string; hashed: string }> {
  const raw = btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(24))));
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(raw));
  const hashed = Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");

  return { raw, hashed };
}

interface GoogleSignInButtonProps {
  mode: "sign-up" | "sign-in";
  disabled: boolean;
  onError: (message: string) => void;
}

/**
 * Google's own button, so the sign-in window names Clerova's site instead
 * of the Supabase address. Falls back to Supabase's redirect sign-in when
 * no client ID is configured or Google's script can't load.
 */
export function GoogleSignInButton({ mode, disabled, onError }: GoogleSignInButtonProps) {
  const container = useRef<HTMLDivElement>(null);
  const [useFallback, setUseFallback] = useState(!CLIENT_ID);
  const [rendered, setRendered] = useState(false);

  useEffect(() => {
    if (!CLIENT_ID) {
      return;
    }

    let cancelled = false;

    Promise.all([loadGoogleIdentity(), makeNonce()])
      .then(([identity, nonce]) => {
        const parent = container.current;

        if (cancelled || !parent) {
          return;
        }

        identity.initialize({
          client_id: CLIENT_ID,
          nonce: nonce.hashed,
          ux_mode: "popup",
          itp_support: true,
          use_fedcm_for_button: true,
          callback: ({ credential }) => {
            supabase.auth
              .signInWithIdToken({ provider: "google", token: credential, nonce: nonce.raw })
              .then(({ error }) => {
                if (error) {
                  onError(googleError(error.message));
                }
              });
          },
        });

        parent.replaceChildren();

        // Google allows 200 to 400 pixels
        const width = Math.max(200, Math.min(400, Math.floor(parent.clientWidth)));

        identity.renderButton(parent, {
          type: "standard",
          theme: "outline",
          size: "large",
          text: mode === "sign-in" ? "signin_with" : "continue_with",
          shape: "rectangular",
          logo_alignment: "center",
          width,
        });
        setRendered(true);
      })
      .catch(() => {
        if (!cancelled) {
          setUseFallback(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [mode, onError]);

  async function redirectToGoogle() {
    const { error } = await supabase.auth.signInWithOAuth({
      provider: "google",
      options: { redirectTo: window.location.origin },
    });

    if (error) {
      onError(googleError(error.message));
    }
  }

  if (useFallback) {
    return (
      <button
        type="button"
        className="google-button"
        onClick={redirectToGoogle}
        disabled={disabled}
      >
        <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
          <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
          <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
          <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
          <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
        </svg>
        Continue with Google
      </button>
    );
  }

  // Google draws its button inside; the box keeps the layout steady meanwhile
  return (
    <div
      ref={container}
      className={`google-button-slot ${rendered ? "" : "loading"}`}
      aria-busy={!rendered}
    />
  );
}

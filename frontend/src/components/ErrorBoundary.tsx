import { Component, type ReactNode } from "react";

interface ErrorBoundaryState {
  failed: boolean;
}

/** A plain screen instead of a blank page if something fails to render. */
export class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: unknown) {
    console.error("Clerova failed to render:", error);
  }

  render() {
    if (!this.state.failed) {
      return this.props.children;
    }

    return (
      <div className="auth-shell" role="main">
        <div className="auth-card">
          <div className="auth-brand">
            <div className="brand-mark">C</div>
            <strong>Clerova</strong>
          </div>
          <h1>Something went wrong</h1>
          <p className="auth-description">
            This page ran into a problem. Reloading usually fixes it. Your
            requests and replies are saved.
          </p>
          <button
            type="button"
            className="primary-button auth-submit"
            onClick={() => window.location.reload()}
          >
            Reload
          </button>
          <p className="auth-description">
            Still stuck? Email hello@clerova-ai.com.
          </p>
        </div>
      </div>
    );
  }
}

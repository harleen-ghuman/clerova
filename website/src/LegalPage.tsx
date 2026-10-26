import type { ReactNode } from "react";

import "./App.css";
import "./legal.css";

interface LegalPageProps {
  title: string;
  updated: string;
  children: ReactNode;
}

/** Shared layout for the Terms and Privacy pages. */
export function LegalPage({ title, updated, children }: LegalPageProps) {
  return (
    <div className="site">
      <header className="navbar">
        <a className="brand" href="/" aria-label="Clerova home">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>
        <a className="button button-small button-dark" href="/">
          Back to home
        </a>
      </header>

      <main className="legal">
        <h1>{title}</h1>
        <p className="legal-updated">Last updated: {updated}</p>
        {children}
      </main>

      <footer className="footer">
        <a className="brand" href="/">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>
        <nav className="footer-links">
          <a href="/terms">Terms</a>
          <a href="/privacy">Privacy</a>
          <a href="mailto:hello@clerova-ai.com">hello@clerova-ai.com</a>
        </nav>
        <span>© 2026 Clerova</span>
      </footer>
    </div>
  );
}

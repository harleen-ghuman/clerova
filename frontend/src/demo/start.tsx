import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { ErrorBoundary } from "../components/ErrorBoundary";
import { DemoRoot } from "./DemoRoot";
import { startDemoMode } from "./mode";

export function startDemo(container: HTMLElement) {
  startDemoMode();

  createRoot(container).render(
    <StrictMode>
      <ErrorBoundary>
        <DemoRoot />
      </ErrorBoundary>
    </StrictMode>
  );
}

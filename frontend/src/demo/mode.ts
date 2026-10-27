/**
 * The demo at /demo runs the real app against sample data in the browser.
 * Nothing reaches the server, and nothing is sent.
 */
let demo = false;

export function startDemoMode() {
  demo = true;
}

export function isDemo(): boolean {
  return demo;
}

/** Asks the app to reload its lists, e.g. after a simulated email. */
export const REFRESH_EVENT = "clerova:refresh";

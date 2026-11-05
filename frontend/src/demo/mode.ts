/**
 * The demo at /demo runs the real app against sample data in the browser.
 * Nothing reaches the server, and nothing is sent.
 */
let demo = false;
let trucking = false;

/** The trucking demo lives at /demo/trucking; /demo is property management. */
export function startDemoMode(industry: "PROPERTY_MANAGEMENT" | "TRUCKING" = "PROPERTY_MANAGEMENT") {
  demo = true;
  trucking = industry === "TRUCKING";
}

export function isDemo(): boolean {
  return demo;
}

export function isTruckingDemo(): boolean {
  return demo && trucking;
}

/** Which demo a path asks for, or null when it isn't a demo path. */
export function demoIndustry(pathname: string): "PROPERTY_MANAGEMENT" | "TRUCKING" | null {
  if (/^\/demo\/trucking\/?$/.test(pathname)) return "TRUCKING";
  if (/^\/demo\/?$/.test(pathname)) return "PROPERTY_MANAGEMENT";
  return null;
}

/** Asks the app to reload its lists, e.g. after a simulated email. */
export const REFRESH_EVENT = "clerova:refresh";

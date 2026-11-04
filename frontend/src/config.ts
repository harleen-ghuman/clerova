export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ??
  "http://localhost:8080";
/** Trucking workspaces can be chosen at sign-up (the API must allow it too). */
export const TRUCKING_ENABLED = import.meta.env.VITE_TRUCKING_ENABLED === "true";

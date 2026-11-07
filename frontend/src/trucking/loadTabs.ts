/** The load page's tabs, and a way for any panel on it to switch tab. */
export type LoadTab = "overview" | "documents" | "emails";

/** How to open a load: on a tab, or at the rate con check (highlighted). */
export interface OpenLoadOptions {
  tab?: LoadTab;
  checks?: boolean;
}

const EVENT = "clerova:load-tab";

/** Switches the open load page to a tab, like "See Emails" from a stop. */
export function showLoadTab(tab: LoadTab) {
  window.dispatchEvent(new CustomEvent<LoadTab>(EVENT, { detail: tab }));
}

/** Calls handler when a panel asks for a tab; returns the unsubscribe. */
export function onLoadTab(handler: (tab: LoadTab) => void): () => void {
  const listener = (event: Event) => handler((event as CustomEvent<LoadTab>).detail);
  window.addEventListener(EVENT, listener);
  return () => window.removeEventListener(EVENT, listener);
}

/** Scrolls to a section and briefly highlights it, so the eye lands on it. */
export function flashSection(id: string) {
  const section = document.getElementById(id);

  if (!section) {
    return;
  }

  section.scrollIntoView({ behavior: "smooth", block: "start" });
  section.classList.remove("tk-flash");
  // Restart the highlight if it was already playing
  void section.offsetWidth;
  section.classList.add("tk-flash");
  window.setTimeout(() => section.classList.remove("tk-flash"), 2600);
}

import { useEffect, useRef, useState } from "react";

import { getLoads } from "../api/loads";
import { getEmailSettings } from "../api/email";
import { getMe } from "../api/account";
import { errorMessage } from "../api/client";
import { REFRESH_EVENT } from "../demo/mode";
import { Sidebar, type AppView, type SidebarItem } from "../components/Sidebar";
import { InboxPage } from "../pages/InboxPage";
import { EmailSetupPage } from "../pages/EmailSetupPage";
import { SettingsPage } from "../pages/SettingsPage";
import type { EmailSettings } from "../types/Email";
import type { Me, Organization } from "../types/Account";
import type { LoadSummary } from "../types/Load";
import { TodayView } from "./TodayView";
import { LoadsBoard } from "./LoadsBoard";
import { LoadView } from "./LoadView";
import { BrokersView } from "./BrokersView";
import "./trucking.css";

interface TruckingAppProps {
  organization: Organization;
  userEmail: string;
  fullName: string | null;
  onAccountUpdated: (me: Me) => void;
  onSignOut: () => void;
}

/** What the browser history remembers for each screen. */
interface NavState {
  clerova: true;
  view: AppView;
  selectedId: string | null;
}

const VIEWS: AppView[] = ["today", "loads", "brokers", "inbox", "email-setup", "settings"];

/** Loads that need the dispatcher: things to check or a reply to approve. */
function needsYou(load: LoadSummary): boolean {
  return (load.status === "BOOKED" && load.openFlags > 0) || load.draftReplies > 0;
}

/** The app for a trucking workspace: loads instead of requests and jobs. */
export function TruckingApp({
  organization: initialOrganization,
  userEmail,
  fullName,
  onAccountUpdated,
  onSignOut,
}: TruckingAppProps) {
  const [organization, setOrganization] = useState(initialOrganization);
  const [restoredNav] = useState<NavState | null>(() => {
    const state = window.history.state as NavState | null;
    return state?.clerova && VIEWS.includes(state.view) ? state : null;
  });
  const [activeView, setActiveView] = useState<AppView>(restoredNav?.view ?? "today");
  const [selectedId, setSelectedId] = useState<string | null>(restoredNav?.selectedId ?? null);
  const [loads, setLoads] = useState<LoadSummary[]>([]);
  const [loadsLoading, setLoadsLoading] = useState(true);
  const [loadsError, setLoadsError] = useState<string | null>(null);
  const [emailSettings, setEmailSettings] = useState<EmailSettings | null>(null);
  const [emailSettingsError, setEmailSettingsError] = useState<string | null>(null);
  const [refreshTick, setRefreshTick] = useState(0);
  const loadsLoaded = useRef(false);
  const accountUpdated = useRef(onAccountUpdated);

  useEffect(() => {
    accountUpdated.current = onAccountUpdated;
  }, [onAccountUpdated]);

  function loadLoads() {
    return getLoads()
      .then((result) => {
        loadsLoaded.current = true;
        setLoads(result);
        setLoadsError(null);
      })
      .catch((err) => {
        console.error("Failed to load loads:", err);

        if (!loadsLoaded.current) {
          setLoadsError(errorMessage(err, "Couldn't load your loads. Refresh to try again."));
        }
      })
      .finally(() => setLoadsLoading(false));
  }

  function loadEmailSettings() {
    return getEmailSettings()
      .then((settings) => {
        setEmailSettings(settings);
        setEmailSettingsError(null);
      })
      .catch((err) =>
        setEmailSettingsError(errorMessage(err, "Couldn't load your email settings. Refresh to try again."))
      );
  }

  function loadOrganization() {
    return getMe()
      .then((me) => {
        if (me.organization) {
          setOrganization(me.organization);
        }
        accountUpdated.current(me);
      })
      .catch((err) => console.error("Failed to refresh the account:", err));
  }

  useEffect(() => {
    void loadLoads();
    void loadEmailSettings();
  }, []);

  // Emails are read in the background, so check for new loads regularly
  useEffect(() => {
    function refresh() {
      void loadLoads();
      void loadOrganization();
      setRefreshTick((tick) => tick + 1);
    }

    const interval = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        refresh();
      }
    }, 30_000);

    window.addEventListener(REFRESH_EVENT, refresh);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener(REFRESH_EVENT, refresh);
    };
  }, []);

  /** Shows a screen and records it, so the phone's Back button works. */
  function show(view: AppView, loadId: string | null = null) {
    setActiveView(view);
    setSelectedId(loadId);

    const current = window.history.state as NavState | null;

    if (current?.clerova && current.view === view && current.selectedId === loadId) {
      return;
    }

    window.history.pushState({ clerova: true, view, selectedId: loadId } satisfies NavState, "");
  }

  useEffect(() => {
    if (!restoredNav) {
      window.history.replaceState({ clerova: true, view: "today", selectedId: null } satisfies NavState, "");
    }

    function onPopState(event: PopStateEvent) {
      const state = event.state as NavState | null;

      if (state?.clerova && VIEWS.includes(state.view)) {
        setActiveView(state.view);
        setSelectedId(state.selectedId);
      }
    }

    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [restoredNav]);

  function navigate(view: AppView) {
    if (view === "email-setup") {
      void loadEmailSettings();
    }

    show(view);
  }

  function openLoad(id: string) {
    show("loads", id);
  }

  function closeLoad() {
    const current = window.history.state as NavState | null;

    // Step back to where the load was opened from, so Back doesn't loop
    if (current?.clerova && current.selectedId && window.history.length > 1) {
      window.history.back();
      return;
    }

    show("loads");
  }

  const attention = loads.filter(needsYou).length;
  const items: SidebarItem[] = [
    { view: "today", label: "Today" },
    { view: "loads", label: "Loads", count: attention, badge: true },
    { view: "brokers", label: "Brokers" },
    { view: "inbox", label: "Inbox" },
    { view: "email-setup", label: "Email setup" },
    { view: "settings", label: "Settings" },
  ];
  const forwardingAddress = emailSettings?.intakeEnabled ? emailSettings.forwardingAddress : null;

  return (
    <div className="app-shell">
      <Sidebar
        activeView={activeView}
        reviewCount={0}
        openJobCount={0}
        organization={organization}
        userEmail={userEmail}
        onNavigate={navigate}
        onSignOut={onSignOut}
        onManageBilling={() => navigate("settings")}
        items={items}
        usageLabel="rate cons"
        showBilling={false}
      />

      <nav className="mobile-nav" aria-label="Mobile navigation">
        {items.map((item) => (
          <button
            key={item.view}
            type="button"
            className={activeView === item.view ? "active" : ""}
            onClick={() => navigate(item.view)}
          >
            {item.label}
            {item.count !== undefined && item.count > 0 && (
              <span className="mobile-nav-count">{item.count}</span>
            )}
          </button>
        ))}
      </nav>

      <main className="app-main">
        {activeView === "today" && (
          <TodayView
            loads={loads}
            loading={loadsLoading}
            error={loadsError}
            forwardingAddress={forwardingAddress}
            onOpenLoad={openLoad}
            onOpenEmailSetup={() => navigate("email-setup")}
          />
        )}

        {activeView === "loads" &&
          (selectedId ? (
            <LoadView
              key={selectedId}
              loadId={selectedId}
              sendingEnabled={emailSettings?.sendingEnabled ?? false}
              onBack={closeLoad}
              onChanged={() => void loadLoads()}
            />
          ) : (
            <LoadsBoard loads={loads} loading={loadsLoading} error={loadsError} onOpenLoad={openLoad} />
          ))}

        {activeView === "brokers" && <BrokersView refreshKey={refreshTick} />}

        {activeView === "inbox" && (
          <div className="page-padded">
            <InboxPage
              refreshKey={refreshTick}
              onOpenWorkItem={() => navigate("loads")}
              onOpenEmailSetup={() => navigate("email-setup")}
              onOpenLoad={openLoad}
            />
          </div>
        )}

        {activeView === "email-setup" && (
          <div className="page-padded">
            <EmailSetupPage
              settings={emailSettings}
              error={emailSettingsError}
              userEmail={userEmail}
              onSettingsChanged={setEmailSettings}
              trucking
            />
          </div>
        )}

        {activeView === "settings" && (
          <div className="page-padded">
            <SettingsPage
              email={userEmail}
              fullName={fullName}
              organization={organization}
              onSaved={(me) => {
                if (me.organization) {
                  setOrganization(me.organization);
                }
                onAccountUpdated(me);
              }}
              onManageBilling={() => undefined}
              upgradeQuestion=""
              onBillingChanged={loadOrganization}
              onUpgrade={() => undefined}
              upgrading={false}
              onSignOut={onSignOut}
              propertyManagement={false}
            />
          </div>
        )}
      </main>
    </div>
  );
}

import { useEffect, useRef, useState } from "react";

import {
  createWorkItem,
  getClosedWorkItems,
  getWorkItem,
  getWorkItems,
} from "./api/workItems";
import { getMaintenanceRequests } from "./api/MaintenanceRequests";
import { getEmailSettings } from "./api/email";
import { errorMessage } from "./api/client";
import { keepPlan, openBillingPortal, upgradeToGrowth } from "./api/billing";
import { REFRESH_EVENT } from "./demo/mode";
import { PLANS, planPrice } from "./utils/plans";
import { getMe } from "./api/account";
import { Sidebar, type AppView } from "./components/Sidebar";
import { RequestsPage, type RequestFilter } from "./pages/RequestsPage";
import { TodayPage } from "./pages/TodayPage";
import { JobsPage } from "./pages/JobsPage";
import { InboxPage } from "./pages/InboxPage";
import { EmailSetupPage } from "./pages/EmailSetupPage";
import { SettingsPage } from "./pages/SettingsPage";
import type { EmailSettings } from "./types/Email";
import type { Me, Organization } from "./types/Account";
import type { MaintenanceRequest } from "./types/MaintenanceRequest";
import type { WorkItem } from "./types/WorkItem";
import { hasNewReply, requestBucket } from "./utils/requestStatus";

interface AppProps {
  /** Just came back from checkout. */
  welcome?: boolean;
  /** As loaded at sign-in; usage and billing refresh in the background. */
  organization: Organization;
  userEmail: string;
  fullName: string | null;
  /** Saved on the Settings page. */
  onAccountUpdated: (me: Me) => void;
  onSignOut: () => void;
}

/** What the browser history remembers for each screen. */
interface NavState {
  clerova: true;
  view: AppView;
  selectedId: string | null;
  /** The request was opened from the Requests list (not Today or Jobs). */
  fromList?: boolean;
}

const MOBILE_NAV: { view: AppView; label: string }[] = [
  { view: "today", label: "Today" },
  { view: "requests", label: "Requests" },
  { view: "jobs", label: "Jobs" },
  { view: "inbox", label: "Inbox" },
  { view: "email-setup", label: "Email setup" },
  { view: "settings", label: "Settings" },
];

function App({
  welcome = false,
  organization: initialOrganization,
  userEmail,
  fullName,
  onAccountUpdated,
  onSignOut,
}: AppProps) {
  const [organization, setOrganization] = useState(initialOrganization);
  const [activeView, setActiveView] = useState<AppView>("today");
  const [requestFilter, setRequestFilter] = useState<RequestFilter>("review");
  // Opened from Today to send a reminder: the request starts with a draft
  const [remindFor, setRemindFor] = useState<string | null>(null);

  const [openRequests, setOpenRequests] = useState<WorkItem[]>([]);
  const [closedRequests, setClosedRequests] = useState<WorkItem[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(true);
  const [requestsError, setRequestsError] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  // Used when the selected request isn't in the lists, e.g. an older one
  const [selectedFallback, setSelectedFallback] = useState<WorkItem | null>(null);

  const [jobs, setJobs] = useState<MaintenanceRequest[]>([]);
  const [jobsLoading, setJobsLoading] = useState(true);
  const [jobsError, setJobsError] = useState<string | null>(null);
  const [highlightJobFor, setHighlightJobFor] = useState<string | null>(null);

  const [emailSettings, setEmailSettings] = useState<EmailSettings | null>(null);
  const [emailSettingsError, setEmailSettingsError] = useState<string | null>(null);

  // Bumped by the background refresh so pages reload their data
  const [refreshTick, setRefreshTick] = useState(0);

  // Once data is on screen, a failed background refresh keeps it instead of
  // replacing it with an error
  const requestsLoaded = useRef(false);
  const jobsLoaded = useRef(false);

  // Read by the background refresh, which is set up once
  const accountUpdated = useRef(onAccountUpdated);

  useEffect(() => {
    accountUpdated.current = onAccountUpdated;
  }, [onAccountUpdated]);

  function loadRequests() {
    return Promise.all([getWorkItems(), getClosedWorkItems()])
      .then(([open, closed]) => {
        requestsLoaded.current = true;
        setOpenRequests(open);
        setClosedRequests(closed);
        setRequestsError(null);
      })
      .catch((err) => {
        console.error("Failed to load requests:", err);

        if (!requestsLoaded.current) {
          setRequestsError(errorMessage(err, "Couldn't load your requests. Refresh to try again."));
        }
      })
      .finally(() => setRequestsLoading(false));
  }

  function loadJobs() {
    return getMaintenanceRequests()
      .then((data) => {
        jobsLoaded.current = true;
        setJobs(data);
        setJobsError(null);
      })
      .catch((err) => {
        console.error("Failed to load jobs:", err);

        if (!jobsLoaded.current) {
          setJobsError(errorMessage(err, "Couldn't load your jobs. Refresh to try again."));
        }
      })
      .finally(() => setJobsLoading(false));
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

        // Lets Root show the plan page if the plan ended meanwhile
        accountUpdated.current(me);
      })
      .catch((err) => console.error("Failed to refresh the account:", err));
  }

  useEffect(() => {
    // State is only set in promise callbacks, never synchronously here.
    void loadRequests();
    void loadJobs();
    void loadEmailSettings();
  }, []);

  // New emails are processed in the background, so check for new work
  // regularly while the tab is visible.
  useEffect(() => {
    function refresh() {
      void loadRequests();
      void loadJobs();
      void loadOrganization();
      setRefreshTick((tick) => tick + 1);
    }

    const interval = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        refresh();
      }
    }, 30_000);

    // The demo asks for a refresh after simulating an email
    window.addEventListener(REFRESH_EVENT, refresh);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener(REFRESH_EVENT, refresh);
    };
  }, []);

  const requests = [...openRequests, ...closedRequests];
  const selected =
    requests.find((item) => item.id === selectedId) ??
    (selectedFallback?.id === selectedId ? selectedFallback : null);

  // Requests needing a person: new ones, and new emails during a job
  const reviewCount = openRequests.filter(
    (item) => requestBucket(item) === "review" || hasNewReply(item)
  ).length;
  const openJobCount = jobs.filter(
    (job) => job.status === "OPEN" || job.status === "IN_PROGRESS"
  ).length;

  /**
   * Shows a screen and records it in the browser history, so the phone's
   * Back button returns to the previous screen instead of leaving Clerova.
   */
  function show(view: AppView, workItemId: string | null = null, fromList = false) {
    setActiveView(view);
    setSelectedId(workItemId);
    setRemindFor(null);

    const current = window.history.state as NavState | null;

    if (current?.clerova && current.view === view && current.selectedId === workItemId) {
      return;
    }

    const state: NavState = { clerova: true, view, selectedId: workItemId, fromList };
    window.history.pushState(state, "");
  }

  useEffect(() => {
    // The first screen, so Back from the next one comes back here
    const initial: NavState = { clerova: true, view: "today", selectedId: null };
    window.history.replaceState(initial, "");

    function onPopState(event: PopStateEvent) {
      const state = event.state as NavState | null;

      if (state?.clerova) {
        setActiveView(state.view);
        setSelectedId(state.selectedId);
        setHighlightJobFor(null);
        setRemindFor(null);
      }
    }

    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, []);

  function navigate(view: AppView) {
    if (view === "email-setup") {
      // A Gmail confirmation email may have arrived since the last load
      void loadEmailSettings();
    }

    if (view !== "jobs") {
      setHighlightJobFor(null);
    }

    show(view, view === "requests" ? selectedId : null);
  }

  function selectRequest(item: WorkItem | null) {
    setSelectedFallback(item);

    if (item) {
      show("requests", item.id, true);
      return;
    }

    const current = window.history.state as NavState | null;

    // Opened from the list: step back to it, so Back doesn't loop
    if (current?.clerova && current.selectedId && current.fromList) {
      window.history.back();
      return;
    }

    // Opened from Today or Jobs: show the list here, without leaving Requests
    setSelectedId(null);
    const list: NavState = { clerova: true, view: "requests", selectedId: null };
    window.history.replaceState(list, "");
  }

  async function openRequest(workItemId: string) {
    show("requests", workItemId);

    if (!requests.some((item) => item.id === workItemId)) {
      try {
        setSelectedFallback(await getWorkItem(workItemId));
      } catch (err) {
        console.error("Failed to open request:", err);
        setSelectedId(null);
      }
    }
  }

  async function handleCreate(message: string) {
    const created = await createWorkItem(message);
    await loadRequests();
    return created;
  }

  const [billingError, setBillingError] = useState<string | null>(null);

  function manageBilling() {
    setBillingError(null);
    openBillingPortal()
      .then(({ url }) => window.location.assign(url))
      .catch((err) =>
        setBillingError(errorMessage(err, "Couldn't open billing. Please try again."))
      );
  }

  const planEnds = organization.currentPeriodEnd
    ? new Date(organization.currentPeriodEnd).toLocaleDateString([], {
        month: "long",
        day: "numeric",
      })
    : null;

  const billingNotice =
    organization.subscriptionStatus === "past_due"
      ? "Your last payment didn't go through. Update your card to keep Clerova running."
      : organization.cancelAtPeriodEnd && planEnds
        ? organization.subscriptionStatus === "trialing"
          ? `Your plan is cancelled. Your trial ends on ${planEnds} and you won't be charged.`
          : `Your plan is cancelled. You can keep using Clerova until ${planEnds}.`
        : null;

  function handleChanged() {
    void loadRequests();
    void loadJobs();
    void loadOrganization();
  }

  const [upgrading, setUpgrading] = useState(false);
  const [keeping, setKeeping] = useState(false);

  /** Undoes a scheduled cancellation, in one click. */
  function keep() {
    setKeeping(true);
    setBillingError(null);
    keepPlan()
      .then(() => loadOrganization())
      .catch((err) =>
        setBillingError(errorMessage(err, "Couldn't keep your plan. Please try again."))
      )
      .finally(() => setKeeping(false));
  }

  function upgrade() {
    const growth = PLANS.find((item) => item.plan === "GROWTH")!;
    const price = planPrice(
      organization.foundingCustomer ? growth.foundingMonthly : growth.monthly,
      organization.billingInterval === "year" ? "year" : "month"
    );
    const when =
      organization.subscriptionStatus === "trialing"
        ? "You won't be charged until your trial ends."
        : "The difference for the rest of this billing period is added to your next bill.";

    if (
      !window.confirm(
        `Upgrade to Growth for $${price.amount.toLocaleString()} / ${price.per}? You get ${growth.requests.toLocaleString()} requests a month starting now. ${when}${organization.cancelAtPeriodEnd ? " Your plan will also stop being cancelled." : ""}`
      )
    ) {
      return;
    }

    setUpgrading(true);
    setBillingError(null);
    upgradeToGrowth()
      .then(() => loadOrganization())
      .catch((err) =>
        setBillingError(errorMessage(err, "Couldn't upgrade. Please try again."))
      )
      .finally(() => setUpgrading(false));
  }

  // Warn from 80% of the monthly requests, and suggest upgrading
  const used = organization.aiRequestsUsedThisMonth;
  const limit = organization.aiMonthlyLimit;
  const usageLevel =
    organization.plan === "INTERNAL" || limit <= 0
      ? null
      : used >= limit
        ? "limit"
        : used >= Math.ceil(limit * 0.8)
          ? "warning"
          : null;
  const canUpgrade =
    organization.plan === "STARTER" &&
    organization.billingEnabled &&
    organization.subscriptionStatus !== null;
  const resetDate = new Date(
    Date.UTC(new Date().getUTCFullYear(), new Date().getUTCMonth() + 1, 1)
  ).toLocaleDateString([], { month: "long", day: "numeric", timeZone: "UTC" });
  const usageNotice =
    usageLevel === "limit"
      ? `You've used all ${limit.toLocaleString()} requests this month. New emails are saved and will be organized on ${resetDate}${canUpgrade ? ", or as soon as you upgrade" : ""}.`
      : usageLevel === "warning"
        ? `You've used ${used} of ${limit} requests this month.`
        : null;

  return (
    <div className="app-shell">
      <Sidebar
        activeView={activeView}
        reviewCount={reviewCount}
        openJobCount={openJobCount}
        organization={organization}
        userEmail={userEmail}
        onNavigate={navigate}
        onSignOut={onSignOut}
        onManageBilling={() => navigate("settings")}
      />

      <nav className="mobile-nav" aria-label="Mobile navigation">
        {MOBILE_NAV.map((item) => (
          <button
            key={item.view}
            type="button"
            className={activeView === item.view ? "active" : ""}
            onClick={() => navigate(item.view)}
          >
            {item.label}
            {item.view === "requests" && reviewCount > 0 && (
              <span className="mobile-nav-count">{reviewCount}</span>
            )}
          </button>
        ))}
      </nav>

      <main className="app-main">
        {(billingNotice || billingError) && (
          <div className="billing-banner" role="status">
            <span>{billingError ?? billingNotice}</span>
            {billingError && (
              <button
                type="button"
                className="rq-button rq-button-small rq-button-quiet"
                onClick={() => setBillingError(null)}
              >
                Dismiss
              </button>
            )}
            {!billingError && organization.subscriptionStatus === "past_due" && (
              <button type="button" className="rq-button rq-button-small" onClick={manageBilling}>
                Update card
              </button>
            )}
            {!billingError && organization.cancelAtPeriodEnd && (
              <button
                type="button"
                className="rq-button rq-button-small"
                onClick={keep}
                disabled={keeping}
              >
                {keeping ? "Keeping your plan..." : "Keep my plan"}
              </button>
            )}
          </div>
        )}

        {usageNotice && (
          <div
            className={`billing-banner ${usageLevel === "limit" ? "billing-banner-limit" : ""}`}
            role="status"
          >
            <span>
              {usageNotice}{" "}
              {canUpgrade
                ? "Growth includes 1,500 requests a month."
                : usageLevel === "limit" && "Email hello@clerova-ai.com if you need more."}
            </span>
            {canUpgrade && (
              <button
                type="button"
                className="rq-button rq-button-small rq-button-primary"
                onClick={upgrade}
                disabled={upgrading}
              >
                {upgrading ? "Upgrading..." : "Upgrade to Growth"}
              </button>
            )}
          </div>
        )}

        {activeView === "today" && (
          <TodayPage
            organizationName={organization.name}
            welcome={welcome}
            forwardingAddress={
              emailSettings?.intakeEnabled ? emailSettings.forwardingAddress : null
            }
            hasAnyRequest={openRequests.length + closedRequests.length > 0}
            onOpenEmailSetup={() => navigate("email-setup")}
            requests={openRequests}
            jobs={jobs}
            refreshKey={refreshTick}
            onOpenRequests={(filter) => {
              setRequestFilter(filter);
              setSelectedFallback(null);
              show("requests");
            }}
            onOpenRequest={(workItemId) => {
              setRequestFilter("all");
              void openRequest(workItemId);
            }}
            canRemind={emailSettings?.sendingEnabled ?? false}
            onRemind={(workItemId) => {
              setRequestFilter("all");
              void openRequest(workItemId);
              setRemindFor(workItemId);
            }}
            onOpenJobs={() => navigate("jobs")}
          />
        )}

        {activeView === "requests" && (
          <RequestsPage
            requests={requests}
            loading={requestsLoading}
            error={requestsError}
            selected={selected}
            filter={requestFilter}
            onFilterChange={setRequestFilter}
            emailSendingEnabled={emailSettings?.sendingEnabled ?? false}
            remindFor={remindFor}
            onSelect={selectRequest}
            onCreate={handleCreate}
            onChanged={handleChanged}
            onOpenJobs={(workItemId) => {
              setHighlightJobFor(workItemId);
              show("jobs");
            }}
          />
        )}

        {activeView === "jobs" && (
          <JobsPage
            jobs={jobs}
            requests={requests}
            loading={jobsLoading}
            error={jobsError}
            highlightWorkItemId={highlightJobFor}
            onJobUpdated={(updated) =>
              setJobs((current) =>
                current.map((job) => (job.id === updated.id ? updated : job))
              )
            }
            onRequestClosed={() => void loadRequests()}
            onOpenRequest={openRequest}
          />
        )}

        {activeView === "inbox" && (
          <div className="page-padded">
            <InboxPage
              refreshKey={refreshTick}
              onOpenWorkItem={openRequest}
              onOpenEmailSetup={() => navigate("email-setup")}
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
              onManageBilling={manageBilling}
              onBillingChanged={loadOrganization}
              onUpgrade={upgrade}
              upgrading={upgrading}
              onSignOut={onSignOut}
            />
          </div>
        )}
      </main>
    </div>
  );
}

export default App;

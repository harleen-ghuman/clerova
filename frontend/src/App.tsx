import { useEffect, useState } from "react";

import {
  createWorkItem,
  getClosedWorkItems,
  getWorkItem,
  getWorkItems,
} from "./api/workItems";
import { getMaintenanceRequests } from "./api/MaintenanceRequests";
import { getEmailSettings } from "./api/email";
import { errorMessage } from "./api/client";
import { Sidebar, type AppView } from "./components/Sidebar";
import { RequestsPage } from "./pages/RequestsPage";
import { JobsPage } from "./pages/JobsPage";
import { InboxPage } from "./pages/InboxPage";
import { EmailSetupPage } from "./pages/EmailSetupPage";
import type { EmailSettings } from "./types/Email";
import type { Organization } from "./types/Account";
import type { MaintenanceRequest } from "./types/MaintenanceRequest";
import type { WorkItem } from "./types/WorkItem";
import { requestBucket } from "./utils/requestStatus";

interface AppProps {
  organization: Organization;
  userEmail: string;
  onSignOut: () => void;
}

const MOBILE_NAV: { view: AppView; label: string }[] = [
  { view: "requests", label: "Requests" },
  { view: "jobs", label: "Jobs" },
  { view: "inbox", label: "Inbox" },
  { view: "email-setup", label: "Email setup" },
];

function App({ organization, userEmail, onSignOut }: AppProps) {
  const [activeView, setActiveView] = useState<AppView>("requests");

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

  function loadRequests() {
    return Promise.all([getWorkItems(), getClosedWorkItems()])
      .then(([open, closed]) => {
        setOpenRequests(open);
        setClosedRequests(closed);
        setRequestsError(null);
      })
      .catch((err) => {
        console.error("Failed to load requests:", err);
        setRequestsError(errorMessage(err, "Unable to load requests."));
      })
      .finally(() => setRequestsLoading(false));
  }

  function loadJobs() {
    return getMaintenanceRequests()
      .then((data) => {
        setJobs(data);
        setJobsError(null);
      })
      .catch((err) => {
        console.error("Failed to load jobs:", err);
        setJobsError(errorMessage(err, "Unable to load jobs."));
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
        setEmailSettingsError(errorMessage(err, "Unable to load email settings."))
      );
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
    const interval = window.setInterval(() => {
      if (document.visibilityState !== "visible") {
        return;
      }

      void loadRequests();
      void loadJobs();
      setRefreshTick((tick) => tick + 1);
    }, 30_000);

    return () => window.clearInterval(interval);
  }, []);

  const requests = [...openRequests, ...closedRequests];
  const selected =
    requests.find((item) => item.id === selectedId) ??
    (selectedFallback?.id === selectedId ? selectedFallback : null);

  const reviewCount = openRequests.filter(
    (item) => requestBucket(item) === "review"
  ).length;
  const openJobCount = jobs.filter(
    (job) => job.status === "OPEN" || job.status === "IN_PROGRESS"
  ).length;

  function navigate(view: AppView) {
    if (view === "email-setup") {
      // A Gmail confirmation email may have arrived since the last load
      void loadEmailSettings();
    }

    if (view !== "jobs") {
      setHighlightJobFor(null);
    }

    setActiveView(view);
  }

  function selectRequest(item: WorkItem | null) {
    setSelectedId(item?.id ?? null);
    setSelectedFallback(item);
  }

  async function openRequest(workItemId: string) {
    setActiveView("requests");
    setSelectedId(workItemId);

    if (!requests.some((item) => item.id === workItemId)) {
      try {
        setSelectedFallback(await getWorkItem(workItemId));
      } catch (err) {
        console.error("Failed to open request:", err);
      }
    }
  }

  async function handleCreate(message: string) {
    const created = await createWorkItem(message);
    await loadRequests();
    return created;
  }

  function handleChanged() {
    void loadRequests();
    void loadJobs();
  }

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
        {activeView === "requests" && (
          <RequestsPage
            requests={requests}
            loading={requestsLoading}
            error={requestsError}
            selected={selected}
            emailSendingEnabled={emailSettings?.sendingEnabled ?? false}
            onSelect={selectRequest}
            onCreate={handleCreate}
            onChanged={handleChanged}
            onOpenJobs={(workItemId) => {
              setHighlightJobFor(workItemId);
              setActiveView("jobs");
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
            <EmailSetupPage settings={emailSettings} error={emailSettingsError} />
          </div>
        )}
      </main>
    </div>
  );
}

export default App;

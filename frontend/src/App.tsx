import { useEffect, useState } from "react";

import {
  approveProposedAction,
  closeWorkItem,
  createWorkItem,
  generateProposedAction,
  getLatestProposedActions,
  getProposedActions,
  getWorkItem,
  getWorkItems,
  rejectProposedAction,
} from "./api/workItems";
import { getMaintenanceRequests } from "./api/MaintenanceRequests";
import { getEmailSettings } from "./api/email";
import { errorMessage } from "./api/client";
import { PendingActionsPage } from "./pages/PendingActionsPage";
import { Sidebar, type AppView } from "./components/Sidebar";
import { WorkItemsPage } from "./pages/WorkItemsPage";
import { DashboardPage } from "./pages/DashboardPage";
import { MaintenancePage } from "./pages/MaintenancePage";
import { InboxPage } from "./pages/InboxPage";
import { EmailSetupPage } from "./pages/EmailSetupPage";
import type { EmailSettings } from "./types/Email";
import type { Organization } from "./types/Account";
import type { MaintenanceRequest } from "./types/MaintenanceRequest";
import type { ProposedAction } from "./types/ProposedAction";
import type { WorkItem } from "./types/WorkItem";

function byNewest(a: { createdAt: string }, b: { createdAt: string }) {
  return (
    new Date(b.createdAt).getTime() -
    new Date(a.createdAt).getTime()
  );
}

interface AppProps {
  organization: Organization;
  userEmail: string;
  onSignOut: () => void;
}

function App({ organization, userEmail, onSignOut }: AppProps) {
  const [workItems, setWorkItems] = useState<WorkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeView, setActiveView] =
    useState<AppView>("dashboard");

  const [selectedWorkItem, setSelectedWorkItem] =
    useState<WorkItem | null>(null);

  const [proposedAction, setProposedAction] =
    useState<ProposedAction | null>(null);

  const [generatingAction, setGeneratingAction] = useState(false);
  const [updatingAction, setUpdatingAction] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const [maintenanceRequests, setMaintenanceRequests] = useState<
    MaintenanceRequest[]
  >([]);

  const [maintenanceLoading, setMaintenanceLoading] = useState(true);

  const [maintenanceError, setMaintenanceError] =
    useState<string | null>(null);

  const [selectedMaintenanceWorkItemId, setSelectedMaintenanceWorkItemId] =
    useState<string | null>(null);

  const [newRequestMessage, setNewRequestMessage] = useState("");
  const [creatingRequest, setCreatingRequest] = useState(false);

  const [createRequestError, setCreateRequestError] =
    useState<string | null>(null);

  const [createRequestSuccess, setCreateRequestSuccess] =
    useState<string | null>(null);

  const [latestActions, setLatestActions] = useState<
    Record<string, ProposedAction>
  >({});

  const [selectedPendingAction, setSelectedPendingAction] =
    useState<ProposedAction | null>(null);

  const [pendingDecision, setPendingDecision] = useState<{
    action: ProposedAction;
    decision: "APPROVED" | "REJECTED";
    // Snapshot, since approving can close the item and drop it from the list
    workItem?: WorkItem;
  } | null>(null);

  const [emailSettings, setEmailSettings] =
    useState<EmailSettings | null>(null);
  const [emailSettingsError, setEmailSettingsError] =
    useState<string | null>(null);

  // Bumped by the background refresh so pages reload their data
  const [refreshTick, setRefreshTick] = useState(0);

  const pendingActions = Object.values(latestActions)
    .filter((action) => action.status === "PENDING_APPROVAL")
    .sort(byNewest);

  function loadMaintenanceRequests() {
    return getMaintenanceRequests()
      .then((data) => {
        setMaintenanceRequests(data);
        setMaintenanceError(null);
      })
      .catch((err) => {
        console.error(
          "Failed to load maintenance requests:",
          err
        );
        setMaintenanceError(
          errorMessage(err, "Unable to load maintenance requests.")
        );
      })
      .finally(() => setMaintenanceLoading(false));
  }

  function loadLatestActions() {
    return getLatestProposedActions()
      .then((actions) => {
        setLatestActions(
          Object.fromEntries(
            actions.map((action) => [action.workItemId, action])
          )
        );
      })
      .catch((err) => {
        console.error("Failed to load proposed actions:", err);
      });
  }

  /**
   * Reloads the open work items and keeps the selected item in sync. A
   * selected item that was just closed stays selected (so its result stays
   * visible) unless clearClosedSelection is set.
   */
  async function refreshWorkItems(clearClosedSelection = false) {
    const refreshedWorkItems = await getWorkItems();

    setWorkItems(refreshedWorkItems);

    setSelectedWorkItem((current) => {
      if (!current) {
        return current;
      }

      return (
        refreshedWorkItems.find((item) => item.id === current.id) ??
        (clearClosedSelection ? null : current)
      );
    });

    return refreshedWorkItems;
  }

  useEffect(() => {
    // State is only set in promise callbacks, never synchronously here.
    getWorkItems()
      .then((data) => {
        setWorkItems(data);
        setError(null);
      })
      .catch((err) => {
        console.error("Failed to load work items:", err);
        setError(errorMessage(err, "Unable to load work items."));
      })
      .finally(() => setLoading(false));

    void loadLatestActions();
    void loadMaintenanceRequests();

    getEmailSettings()
      .then(setEmailSettings)
      .catch((err) =>
        setEmailSettingsError(
          errorMessage(err, "Unable to load email settings.")
        )
      );
  }, []);

  // New emails are processed in the background, so check for new work
  // regularly while the tab is visible.
  useEffect(() => {
    const interval = window.setInterval(() => {
      if (document.visibilityState !== "visible") {
        return;
      }

      getWorkItems()
        .then((data) => setWorkItems(data))
        .catch((err) => console.error("Background refresh failed:", err));

      void loadLatestActions();
      void loadMaintenanceRequests();
      setRefreshTick((tick) => tick + 1);
    }, 30_000);

    return () => window.clearInterval(interval);
  }, []);

  function rememberAction(action: ProposedAction) {
    setLatestActions((current) => ({
      ...current,
      [action.workItemId]: action,
    }));

    if (proposedAction?.id === action.id) {
      setProposedAction(action);
    }
  }

  async function handleReviewWorkItem(workItem: WorkItem) {
    setSelectedWorkItem(workItem);
    setProposedAction(null);
    setActionError(null);

    try {
      const actions = await getProposedActions(workItem.id);

      if (actions.length > 0) {
        setProposedAction([...actions].sort(byNewest)[0]);
      }
    } catch (err) {
      console.error("Failed to load proposed action:", err);
      setActionError(
        errorMessage(err, "Unable to load proposed action.")
      );
    }
  }

  async function generateFor(workItemId: string) {
    const result = await generateProposedAction(workItemId);

    setLatestActions((current) => ({
      ...current,
      [workItemId]: result,
    }));

    if (selectedWorkItem?.id === workItemId) {
      setProposedAction(result);
    }

    await refreshWorkItems();

    return result;
  }

  async function handleGenerateProposedAction() {
    if (!selectedWorkItem) {
      return;
    }

    try {
      setGeneratingAction(true);
      setActionError(null);
      setProposedAction(null);

      await generateFor(selectedWorkItem.id);
    } catch (err) {
      console.error("Failed to generate proposed action:", err);
      setActionError(
        errorMessage(err, "Unable to generate proposed action.")
      );
    } finally {
      setGeneratingAction(false);
    }
  }

  /** A reviewed action changed, e.g. its reply was sent. */
  function handleActionUpdated(action: ProposedAction) {
    rememberAction(action);

    setPendingDecision((current) =>
      current && current.action.id === action.id
        ? { ...current, action }
        : current
    );

    setRefreshTick((tick) => tick + 1);
  }

  async function handleOpenWorkItem(workItemId: string) {
    navigate("work-items");

    try {
      const workItem =
        workItems.find((item) => item.id === workItemId) ??
        (await getWorkItem(workItemId));

      await handleReviewWorkItem(workItem);
    } catch (err) {
      setActionError(errorMessage(err, "Unable to open this request."));
    }
  }

  async function handleRegenerateRejectedAction(
    rejectedAction: ProposedAction
  ) {
    try {
      setGeneratingAction(true);
      setActionError(null);

      const result = await generateFor(rejectedAction.workItemId);

      // Make it the current pending review
      setSelectedPendingAction(result);
      setPendingDecision(null);
    } catch (err) {
      console.error(
        "Failed to regenerate proposed action:",
        err
      );

      setActionError(
        errorMessage(err, "Unable to generate a new proposed action.")
      );
    } finally {
      setGeneratingAction(false);
    }
  }

  async function decide(
    action: ProposedAction,
    decision: "APPROVED" | "REJECTED",
    finalResponse?: string
  ) {
    const updatedAction =
      decision === "APPROVED"
        ? await approveProposedAction(action.id, finalResponse)
        : await rejectProposedAction(action.id);

    rememberAction(updatedAction);

    // The backend changes the work item status on approval/rejection.
    await refreshWorkItems();

    if (
      decision === "APPROVED" &&
      updatedAction.actionType === "CREATE_MAINTENANCE_REQUEST"
    ) {
      await loadMaintenanceRequests();
    }

    return updatedAction;
  }

  async function handleDecision(
    decision: "APPROVED" | "REJECTED",
    finalResponse?: string
  ) {
    if (!proposedAction) {
      return;
    }

    try {
      setUpdatingAction(true);
      setActionError(null);
      setSelectedPendingAction(null);

      const updatedAction = await decide(
        proposedAction,
        decision,
        finalResponse
      );

      setProposedAction(updatedAction);
    } catch (err) {
      console.error("Failed to update proposed action:", err);
      setActionError(
        errorMessage(err, "Unable to update proposed action.")
      );
    } finally {
      setUpdatingAction(false);
    }
  }

  async function handlePendingDecision(
    decision: "APPROVED" | "REJECTED",
    finalResponse?: string
  ) {
    if (!selectedPendingAction) {
      return;
    }

    try {
      setUpdatingAction(true);
      setActionError(null);

      const decidedWorkItem = workItems.find(
        (item) => item.id === selectedPendingAction.workItemId
      );

      const updatedAction = await decide(
        selectedPendingAction,
        decision,
        finalResponse
      );

      // Show result instead of immediately blanking panel
      setPendingDecision({
        action: updatedAction,
        decision,
        workItem: decidedWorkItem,
      });
      setSelectedPendingAction(null);
    } catch (err) {
      console.error("Failed to update pending action:", err);
      setActionError(
        errorMessage(err, "Unable to update proposed action.")
      );
    } finally {
      setUpdatingAction(false);
    }
  }

  async function handleCloseWorkItem(
    status: "COMPLETED" | "CANCELLED"
  ) {
    if (!selectedWorkItem) {
      return;
    }

    try {
      setUpdatingAction(true);
      setActionError(null);

      await closeWorkItem(selectedWorkItem.id, status);

      setSelectedWorkItem(null);
      setProposedAction(null);

      await refreshWorkItems();
      await loadLatestActions();
    } catch (err) {
      console.error("Failed to close work item:", err);
      setActionError(
        errorMessage(err, "Unable to close this work item.")
      );
    } finally {
      setUpdatingAction(false);
    }
  }

  async function handleCreateRequest(
    event: React.FormEvent<HTMLFormElement>
  ) {
    event.preventDefault();

    const message = newRequestMessage.trim();

    if (!message) {
      setCreateRequestError(
        "Please enter a tenant or property request."
      );
      return;
    }

    try {
      setCreatingRequest(true);
      setCreateRequestError(null);
      setCreateRequestSuccess(null);

      const createdWorkItem = await createWorkItem(message);

      setWorkItems((currentItems) => [
        createdWorkItem,
        ...currentItems,
      ]);

      setNewRequestMessage("");

      // The backend proposes an action right away; pick it up so the
      // pending count and the work item details are current.
      await loadLatestActions();

      setCreateRequestSuccess(
        createdWorkItem.status === "WAITING_FOR_APPROVAL"
          ? "Request processed. A recommended action is ready for review in Pending Actions."
          : "Request processed. Open it in Work Items to generate a recommended action."
      );

      setSelectedWorkItem(createdWorkItem);
      setActionError(null);

      const actions = await getProposedActions(createdWorkItem.id);
      setProposedAction(actions.length > 0 ? [...actions].sort(byNewest)[0] : null);
    } catch (err) {
      console.error("Failed to create work item:", err);
      setCreateRequestError(
        errorMessage(err, "Unable to process the request.")
      );
    } finally {
      setCreatingRequest(false);
    }
  }

  function handleCloseDetails() {
    setSelectedWorkItem(null);
    setProposedAction(null);
    setActionError(null);
  }

  function navigate(view: AppView) {
    if (view === "maintenance") {
      setSelectedMaintenanceWorkItemId(null);
    }

    if (view === "email-setup") {
      // A Gmail confirmation email may have arrived since the last load
      getEmailSettings()
        .then((settings) => {
          setEmailSettings(settings);
          setEmailSettingsError(null);
        })
        .catch((err) =>
          setEmailSettingsError(
            errorMessage(err, "Unable to load email settings.")
          )
        );
    }

    if (view !== "pending-actions") {
      setPendingDecision(null);
      setSelectedPendingAction(null);
    }

    setActiveView(view);
    setActionError(null);
  }

  const totalWorkItems = workItems.length;
  const newWorkItems = workItems.filter(
    (item) => !latestActions[item.id]
  ).length;

  const viewContent = {
    dashboard: {
      eyebrow: "Operations",
      title: "Dashboard",
      description:
        "Monitor incoming requests and operations.",
    },

    inbox: {
      eyebrow: "Email Intake",
      title: "Inbox",
      description:
        "Emails received at your Clerova forwarding address.",
    },

    "email-setup": {
      eyebrow: "Settings",
      title: "Email Setup",
      description:
        "Connect your inbox by forwarding email to Clerova.",
    },

    "work-items": {
      eyebrow: "Operations",
      title: "Work Items",
      description:
        "Review incoming requests and AI-recommended actions.",
    },

    maintenance: {
      eyebrow: "Property Operations",
      title: "Maintenance",
      description:
        "Track maintenance requests created from approved actions.",
    },

    "pending-actions": {
      eyebrow: "Human Review",
      title: "Pending Actions",
      description:
        "Review AI recommendations awaiting approval.",
    },
  } satisfies Record<
    AppView,
    {
      eyebrow: string;
      title: string;
      description: string;
    }
  >;

  const currentView = viewContent[activeView];

  function handleSelectPendingAction(action: ProposedAction) {
    setSelectedPendingAction(action);
    setPendingDecision(null);
    setActionError(null);
  }

  function handleViewMaintenance(workItemId: string) {
    setSelectedMaintenanceWorkItemId(workItemId);
    setActiveView("maintenance");
  }

  function handleMaintenanceRequestUpdated(
    updated: MaintenanceRequest
  ) {
    setMaintenanceRequests((current) =>
      current.map((item) =>
        item.id === updated.id ? updated : item
      )
    );
  }

  async function handleMaintenanceWorkItemClosed() {
    const refreshedWorkItems = await refreshWorkItems(true);

    if (
      selectedWorkItem &&
      !refreshedWorkItems.some(
        (item) => item.id === selectedWorkItem.id
      )
    ) {
      setProposedAction(null);
    }

    await loadLatestActions();
  }

  const pendingDecisionWorkItem =
    pendingDecision
      ? workItems.find(
          (item) =>
            item.id === pendingDecision.action.workItemId
        ) ?? pendingDecision.workItem
      : undefined;

  return (
    <div className="app-shell">
      <Sidebar
        activeView={activeView}
        pendingActionCount={pendingActions.length}
        organization={organization}
        userEmail={userEmail}
        onNavigate={navigate}
        onSignOut={onSignOut}
      />

      <nav className="mobile-nav" aria-label="Mobile navigation">
        <button
          type="button"
          className={activeView === "dashboard" ? "active" : ""}
          onClick={() => navigate("dashboard")}
        >
          Dashboard
        </button>

        <button
          type="button"
          className={activeView === "inbox" ? "active" : ""}
          onClick={() => navigate("inbox")}
        >
          Inbox
        </button>

        <button
          type="button"
          className={activeView === "work-items" ? "active" : ""}
          onClick={() => navigate("work-items")}
        >
          Work Items
        </button>

        <button
          type="button"
          className={activeView === "pending-actions" ? "active" : ""}
          onClick={() => navigate("pending-actions")}
        >
          AI Review
          {pendingActions.length > 0 && (
            <span className="mobile-nav-count">
              {pendingActions.length}
            </span>
          )}
        </button>

        <button
          type="button"
          className={activeView === "maintenance" ? "active" : ""}
          onClick={() => navigate("maintenance")}
        >
          Maintenance
        </button>
      </nav>

      <main className="dashboard">
        <header className="dashboard-header">
          <div>
            <span className="eyebrow">
              {currentView.eyebrow}
            </span>

            <h1>{currentView.title}</h1>

            <p>{currentView.description}</p>
          </div>
        </header>

        {/* =====================================================
            DASHBOARD
           ===================================================== */}

        {activeView === "dashboard" && (
          <DashboardPage
            totalWorkItems={totalWorkItems}
            newWorkItems={newWorkItems}
            pendingActionCount={pendingActions.length}
            maintenanceRequests={maintenanceRequests}
            newRequestMessage={newRequestMessage}
            creatingRequest={creatingRequest}
            createRequestError={createRequestError}
            createRequestSuccess={createRequestSuccess}
            onNewRequestMessageChange={setNewRequestMessage}
            onCreateRequest={handleCreateRequest}
            onViewWorkItems={() => navigate("work-items")}
            onViewMaintenance={() => navigate("maintenance")}
          />
        )}

        {/* =====================================================
            WORK ITEMS
           ===================================================== */}

        {activeView === "work-items" && (
          <WorkItemsPage
            workItems={workItems}
            loading={loading}
            error={error}
            selectedWorkItem={selectedWorkItem}
            proposedAction={proposedAction}
            latestActions={latestActions}
            generatingAction={generatingAction}
            updatingAction={updatingAction}
            actionError={actionError}
            onReviewWorkItem={handleReviewWorkItem}
            onCloseDetails={handleCloseDetails}
            onGenerateProposedAction={
              handleGenerateProposedAction
            }
            onApprove={(finalResponse) =>
              handleDecision("APPROVED", finalResponse)
            }
            onReject={() => handleDecision("REJECTED")}
            onCloseWorkItem={handleCloseWorkItem}
            onViewMaintenance={handleViewMaintenance}
            emailSendingEnabled={emailSettings?.sendingEnabled ?? false}
            onActionUpdated={handleActionUpdated}
          />
        )}

        {/* =====================================================
            INBOX AND EMAIL SETUP
           ===================================================== */}

        {activeView === "inbox" && (
          <InboxPage
            refreshKey={refreshTick}
            onOpenWorkItem={handleOpenWorkItem}
            onOpenEmailSetup={() => navigate("email-setup")}
          />
        )}

        {activeView === "email-setup" && (
          <EmailSetupPage
            settings={emailSettings}
            error={emailSettingsError}
          />
        )}

        {/* =====================================================
            MAINTENANCE
           ===================================================== */}

        {activeView === "maintenance" && (
          <MaintenancePage
            maintenanceRequests={maintenanceRequests}
            loading={maintenanceLoading}
            error={maintenanceError}
            selectedWorkItemId={selectedMaintenanceWorkItemId}
            onClearSelection={() =>
              setSelectedMaintenanceWorkItemId(null)
            }
            onRequestUpdated={handleMaintenanceRequestUpdated}
            onWorkItemClosed={handleMaintenanceWorkItemClosed}
          />
        )}

        {/* =====================================================
            PENDING ACTIONS
           ===================================================== */}

        {activeView === "pending-actions" && (
          <PendingActionsPage
            pendingActions={pendingActions}
            workItems={workItems}
            selectedPendingAction={selectedPendingAction}
            pendingDecision={pendingDecision}
            pendingDecisionWorkItem={pendingDecisionWorkItem}
            updatingAction={updatingAction}
            generatingAction={generatingAction}
            actionError={actionError}
            onSelectAction={handleSelectPendingAction}
            onApprove={(finalResponse) =>
              handlePendingDecision("APPROVED", finalResponse)
            }
            onReject={() => handlePendingDecision("REJECTED")}
            onRegenerate={handleRegenerateRejectedAction}
            onClearDecision={() => setPendingDecision(null)}
            emailSendingEnabled={emailSettings?.sendingEnabled ?? false}
            onActionUpdated={handleActionUpdated}
            onViewMaintenance={(workItemId) => {
              setPendingDecision(null);
              handleViewMaintenance(workItemId);
            }}
          />
        )}
      </main>
    </div>
  );
}

export default App;

import { useEffect, useState } from "react";
import "./App.css";

import {
  approveProposedAction,
  createWorkItem,
  generateProposedAction,
  getProposedActions,
  getWorkItems,
  rejectProposedAction,
} from "./api/workItems";
import { getMaintenanceRequests, } from "./api/MaintenanceRequests";
import { PendingActionsPage } from "./pages/PendingActionsPage";
import { Sidebar, type AppView } from "./components/Sidebar";
import { WorkItemsPage } from "./pages/WorkItemsPage";
import { DashboardPage } from "./pages/DashboardPage";
import { MaintenancePage } from "./pages/MaintenancePage";
import type { MaintenanceRequest } from "./types/MaintenanceRequest";
import type { ProposedAction } from "./types/ProposedAction";
import type { WorkItem } from "./types/WorkItem";

function App() {
  const [workItems, setWorkItems] = useState<WorkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [activeView, setActiveView] =
    useState<AppView>("dashboard");

  const [selectedWorkItem, setSelectedWorkItem] =
    useState<WorkItem | null>(null);

  const [proposedAction, setProposedAction] =
    useState<ProposedAction | null>(null);

  const [pendingActions, setPendingActions] = useState<
    ProposedAction[]
  >([]);

  const [generatingAction, setGeneratingAction] = useState(false);
  const [updatingAction, setUpdatingAction] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const [maintenanceRequests, setMaintenanceRequests] = useState<
    MaintenanceRequest[]
  >([]);

  const [, setMaintenanceLoading] = useState(true);

  const [, setMaintenanceError] =
    useState<string | null>(null);

  const [, setMaintenanceNotes] = useState<
    Record<string, string>
  >({});

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
  } | null>(null);

  async function loadMaintenanceRequests() {
    try {
      setMaintenanceLoading(true);
      setMaintenanceError(null);

      const data = await getMaintenanceRequests();

      setMaintenanceRequests(data);

      setMaintenanceNotes((current) => {
        const next = { ...current };

        data.forEach((request) => {
          if (next[request.id] === undefined) {
            next[request.id] = request.notes ?? "";
          }
        });

        return next;
      });
    } catch (err) {
      console.error("Failed to load maintenance requests:", err);
      setMaintenanceError("Unable to load maintenance requests.");
    } finally {
      setMaintenanceLoading(false);
    }
  }

  useEffect(() => {
    async function loadWorkItems() {
      try {
        setLoading(true);
        setError(null);

        const data = await getWorkItems();
        setWorkItems(data);
        await loadPendingActions(data);
      } catch (err) {
        console.error("Failed to load work items:", err);
        setError("Unable to load work items.");
      } finally {
        setLoading(false);
      }
    }

    void loadWorkItems();
    void loadMaintenanceRequests();
  }, []);

useEffect(() => {
  if (activeView !== "pending-actions") {
    setPendingDecision(null);
    setSelectedPendingAction(null);
  }
}, [activeView]);

  async function handleReviewWorkItem(workItem: WorkItem) {
    setSelectedWorkItem(workItem);
    setProposedAction(null);
    setActionError(null);

    try {
      const actions = await getProposedActions(workItem.id);

      if (actions.length > 0) {
        const latestAction = [...actions].sort(
          (a, b) =>
            new Date(b.createdAt).getTime() -
            new Date(a.createdAt).getTime()
        )[0];

        setProposedAction(latestAction);
      }
    } catch (err) {
      console.error("Failed to load proposed action:", err);
      setActionError("Unable to load proposed action.");
    }
  }

  async function handleGenerateProposedAction() {
    if (!selectedWorkItem) {
      return;
    }

    try {
      setGeneratingAction(true);
      setActionError(null);
      setProposedAction(null);

      const result = await generateProposedAction(
        selectedWorkItem.id
      );

      setProposedAction(result);

      setLatestActions((current) => ({
        ...current,
        [result.workItemId]: result,
      }));

      setPendingActions((current) => [
        result,
        ...current.filter((action) => action.id !== result.id),
      ]);

      const refreshedWorkItems = await getWorkItems();

      setWorkItems(refreshedWorkItems);

      const refreshedSelectedWorkItem = refreshedWorkItems.find(
        (item) => item.id === selectedWorkItem.id
      );

      if (refreshedSelectedWorkItem) {
        setSelectedWorkItem(refreshedSelectedWorkItem);
      }
    } catch (err) {
      console.error("Failed to generate proposed action:", err);
      setActionError("Unable to generate proposed action.");
    } finally {
      setGeneratingAction(false);
    }
  }

async function handleRegenerateRejectedAction(
  rejectedAction: ProposedAction
) {
  const workItem = workItems.find(
    (item) => item.id === rejectedAction.workItemId
  );

  if (!workItem) {
    setActionError(
      "Unable to find the work item for this action."
    );
    return;
  }

  try {
    setGeneratingAction(true);
    setActionError(null);

    const result = await generateProposedAction(
      workItem.id
    );

    // Latest action for Work Items
    setLatestActions((current) => ({
      ...current,
      [workItem.id]: result,
    }));

    // Put the newly generated action into Pending Actions
    setPendingActions((current) => [
      result,
      ...current.filter(
        (action) => action.id !== result.id
      ),
    ]);

    // Make it the current pending review
    setSelectedPendingAction(result);

    // Keep Work Items synchronized too
    if (selectedWorkItem?.id === workItem.id) {
      setProposedAction(result);
    }

    setPendingDecision(null);
  } catch (err) {
    console.error(
      "Failed to regenerate proposed action:",
      err
    );

    setActionError(
      "Unable to generate a new proposed action."
    );
  } finally {
    setGeneratingAction(false);
  }
}

  async function handleApprove() {
    if (!proposedAction) {
      return;
    }

    try {
      setUpdatingAction(true);
      setActionError(null);

      const updatedAction = await approveProposedAction(
        proposedAction.id
      );

      setProposedAction(updatedAction);

      setSelectedPendingAction(null);

      setLatestActions((current) => ({
        ...current,
        [updatedAction.workItemId]: updatedAction,
      }));

      setPendingActions((current) =>
        current.filter(
          (action) => action.id !== updatedAction.id
        )
      );

      // IMPORTANT:
      // Approval changes the WorkItem status in the backend.
      // Reload work items so the UI gets IN_PROGRESS,
      // WAITING_FOR_INFORMATION, COMPLETED, etc.
      const refreshedWorkItems = await getWorkItems();

      setWorkItems(refreshedWorkItems);

      // Also update the selected item so the right panel
      // doesn't continue showing the old NEW status.
      const refreshedSelectedWorkItem =
        refreshedWorkItems.find(
          (item) =>
            item.id === updatedAction.workItemId
        );

      if (refreshedSelectedWorkItem) {
        setSelectedWorkItem(refreshedSelectedWorkItem);
      }

      await loadMaintenanceRequests();
    } catch (err) {
      console.error(
        "Failed to approve proposed action:",
        err
      );

      setActionError(
        "Unable to approve proposed action."
      );
    } finally {
      setUpdatingAction(false);
    }
  }

  async function handleReject() {
    if (!proposedAction) {
      return;
    }

    try {
      setUpdatingAction(true);
      setSelectedPendingAction(null);
      setActionError(null);

      const updatedAction = await rejectProposedAction(
        proposedAction.id
      );

      setProposedAction(updatedAction);

      setLatestActions((current) => ({
        ...current,
        [updatedAction.workItemId]: updatedAction,
      }));

      setPendingActions((current) =>
        current.filter(
          (action) => action.id !== updatedAction.id
        )
      );

    } catch (err) {
      console.error("Failed to reject proposed action:", err);
      setActionError("Unable to reject proposed action.");
    } finally {
      setUpdatingAction(false);
    }
  }

async function handleApprovePendingAction() {
  if (!selectedPendingAction) {
    return;
  }

  try {
    setUpdatingAction(true);
    setActionError(null);

    const updatedAction = await approveProposedAction(
      selectedPendingAction.id
    );

    setPendingActions((current) =>
      current.filter(
        (action) => action.id !== updatedAction.id
      )
    );

    setLatestActions((current) => ({
      ...current,
      [updatedAction.workItemId]: updatedAction,
    }));

    if (proposedAction?.id === updatedAction.id) {
      setProposedAction(updatedAction);
    }

    // Show result instead of immediately blanking panel
    setPendingDecision({
      action: updatedAction,
      decision: "APPROVED",
    });

    setSelectedPendingAction(null);
    const refreshedWorkItems = await getWorkItems();

    setWorkItems(refreshedWorkItems);

    const refreshedSelectedWorkItem =
      refreshedWorkItems.find(
        (item) =>
          item.id === updatedAction.workItemId
      );

    if (
      refreshedSelectedWorkItem &&
      selectedWorkItem?.id === updatedAction.workItemId
    ) {
      setSelectedWorkItem(refreshedSelectedWorkItem);
    }

    await loadPendingActions(refreshedWorkItems);
    await loadMaintenanceRequests();
  } catch (err) {
    console.error(
      "Failed to approve pending action:",
      err
    );

    setActionError("Unable to approve proposed action.");
  } finally {
    setUpdatingAction(false);
  }
}

async function handleRejectPendingAction() {
  if (!selectedPendingAction) {
    return;
  }

  try {
    setUpdatingAction(true);
    setActionError(null);

    const updatedAction = await rejectProposedAction(
      selectedPendingAction.id
    );

    setPendingActions((current) =>
      current.filter(
        (action) => action.id !== updatedAction.id
      )
    );

    setLatestActions((current) => ({
      ...current,
      [updatedAction.workItemId]: updatedAction,
    }));

    if (proposedAction?.id === updatedAction.id) {
      setProposedAction(updatedAction);
    }

    setPendingDecision({
      action: updatedAction,
      decision: "REJECTED",
    });

    setSelectedPendingAction(null);
  } catch (err) {
    console.error(
      "Failed to reject pending action:",
      err
    );

    setActionError("Unable to reject proposed action.");
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

      const createdWorkItem = await createWorkItem(
        "demo-property-management",
        message
      );

      setWorkItems((currentItems) => [
        createdWorkItem,
        ...currentItems,
      ]);

      setNewRequestMessage("");
      setCreateRequestSuccess("Request processed successfully.");

      setSelectedWorkItem(createdWorkItem);
      setProposedAction(null);
      setActionError(null);
    } catch (err) {
      console.error("Failed to create work item:", err);
      setCreateRequestError("Unable to process the request.");
    } finally {
      setCreatingRequest(false);
    }
  }

async function loadPendingActions(items: WorkItem[]) {
  try {
    const results = await Promise.all(
      items.map(async (item) => {
        const actions = await getProposedActions(item.id);

        if (actions.length === 0) {
          return {
            workItemId: item.id,
            latestAction: null,
          };
        }

        const latestAction = [...actions].sort(
          (a, b) =>
            new Date(b.createdAt).getTime() -
            new Date(a.createdAt).getTime()
        )[0];

        return {
          workItemId: item.id,
          latestAction,
        };
      })
    );

    const latestByWorkItem: Record<
      string,
      ProposedAction
    > = {};

    const pending: ProposedAction[] = [];

    results.forEach(({ workItemId, latestAction }) => {
      if (!latestAction) {
        return;
      }

      latestByWorkItem[workItemId] = latestAction;

      if (latestAction.status === "PENDING_APPROVAL") {
        pending.push(latestAction);
      }
    });

    pending.sort(
      (a, b) =>
        new Date(b.createdAt).getTime() -
        new Date(a.createdAt).getTime()
    );

    setLatestActions(latestByWorkItem);
    setPendingActions(pending);
  } catch (err) {
    console.error(
      "Failed to load proposed actions:",
      err
    );
  }
}

  function handleCloseDetails() {
    setSelectedWorkItem(null);
    setProposedAction(null);
    setActionError(null);
  }

  const totalWorkItems = workItems.length;
  const newWorkItems = workItems.filter(
    (item) => !latestActions[item.id]
  ).length;

  const viewContent = {
    dashboard: {
      eyebrow: "Property Operations",
      title: "Dashboard",
      description:
        "Monitor incoming requests and property operations.",
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

async function handleMaintenanceWorkItemClosed() {
  const refreshedWorkItems = await getWorkItems();

  setWorkItems(refreshedWorkItems);

  await loadPendingActions(refreshedWorkItems);

  if (
    selectedWorkItem &&
    !refreshedWorkItems.some(
      (item) => item.id === selectedWorkItem.id
    )
  ) {
    setSelectedWorkItem(null);
    setProposedAction(null);
  }

  await loadMaintenanceRequests();
}

const pendingDecisionWorkItem =
  pendingDecision
    ? workItems.find(
        (item) =>
          item.id === pendingDecision.action.workItemId
      )
    : undefined;

  return (
    <div className="app-shell">
      <Sidebar
        activeView={activeView}
        pendingActionCount={pendingActions.length}
        onNavigate={(view) => {
          if (view === "maintenance") {
            setSelectedMaintenanceWorkItemId(null);
          }

          setActiveView(view);
          setActionError(null);
        }}
      />

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
            onViewWorkItems={() =>
              setActiveView("work-items")
            }
            onViewMaintenance={() => {
              setSelectedMaintenanceWorkItemId(null);
              setActiveView("maintenance");
            }}
          />
        )}

        {/* =====================================================
            WORK ITEMS
           ===================================================== */}

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
            onApprove={handleApprove}
            onReject={handleReject}
            onViewMaintenance={handleViewMaintenance}
          />
        )}

        {/* =====================================================
            MAINTENANCE
           ===================================================== */}

{activeView === "maintenance" && (
  <MaintenancePage
    selectedWorkItemId={selectedMaintenanceWorkItemId}
    onClearSelection={() =>
      setSelectedMaintenanceWorkItemId(null)
    }
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
            onApprove={handleApprovePendingAction}
            onReject={handleRejectPendingAction}
            onRegenerate={handleRegenerateRejectedAction}
            onClearDecision={() => setPendingDecision(null)}
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
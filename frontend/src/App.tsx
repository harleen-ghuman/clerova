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

import { Sidebar } from "./components/Sidebar";
import { StatCard } from "./components/StatCard";
import { WorkItemCard } from "./components/WorkItemCard";
import { getMaintenanceRequests } from "./api/MaintenanceRequests";
import type { MaintenanceRequest } from "./types/MaintenanceRequest";
import type { ProposedAction } from "./types/ProposedAction";
import type { WorkItem } from "./types/WorkItem";

function App() {
  const [workItems, setWorkItems] = useState<WorkItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedWorkItem, setSelectedWorkItem] =
    useState<WorkItem | null>(null);

  const [proposedAction, setProposedAction] =
    useState<ProposedAction | null>(null);

  const [generatingAction, setGeneratingAction] = useState(false);
  const [updatingAction, setUpdatingAction] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [maintenanceRequests, setMaintenanceRequests] =
    useState<MaintenanceRequest[]>([]);

  const [maintenanceLoading, setMaintenanceLoading] =
    useState(true);

  const [maintenanceError, setMaintenanceError] =
    useState<string | null>(null);

  const [newRequestMessage, setNewRequestMessage] =
    useState("");

  const [creatingRequest, setCreatingRequest] =
    useState(false);

  const [createRequestError, setCreateRequestError] =
    useState<string | null>(null);

  const [createRequestSuccess, setCreateRequestSuccess] =
    useState<string | null>(null);

  async function loadMaintenanceRequests() {
    try {
      setMaintenanceLoading(true);
      setMaintenanceError(null);

      const data = await getMaintenanceRequests();
      setMaintenanceRequests(data);
    } catch (err) {
      console.error(
        "Failed to load maintenance requests:",
        err
      );

      setMaintenanceError(
        "Unable to load maintenance requests."
      );
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
      } catch (err) {
        console.error("Failed to load work items:", err);
        setError("Unable to load work items.");
      } finally {
        setLoading(false);
      }
    }

    loadWorkItems();
    loadMaintenanceRequests();
  }, []);

  async function handleReviewWorkItem(workItem: WorkItem) {
    setSelectedWorkItem(workItem);
    setProposedAction(null);
    setActionError(null);

    try {
      const actions = await getProposedActions(workItem.id);

      if (actions.length > 0) {
        setProposedAction(actions[0]);
      }
    } catch (err) {
      console.error(err);
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
    } catch (err) {
      console.error("Failed to generate proposed action:", err);
      setActionError("Unable to generate proposed action.");
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

      await loadMaintenanceRequests();
    } catch (err) {
      console.error("Failed to approve proposed action:", err);
      setActionError("Unable to approve proposed action.");
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
      setActionError(null);

      const updatedAction = await rejectProposedAction(
        proposedAction.id
      );

      setProposedAction(updatedAction);
    } catch (err) {
      console.error("Failed to reject proposed action:", err);
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

      setCreateRequestSuccess(
        "Request processed successfully."
      );

      setSelectedWorkItem(createdWorkItem);
      setProposedAction(null);
      setActionError(null);
    } catch (err) {
      console.error("Failed to create work item:", err);

      setCreateRequestError(
        "Unable to process the request."
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

  const totalWorkItems = workItems.length;

  const openWorkItems = workItems.filter(
    (item) => item.status === "NEW"
  ).length;

  const highPriorityItems = workItems.filter(
    (item) => item.priority === "HIGH"
  ).length;

  return (
    <div className="app-shell">
      <Sidebar />

      <main className="dashboard">
        <header className="dashboard-header">
          <div>
            <span className="eyebrow">
              Property Operations
            </span>

            <h1>Dashboard</h1>

            <p>
              Review incoming requests and AI-proposed actions.
            </p>
          </div>
        </header>

        <section className="stats-grid">
          <StatCard
            label="Total Work Items"
            value={totalWorkItems}
            helperText="Incoming requests"
          />

          <StatCard
            label="Open"
            value={openWorkItems}
            helperText="Needs attention"
          />

          <StatCard
            label="High Priority"
            value={highPriorityItems}
            helperText="Review first"
          />
        </section>

        <section className="dashboard-section new-request-section">
          <div className="section-heading">
            <div>
              <span className="eyebrow">AI Intake</span>
              <h2>Process New Request</h2>
              <p>
                Enter an incoming tenant or property message.
                Clerova will classify and prioritize it automatically.
              </p>
            </div>
          </div>

          <form
            className="new-request-form"
            onSubmit={handleCreateRequest}
          >
            <label htmlFor="new-request-message">
              Incoming message
            </label>

            <textarea
              id="new-request-message"
              value={newRequestMessage}
              onChange={(event) =>
                setNewRequestMessage(event.target.value)
              }
              placeholder="Example: Water is leaking through the kitchen ceiling at 850 Maple Avenue and it's getting worse."
              rows={5}
              disabled={creatingRequest}
            />

            <div className="new-request-actions">
              <span className="request-helper">
                AI will extract the address, category,
                priority and summary.
              </span>

              <button
                className="primary-button"
                type="submit"
                disabled={
                  creatingRequest ||
                  newRequestMessage.trim().length === 0
                }
              >
                {creatingRequest
                  ? "Processing with AI..."
                  : "Process Request"}
              </button>
            </div>

            {createRequestError && (
              <p className="action-error">
                {createRequestError}
              </p>
            )}

            {createRequestSuccess && (
              <p className="request-success">
                {createRequestSuccess}
              </p>
            )}
          </form>
        </section>

        <section className="dashboard-section">
          <div className="section-heading">
            <div>
              <h2>Recent Work Items</h2>
              <p>
                AI-classified incoming property requests.
              </p>
            </div>
          </div>

          {loading && <p>Loading work items...</p>}

          {error && <p className="error-message">{error}</p>}

          {!loading && !error && workItems.length === 0 && (
            <p>No work items found.</p>
          )}

          {!loading && !error && workItems.length > 0 && (
            <div className="work-items-list">
              {workItems.map((workItem) => (
                <WorkItemCard
                  key={workItem.id}
                  workItem={workItem}
                  onReview={handleReviewWorkItem}
                />
              ))}
            </div>
          )}

          {selectedWorkItem && (
            <section className="work-item-details">
              <div className="details-header">
                <div>
                  <span className="details-label">
                    Work Item
                  </span>

                  <h2>
                    {selectedWorkItem.address ??
                      "Address not provided"}
                  </h2>
                </div>

                <button
                  className="close-button"
                  onClick={handleCloseDetails}
                >
                  Close
                </button>
              </div>

              <div className="details-grid">
                <div>
                  <span>Priority</span>
                  <strong>
                    {selectedWorkItem.priority}
                  </strong>
                </div>

                <div>
                  <span>Category</span>
                  <strong>
                    {selectedWorkItem.category}
                  </strong>
                </div>

                <div>
                  <span>Status</span>
                  <strong>
                    {selectedWorkItem.status}
                  </strong>
                </div>
              </div>

              <div className="details-section">
                <h3>Original Message</h3>
                <p>{selectedWorkItem.originalMessage}</p>
              </div>

              <div className="details-section">
                <h3>AI Summary</h3>
                <p>{selectedWorkItem.summary}</p>
              </div>

              {(!proposedAction ||
                proposedAction.status === "REJECTED") && (
                <button
                  className="primary-button"
                  onClick={handleGenerateProposedAction}
                  disabled={generatingAction || updatingAction}
                >
                  {generatingAction
                    ? "Generating..."
                    : proposedAction?.status === "REJECTED"
                      ? "Generate New Proposed Action"
                      : "Generate Proposed Action"}
                </button>
              )}

              {actionError && (
                <p className="action-error">
                  {actionError}
                </p>
              )}

              {proposedAction && (
                <section className="proposed-action">
                  <h3>Proposed Action</h3>

                  <div className="action-meta">
                    <div>
                      <span>Action</span>
                      <strong>
                        {proposedAction.actionType}
                      </strong>
                    </div>

                    <div>
                      <span>Status</span>
                      <strong>
                        {proposedAction.status}
                      </strong>
                    </div>
                  </div>

                  <div className="details-section">
                    <h3>AI Reasoning</h3>
                    <p>{proposedAction.reasoning}</p>
                  </div>

                  <div className="details-section">
                    <h3>Drafted Response</h3>
                    <p className="drafted-response">
                      {proposedAction.draftedResponse}
                    </p>
                  </div>

                  {proposedAction.status ===
                  "PENDING_APPROVAL" ? (
                    <div className="approval-actions">
                      <button
                        className="approve-button"
                        onClick={handleApprove}
                        disabled={updatingAction}
                      >
                        {updatingAction
                          ? "Updating..."
                          : "Approve"}
                      </button>

                      <button
                        className="reject-button"
                        onClick={handleReject}
                        disabled={updatingAction}
                      >
                        {updatingAction
                          ? "Updating..."
                          : "Reject"}
                      </button>
                    </div>
                  ) : (
                    <div className="decision-result">
                      Action{" "}
                      {proposedAction.status.toLowerCase()}.
                    </div>
                  )}
                </section>
              )}
            </section>
          )}
        </section>
       <section className="dashboard-section">
         <div className="section-heading">
           <div>
             <h2>Maintenance Requests</h2>
             <p>
               Requests created from approved AI actions.
             </p>
           </div>
         </div>

         {maintenanceLoading && (
           <p>Loading maintenance requests...</p>
         )}

         {maintenanceError && (
           <p className="error-message">
             {maintenanceError}
           </p>
         )}

         {!maintenanceLoading &&
           !maintenanceError &&
           maintenanceRequests.length === 0 && (
             <p>No maintenance requests yet.</p>
           )}

         {!maintenanceLoading &&
           !maintenanceError &&
           maintenanceRequests.length > 0 && (
             <div className="maintenance-list">
               {maintenanceRequests.map((request) => (
                 <article
                   className="maintenance-card"
                   key={request.id}
                 >
                   <div className="maintenance-card-header">
                     <div>
                       <span className="details-label">
                         Maintenance Request
                       </span>

                       <h3>
                         {request.address ??
                           "Address not provided"}
                       </h3>
                     </div>

                     <span
                       className={`priority-badge priority-${request.priority.toLowerCase()}`}
                     >
                       {request.priority}
                     </span>
                   </div>

                   <p className="maintenance-description">
                     {request.description}
                   </p>

                   <div className="maintenance-footer">
                     <span>
                       Status: <strong>{request.status}</strong>
                     </span>

                     <span className="ai-created-label">
                       Created from AI-approved action
                     </span>
                   </div>
                 </article>
               ))}
             </div>
           )}
       </section>
      </main>
    </div>
  );
}

export default App;
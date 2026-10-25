import { useState } from "react";

import { ApprovedResponse } from "../components/ApprovedResponse";
import { ResponseEditor } from "../components/ResponseEditor";
import { WorkItemActivity } from "../components/WorkItemActivity";
import { WorkItemCard } from "../components/WorkItemCard";
import type { ProposedAction } from "../types/ProposedAction";
import type { WorkItem } from "../types/WorkItem";
import { decisionCopy, hasResponseToSend } from "../utils/actionCopy";

// Statuses where the work item waits on people outside Clerova and is
// closed manually once handled.
const MANUALLY_CLOSABLE_STATUSES = [
  "ESCALATED",
  "WAITING_FOR_INFORMATION",
];

interface WorkItemsPageProps {
  workItems: WorkItem[];
  loading: boolean;
  error: string | null;

  selectedWorkItem: WorkItem | null;
  proposedAction: ProposedAction | null;

  latestActions: Record<string, ProposedAction>;

  generatingAction: boolean;
  updatingAction: boolean;
  actionError: string | null;

  onReviewWorkItem: (workItem: WorkItem) => void;
  onCloseDetails: () => void;
  onGenerateProposedAction: () => void;
  onApprove: (finalResponse: string) => void;
  onReject: () => void;
  onCloseWorkItem: (status: "COMPLETED" | "CANCELLED") => void;
  onViewMaintenance: (workItemId: string) => void;
  emailSendingEnabled: boolean;
  onActionUpdated: (action: ProposedAction) => void;
}

export function WorkItemsPage({
  workItems,
  loading,
  error,
  selectedWorkItem,
  proposedAction,
  latestActions,
  generatingAction,
  updatingAction,
  actionError,
  onReviewWorkItem,
  onCloseDetails,
  onGenerateProposedAction,
  onApprove,
  onReject,
  onCloseWorkItem,
  onViewMaintenance,
  emailSendingEnabled,
  onActionUpdated,
}: WorkItemsPageProps) {
  // Approved replies can be emailed only for requests that came by email
  const replyTo =
    emailSendingEnabled && selectedWorkItem?.source === "EMAIL"
      ? selectedWorkItem.senderEmail
      : null;

  return (
    <section className="dashboard-section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">
            Operations Queue
          </span>

          <h2>Recent Work Items</h2>

          <p>
            Review AI-classified requests and approve
            recommended actions.
          </p>
        </div>
      </div>

      {loading && <p>Loading work items...</p>}

      {error && (
        <p className="error-message">{error}</p>
      )}

      {!loading && !error && workItems.length === 0 && (
        <p>No work items found.</p>
      )}

      {!loading && !error && workItems.length > 0 && (
        <div className="work-items-workspace">
          <div className="work-items-queue">
            <div className="queue-header">
              <div>
                <span className="details-label">
                  Request Queue
                </span>

                <strong>
                  {workItems.length} requests
                </strong>
              </div>

              <span className="queue-sort-label">
                Newest first
              </span>
            </div>

            <div className="work-items-list">
              {workItems.map((workItem) => (
                <WorkItemCard
                  key={workItem.id}
                  workItem={workItem}
                  proposedAction={
                    latestActions[workItem.id]
                  }
                  selected={
                    selectedWorkItem?.id === workItem.id
                  }
                  onReview={onReviewWorkItem}
                />
              ))}
            </div>
          </div>

          <div className="work-item-review-panel">
            {!selectedWorkItem ? (
              <div className="review-empty-state">
                <div className="review-empty-icon">
                  C
                </div>

                <h3>Select a request to review</h3>

                <p>
                  Choose a work item from the queue to
                  review its details and generate a Clerova
                  AI recommendation.
                </p>
              </div>
            ) : (
              <section className="work-item-details">
                <div className="details-header">
                  <div>
                    <span className="details-label">
                      Selected Request
                    </span>

                    <h2>
                      {selectedWorkItem.address ||
                        "Address not provided"}
                    </h2>
                  </div>

                  <button
                    className="close-button"
                    type="button"
                    onClick={onCloseDetails}
                  >
                    Close
                  </button>
                </div>

                <div className="details-grid">
                  <div>
                    <span>Priority</span>

                    <strong
                      className={`priority-badge priority-${selectedWorkItem.priority.toLowerCase()}`}
                    >
                      {selectedWorkItem.priority}
                    </strong>
                  </div>

                  <div>
                    <span>Category</span>

                    <strong
                      className={`category-badge category-${selectedWorkItem.category.toLowerCase()}`}
                    >
                      {selectedWorkItem.category}
                    </strong>
                  </div>

                  <div>
                    <span>Status</span>

                    <strong
                      className={`status-badge status-${selectedWorkItem.status
                        .toLowerCase()
                        .replaceAll("_", "-")}`}
                    >
                      {selectedWorkItem.status.replaceAll(
                        "_",
                        " "
                      )}
                    </strong>
                  </div>
                </div>

                {selectedWorkItem.source === "EMAIL" && (
                  <div className="email-origin">
                    <span className="details-label">Received by email</span>
                    <strong>
                      {selectedWorkItem.senderName
                        ? `${selectedWorkItem.senderName} <${selectedWorkItem.senderEmail}>`
                        : selectedWorkItem.senderEmail}
                    </strong>
                    {selectedWorkItem.subject && (
                      <span>{selectedWorkItem.subject}</span>
                    )}
                  </div>
                )}

                <div className="details-section">
                  <h3>Original Message</h3>

                  <p>
                    {selectedWorkItem.originalMessage}
                  </p>
                </div>

                <div className="details-section">
                  <h3>AI Summary</h3>

                  <p>{selectedWorkItem.summary}</p>
                </div>

                {!proposedAction && (
                  <button
                    className="primary-button"
                    type="button"
                    onClick={onGenerateProposedAction}
                    disabled={
                      generatingAction ||
                      updatingAction
                    }
                  >
                    {generatingAction
                      ? "Generating..."
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
                    <div className="proposed-action-heading">
                      <div>
                        <span className="eyebrow">
                          Clerova AI
                        </span>

                        <h3>Proposed Action</h3>

                        <p>
                          Review the AI recommendation
                          before taking action.
                        </p>
                      </div>

                      <span
                        className={`status-badge status-${proposedAction.status
                          .toLowerCase()
                          .replaceAll("_", "-")}`}
                      >
                        {proposedAction.status.replaceAll(
                          "_",
                          " "
                        )}
                      </span>
                    </div>

                    <div className="action-meta">
                      <div>
                        <span>
                          Recommended Action
                        </span>

                        <strong>
                          {proposedAction.actionType.replaceAll(
                            "_",
                            " "
                          )}
                        </strong>
                      </div>
                    </div>

                    <div className="details-section">
                      <h3>
                        Why Clerova recommends this
                      </h3>

                      <p>
                        {proposedAction.reasoning}
                      </p>
                    </div>

                    {proposedAction.status ===
                    "PENDING_APPROVAL" ? (
                      <PendingApproval
                        key={proposedAction.id}
                        action={proposedAction}
                        updatingAction={updatingAction}
                        onApprove={onApprove}
                        onReject={onReject}
                      />
                    ) : (
                      <>
                        {hasResponseToSend(proposedAction) && (
                          <ApprovedResponse
                            action={proposedAction}
                            sendTo={replyTo}
                            onSent={onActionUpdated}
                          />
                        )}

                        <div
                          className={`decision-result decision-${proposedAction.status.toLowerCase()}`}
                        >
                          <div className="decision-content">
                            <strong>
                              {decisionCopy(proposedAction, Boolean(replyTo)).title}
                            </strong>

                            <span>
                              {decisionCopy(proposedAction, Boolean(replyTo)).description}
                            </span>
                          </div>

                          {proposedAction.status ===
                            "APPROVED" &&
                            proposedAction.actionType ===
                              "CREATE_MAINTENANCE_REQUEST" && (
                              <button
                                type="button"
                                className="primary-button decision-action-button"
                                onClick={() =>
                                  onViewMaintenance(
                                    selectedWorkItem.id
                                  )
                                }
                              >
                                View Maintenance
                              </button>
                            )}

                          {proposedAction.status ===
                            "REJECTED" && (
                            <button
                              className="primary-button decision-action-button"
                              type="button"
                              onClick={
                                onGenerateProposedAction
                              }
                              disabled={
                                generatingAction ||
                                updatingAction
                              }
                            >
                              {generatingAction
                                ? "Generating..."
                                : "Generate New Proposal"}
                            </button>
                          )}
                        </div>
                      </>
                    )}
                  </section>
                )}

                <WorkItemActivity
                  workItem={selectedWorkItem}
                  refreshKey={`${selectedWorkItem.status}-${selectedWorkItem.updatedAt}-${proposedAction?.id}-${proposedAction?.status}`}
                />

                {MANUALLY_CLOSABLE_STATUSES.includes(
                  selectedWorkItem.status
                ) && (
                  <div className="close-work-item-actions">
                    <button
                      type="button"
                      className="approve-button"
                      onClick={() => onCloseWorkItem("COMPLETED")}
                      disabled={updatingAction}
                    >
                      Mark Resolved
                    </button>

                    <button
                      type="button"
                      className="reject-button"
                      onClick={() => onCloseWorkItem("CANCELLED")}
                      disabled={updatingAction}
                    >
                      Cancel Request
                    </button>
                  </div>
                )}
              </section>
            )}
          </div>
        </div>
      )}
    </section>
  );
}

interface PendingApprovalProps {
  action: ProposedAction;
  updatingAction: boolean;
  onApprove: (finalResponse: string) => void;
  onReject: () => void;
}

function PendingApproval({
  action,
  updatingAction,
  onApprove,
  onReject,
}: PendingApprovalProps) {
  const [response, setResponse] = useState(action.draftedResponse);

  return (
    <>
      <ResponseEditor
        id={`response-${action.id}`}
        value={response}
        disabled={updatingAction}
        onChange={setResponse}
      />

      <div className="approval-actions">
        <button
          className="approve-button"
          type="button"
          onClick={() => onApprove(response)}
          disabled={updatingAction || response.trim().length === 0}
        >
          {updatingAction ? "Updating..." : "Approve"}
        </button>

        <button
          className="reject-button"
          type="button"
          onClick={onReject}
          disabled={updatingAction}
        >
          {updatingAction ? "Updating..." : "Reject"}
        </button>
      </div>
    </>
  );
}

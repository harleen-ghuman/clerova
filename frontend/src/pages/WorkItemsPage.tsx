import { WorkItemCard } from "../components/WorkItemCard";
import type { ProposedAction } from "../types/ProposedAction";
import type { WorkItem } from "../types/WorkItem";

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
  onApprove: () => void;
  onReject: () => void;
  onViewMaintenance: (workItemId: string) => void;
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
  onViewMaintenance,
}: WorkItemsPageProps) {
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

                    <div className="details-section">
                      <h3>
                        Suggested response to resident
                      </h3>

                      <p className="drafted-response">
                        {proposedAction.draftedResponse}
                      </p>
                    </div>

                    {proposedAction.status ===
                    "PENDING_APPROVAL" ? (
                      <div className="approval-actions">
                        <button
                          className="approve-button"
                          type="button"
                          onClick={onApprove}
                          disabled={updatingAction}
                        >
                          {updatingAction
                            ? "Updating..."
                            : "Approve"}
                        </button>

                        <button
                          className="reject-button"
                          type="button"
                          onClick={onReject}
                          disabled={updatingAction}
                        >
                          {updatingAction
                            ? "Updating..."
                            : "Reject"}
                        </button>
                      </div>
                    ) : (
                      <div
                        className={`decision-result decision-${proposedAction.status.toLowerCase()}`}
                      >
                        <div className="decision-content">
                          <strong>
                            {proposedAction.status ===
                              "APPROVED" &&
                            proposedAction.actionType ===
                              "CREATE_MAINTENANCE_REQUEST"
                              ? "Maintenance request created"
                              : proposedAction.status ===
                                  "APPROVED"
                                ? "Action approved"
                                : "Action rejected"}
                          </strong>

                          <span>
                            {proposedAction.status ===
                              "APPROVED" &&
                            proposedAction.actionType ===
                              "CREATE_MAINTENANCE_REQUEST"
                              ? "The maintenance request was created successfully and is now available in Maintenance."
                              : proposedAction.status ===
                                  "APPROVED"
                                ? "The approved action was completed successfully."
                                : "No action was executed."}
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
                    )}
                  </section>
                )}
              </section>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
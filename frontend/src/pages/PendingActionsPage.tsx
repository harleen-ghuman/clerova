import type { ProposedAction } from "../types/ProposedAction";
import type { WorkItem } from "../types/WorkItem";

interface PendingDecision {
  action: ProposedAction;
  decision: "APPROVED" | "REJECTED";
}

interface PendingActionsPageProps {
  pendingActions: ProposedAction[];
  workItems: WorkItem[];
  selectedPendingAction: ProposedAction | null;
  pendingDecision: PendingDecision | null;
  pendingDecisionWorkItem?: WorkItem;
  updatingAction: boolean;
  generatingAction: boolean;
  actionError: string | null;

  onSelectAction: (action: ProposedAction) => void;
  onApprove: () => void;
  onReject: () => void;
  onRegenerate: (action: ProposedAction) => void;
  onClearDecision: () => void;
  onViewMaintenance: (workItemId: string) => void;
}

export function PendingActionsPage({
  pendingActions,
  workItems,
  selectedPendingAction,
  pendingDecision,
  pendingDecisionWorkItem,
  updatingAction,
  generatingAction,
  actionError,
  onSelectAction,
  onApprove,
  onReject,
  onRegenerate,
  onClearDecision,
  onViewMaintenance,
}: PendingActionsPageProps) {
  return (
    <section className="dashboard-section">
      <div className="section-heading">
        <div>
          <span className="eyebrow">Human Review</span>

          <h2>Pending Actions</h2>

          <p>
            Review AI recommendations before Clerova takes
            approved action.
          </p>
        </div>
      </div>

      {pendingActions.length === 0 && !pendingDecision ? (
        <div className="empty-view">
          <div className="review-empty-icon">✓</div>

          <h2>You're all caught up</h2>

          <p>
            There are no AI recommendations awaiting approval.
          </p>
        </div>
      ) : (
        <div className="pending-workspace">
          <div className="pending-queue">
            <div className="queue-header">
              <div>
                <span className="details-label">
                  Approval Queue
                </span>

                <strong>
                  {pendingActions.length} awaiting review
                </strong>
              </div>
            </div>

            <div className="pending-queue-list">
              {pendingActions.map((action) => {
                const workItem = workItems.find(
                  (item) => item.id === action.workItemId
                );

                return (
                  <button
                    type="button"
                    key={action.id}
                    className={`pending-queue-item ${
                      selectedPendingAction?.id === action.id
                        ? "selected"
                        : ""
                    }`}
                    onClick={() => onSelectAction(action)}
                  >
                    <div className="pending-queue-item-top">
                      <div>
                        {workItem && (
                          <span
                            className={`priority-badge priority-${workItem.priority.toLowerCase()}`}
                          >
                            {workItem.priority}
                          </span>
                        )}
                      </div>

                      <span className="status-badge status-pending-approval">
                        PENDING
                      </span>
                    </div>

                    <strong className="pending-address">
                      {workItem?.address ??
                        "Address not provided"}
                    </strong>

                    <span className="pending-category">
                      {workItem?.category ?? "Request"}
                    </span>

                    <p>
                      {workItem?.summary ??
                        action.actionType.replaceAll("_", " ")}
                    </p>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="pending-review-panel">
            {pendingDecision ? (
              <div className="pending-decision-state">
                <div
                  className={`pending-decision-icon ${
                    pendingDecision.decision === "APPROVED"
                      ? "approved"
                      : "rejected"
                  }`}
                >
                  {pendingDecision.decision === "APPROVED"
                    ? "✓"
                    : "×"}
                </div>

                <span className="details-label">
                  Review Complete
                </span>

                <h2>
                  {pendingDecision.decision === "APPROVED" &&
                  pendingDecision.action.actionType ===
                    "CREATE_MAINTENANCE_REQUEST"
                    ? "Maintenance request created"
                    : pendingDecision.decision === "APPROVED"
                      ? "Action approved"
                      : "Action rejected"}
                </h2>

                {pendingDecisionWorkItem && (
                  <div className="pending-decision-work-item">
                    <span className="details-label">
                      Work Item
                    </span>

                    <strong>
                      {pendingDecisionWorkItem.address ||
                        "Address not provided"}
                    </strong>

                    <span>
                      {pendingDecisionWorkItem.summary}
                    </span>
                  </div>
                )}

                <p>
                  {pendingDecision.decision === "APPROVED" &&
                  pendingDecision.action.actionType ===
                    "CREATE_MAINTENANCE_REQUEST"
                    ? "The maintenance request was created successfully and is now available in Maintenance."
                    : pendingDecision.decision === "APPROVED"
                      ? "The approved action was completed successfully."
                      : "The recommendation was rejected and no action was executed."}
                </p>

                {pendingDecision.decision === "APPROVED" &&
                  pendingDecision.action.actionType ===
                    "CREATE_MAINTENANCE_REQUEST" && (
                    <button
                      type="button"
                      className="primary-button"
                      onClick={() =>
                        onViewMaintenance(
                          pendingDecision.action.workItemId
                        )
                      }
                    >
                      View Maintenance Request
                    </button>
                  )}

                {pendingDecision.decision === "REJECTED" ? (
                  <button
                    type="button"
                    className="primary-button"
                    onClick={() =>
                      onRegenerate(pendingDecision.action)
                    }
                    disabled={generatingAction}
                  >
                    {generatingAction
                      ? "Generating..."
                      : "Generate New Proposed Action"}
                  </button>
                ) : (
                  <button
                    type="button"
                    className="secondary-action-button"
                    onClick={onClearDecision}
                  >
                    Review Next Action
                  </button>
                )}
              </div>
            ) : !selectedPendingAction ? (
              <div className="review-empty-state">
                <div className="review-empty-icon">C</div>

                <h3>Select an action to review</h3>

                <p>
                  Choose an AI recommendation from the approval
                  queue to inspect its reasoning and suggested
                  response.
                </p>
              </div>
            ) : (
              <PendingActionReview
                action={selectedPendingAction}
                workItem={workItems.find(
                  (item) =>
                    item.id === selectedPendingAction.workItemId
                )}
                updatingAction={updatingAction}
                actionError={actionError}
                onApprove={onApprove}
                onReject={onReject}
              />
            )}
          </div>
        </div>
      )}
    </section>
  );
}

interface PendingActionReviewProps {
  action: ProposedAction;
  workItem?: WorkItem;
  updatingAction: boolean;
  actionError: string | null;
  onApprove: () => void;
  onReject: () => void;
}

function PendingActionReview({
  action,
  workItem,
  updatingAction,
  actionError,
  onApprove,
  onReject,
}: PendingActionReviewProps) {
  return (
    <div className="pending-review-content">
      <div className="details-header">
        <div>
          <span className="details-label">
            Action Review
          </span>

          <h2>
            {workItem?.address ??
              "Address not provided"}
          </h2>
        </div>

        <span className="status-badge status-pending-approval">
          PENDING APPROVAL
        </span>
      </div>

      {workItem && (
        <>
          <div className="details-grid">
            <div>
              <span>Priority</span>

              <strong
                className={`priority-badge priority-${workItem.priority.toLowerCase()}`}
              >
                {workItem.priority}
              </strong>
            </div>

            <div>
              <span>Category</span>

              <strong
                className={`category-badge category-${workItem.category.toLowerCase()}`}
              >
                {workItem.category}
              </strong>
            </div>

            <div>
              <span>Action</span>

              <strong>
                {action.actionType.replaceAll("_", " ")}
              </strong>
            </div>
          </div>

          <div className="details-section">
            <h3>Original Message</h3>
            <p>{workItem.originalMessage}</p>
          </div>
        </>
      )}

      <div className="details-section">
        <h3>Why Clerova recommends this</h3>
        <p>{action.reasoning}</p>
      </div>

      <div className="details-section">
        <h3>Suggested response to resident</h3>

        <p className="drafted-response">
          {action.draftedResponse}
        </p>
      </div>

      {actionError && (
        <p className="action-error">
          {actionError}
        </p>
      )}

      <div className="pending-review-actions">
        <button
          type="button"
          className="reject-button"
          onClick={onReject}
          disabled={updatingAction}
        >
          {updatingAction ? "Updating..." : "Reject"}
        </button>

        <button
          type="button"
          className="approve-button"
          onClick={onApprove}
          disabled={updatingAction}
        >
          {updatingAction
            ? "Updating..."
            : "Approve Action"}
        </button>
      </div>
    </div>
  );
}
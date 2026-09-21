import type { ProposedAction } from "../types/ProposedAction";
import type { WorkItem } from "../types/WorkItem";

interface WorkItemCardProps {
  workItem: WorkItem;
  proposedAction?: ProposedAction;
  selected?: boolean;
  onReview: (workItem: WorkItem) => void;
}

export function WorkItemCard({
  workItem,
  proposedAction,
  selected = false,
  onReview,
}: WorkItemCardProps) {
  const displayStatus =
    proposedAction?.status === "REJECTED"
      ? "REJECTED"
      : workItem.status;

  const statusClass = displayStatus
    .toLowerCase()
    .replaceAll("_", "-");

  const statusLabel =
    displayStatus === "REJECTED"
      ? "REJECTED"
      : displayStatus === "WAITING_FOR_APPROVAL"
        ? "AWAITING REVIEW"
        : displayStatus === "WAITING_FOR_INFORMATION"
          ? "WAITING FOR INFO"
          : displayStatus.replaceAll("_", " ");

  return (
    <article
      className={`work-item ${
        selected ? "work-item-selected" : ""
      }`}
    >
      <div className="work-item-top">
        <div className="work-item-badges">
          <span
            className={`priority-badge priority-${workItem.priority.toLowerCase()}`}
          >
            {workItem.priority}
          </span>

          <span
            className={`category-badge category-${workItem.category.toLowerCase()}`}
          >
            {workItem.category}
          </span>
        </div>

        <span
          className={`status-badge status-${statusClass}`}
        >
          {statusLabel}
        </span>
      </div>

      <h3>
        {workItem.address || "Address not provided"}
      </h3>

      <p>{workItem.summary}</p>

      {proposedAction?.status === "APPROVED" &&
        workItem.status === "WAITING_FOR_INFORMATION" && (
          <p className="work-item-context">
            More information requested
          </p>
        )}

      {proposedAction?.status === "APPROVED" &&
        workItem.status === "IN_PROGRESS" && (
          <p className="work-item-context">
            Approved action in progress
          </p>
        )}

      <div className="work-item-footer">
        <span>
          {new Date(workItem.createdAt).toLocaleString([], {
            month: "short",
            day: "numeric",
            hour: "numeric",
            minute: "2-digit",
          })}
        </span>

        <button
          type="button"
          onClick={() => onReview(workItem)}
        >
          {selected ? "Selected" : "Review"}
        </button>
      </div>
    </article>
  );
}
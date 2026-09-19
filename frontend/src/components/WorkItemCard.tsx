import type { WorkItem } from "../types/WorkItem";

interface WorkItemCardProps {
  workItem: WorkItem;
  onReview: (workItem: WorkItem) => void;
}

export function WorkItemCard({
  workItem,
  onReview,
}: WorkItemCardProps) {
  return (
    <article className="work-item">
      <div className="work-item-top">
        <span
          className={`priority-badge priority-${workItem.priority.toLowerCase()}`}
        >
          {workItem.priority}
        </span>

        <span className="status-badge">
          {workItem.status}
        </span>
      </div>

      <h3>
        {workItem.address ?? "Address not provided"}
      </h3>

      <p>{workItem.summary}</p>

      <div className="work-item-footer">
        <span>{workItem.category}</span>

        <button onClick={() => onReview(workItem)}>
          Review
        </button>
      </div>
    </article>
  );
}
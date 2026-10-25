import { useEffect, useState } from "react";

import { getConversation, getWorkItemEvents } from "../api/email";
import type { ConversationEntry, WorkItemEvent } from "../types/Email";
import type { WorkItem } from "../types/WorkItem";

interface WorkItemActivityProps {
  workItem: WorkItem;
  /** Changes whenever the work item may have new activity to show. */
  refreshKey: string;
}

function formatTime(value: string) {
  return new Date(value).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/**
 * The email conversation (for email requests) and the audit trail of a
 * work item.
 */
export function WorkItemActivity({
  workItem,
  refreshKey,
}: WorkItemActivityProps) {
  const [conversation, setConversation] = useState<ConversationEntry[]>([]);
  const [events, setEvents] = useState<WorkItemEvent[]>([]);

  const isEmail = workItem.source === "EMAIL";

  useEffect(() => {
    let cancelled = false;

    if (isEmail) {
      getConversation(workItem.id)
        .then((entries) => {
          if (!cancelled) {
            setConversation(entries);
          }
        })
        .catch((err) => console.error("Failed to load conversation:", err));
    }

    getWorkItemEvents(workItem.id)
      .then((items) => {
        if (!cancelled) {
          setEvents(items);
        }
      })
      .catch((err) => console.error("Failed to load activity:", err));

    return () => {
      cancelled = true;
    };
  }, [workItem.id, isEmail, refreshKey]);

  return (
    <>
      {isEmail && conversation.length > 1 && (
        <div className="details-section">
          <h3>Email conversation</h3>

          <ol className="conversation">
            {conversation.map((entry) => (
              <li
                key={entry.id}
                className={`conversation-entry conversation-${entry.direction.toLowerCase()}`}
              >
                <div className="conversation-meta">
                  <strong>
                    {entry.direction === "INBOUND"
                      ? entry.from
                      : `You replied to ${entry.to}`}
                  </strong>
                  <span>{formatTime(entry.at)}</span>
                </div>

                <p>{entry.text}</p>

                {entry.attachments.length > 0 && (
                  <span className="conversation-attachments">
                    Attachments: {entry.attachments.join(", ")}
                  </span>
                )}
              </li>
            ))}
          </ol>
        </div>
      )}

      {events.length > 0 && (
        <div className="details-section">
          <h3>Activity</h3>

          <ol className="activity-timeline">
            {events.map((event) => (
              <li key={event.id}>
                <span className="activity-detail">{event.detail}</span>
                <span className="activity-meta">
                  {event.actor ?? "Clerova"} · {formatTime(event.createdAt)}
                </span>
              </li>
            ))}
          </ol>
        </div>
      )}
    </>
  );
}

import type { WorkItem } from "../types/WorkItem";

/** The groups requests are filtered by on the Requests page. */
export type RequestBucket = "review" | "waiting" | "progress" | "done";

export const BUCKET_LABELS: Record<RequestBucket, string> = {
  review: "Needs review",
  waiting: "Waiting on customer",
  progress: "In progress",
  done: "Closed",
};

export function requestBucket(item: WorkItem): RequestBucket {
  switch (item.status) {
    case "NEW":
    case "WAITING_FOR_APPROVAL":
      return "review";
    case "WAITING_FOR_INFORMATION":
      return "waiting";
    case "IN_PROGRESS":
    case "ESCALATED":
      return "progress";
    default:
      return "done";
  }
}

/**
 * Where a request is listed on the Requests page. Each request is in
 * exactly one group, so the counts add up to All. A new email during a job
 * needs a person, so that job is listed under review until it's handled.
 */
export function requestFilterBucket(item: WorkItem): RequestBucket {
  return hasNewReply(item) ? "review" : requestBucket(item);
}

/** The sender wrote again while the job is in progress, unanswered. */
export function hasNewReply(item: WorkItem): boolean {
  return item.status === "IN_PROGRESS" && Boolean(item.unreadReplyAt);
}

/** The new email during a job looks urgent. */
export function hasUrgentReply(item: WorkItem): boolean {
  return hasNewReply(item) && Boolean(item.unreadReplyUrgent);
}

/** Status shown on a single request, a bit more specific than its bucket. */
export function requestStatusLabel(item: WorkItem): string {
  switch (item.status) {
    case "ESCALATED":
      return "Escalated";
    case "CANCELLED":
      return "Cancelled";
    case "COMPLETED":
      return "Done";
    default:
      return BUCKET_LABELS[requestBucket(item)];
  }
}

export function requestTitle(item: WorkItem): string {
  return item.subject?.trim() || item.summary || "Request";
}

export function requestSender(item: WorkItem): string {
  if (item.source === "EMAIL") {
    return item.senderName || item.senderEmail || "Unknown sender";
  }

  return "Entered in Clerova";
}

export function capitalize(value: string | null | undefined): string {
  const lower = (value ?? "").toLowerCase().replaceAll("_", " ");
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** Short time for lists: today's time, otherwise the date. */
export function shortTime(value: string): string {
  const date = new Date(value);
  const now = new Date();

  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }

  return date.toLocaleDateString([], { month: "short", day: "numeric" });
}

export function longTime(value: string): string {
  return new Date(value).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

/** What Clerova suggests, in plain words. */
export function suggestionTitle(actionType: string, canEmail: boolean): string {
  const reply = canEmail ? " and reply" : "";

  switch (actionType) {
    case "CREATE_MAINTENANCE_REQUEST":
      return `Create a maintenance job${reply}`;
    case "RESPOND_TO_CUSTOMER":
      return canEmail ? "Reply and close" : "Reply and close (copy the reply)";
    case "REQUEST_MORE_INFORMATION":
      return "Ask for more details";
    case "ESCALATE_TO_HUMAN":
      return "Escalate to your team";
    case "NO_ACTION":
      return "No action needed";
    default:
      return capitalize(actionType);
  }
}

import type { WorkItem } from "../types/WorkItem";
import type { ProposedAction } from "../types/ProposedAction";
import { apiRequest } from "./client";

export function getWorkItems(): Promise<WorkItem[]> {
  return apiRequest("/api/work-items");
}

export function createWorkItem(message: string): Promise<WorkItem> {
  return apiRequest("/api/work-items", {
    method: "POST",
    body: { message },
  });
}

export function closeWorkItem(
  workItemId: string,
  status: "COMPLETED" | "CANCELLED"
): Promise<WorkItem> {
  return apiRequest(`/api/work-items/${workItemId}/close`, {
    method: "POST",
    body: { status },
  });
}

export function generateProposedAction(
  workItemId: string
): Promise<ProposedAction> {
  return apiRequest(
    `/api/work-items/${workItemId}/proposed-actions`,
    { method: "POST" }
  );
}

export function getProposedActions(
  workItemId: string
): Promise<ProposedAction[]> {
  return apiRequest(`/api/work-items/${workItemId}/proposed-actions`);
}

/** Latest proposed action for each open work item. */
export function getLatestProposedActions(): Promise<ProposedAction[]> {
  return apiRequest("/api/work-items/proposed-actions/latest");
}

export function approveProposedAction(
  proposedActionId: string,
  finalResponse?: string
): Promise<ProposedAction> {
  return apiRequest(
    `/api/work-items/proposed-actions/${proposedActionId}/approve`,
    {
      method: "POST",
      body: finalResponse === undefined ? undefined : { finalResponse },
    }
  );
}

export function rejectProposedAction(
  proposedActionId: string
): Promise<ProposedAction> {
  return apiRequest(
    `/api/work-items/proposed-actions/${proposedActionId}/reject`,
    { method: "POST" }
  );
}

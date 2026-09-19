import type { WorkItem } from "../types/WorkItem";
import type { ProposedAction } from "../types/ProposedAction";

const API_BASE_URL = "http://localhost:8080";

export async function getWorkItems(): Promise<WorkItem[]> {
  const response = await fetch(`${API_BASE_URL}/api/work-items`);

  if (!response.ok) {
    throw new Error(`Failed to load work items: ${response.status}`);
  }

  return response.json();
}

export async function generateProposedAction(
  workItemId: string
): Promise<ProposedAction> {
  const response = await fetch(
    `${API_BASE_URL}/api/work-items/${workItemId}/proposed-actions`,
    {
      method: "POST",
    }
  );

  if (!response.ok) {
    throw new Error(
      `Failed to generate proposed action: ${response.status}`
    );
  }

  return response.json();
}

export async function approveProposedAction(
  proposedActionId: string
): Promise<ProposedAction> {
  const response = await fetch(
    `${API_BASE_URL}/api/work-items/proposed-actions/${proposedActionId}/approve`,
    {
      method: "POST",
    }
  );

  if (!response.ok) {
    throw new Error(
      `Failed to approve proposed action: ${response.status}`
    );
  }

  return response.json();
}

export async function rejectProposedAction(
  proposedActionId: string
): Promise<ProposedAction> {
  const response = await fetch(
    `${API_BASE_URL}/api/work-items/proposed-actions/${proposedActionId}/reject`,
    {
      method: "POST",
    }
  );

  if (!response.ok) {
    throw new Error(
      `Failed to reject proposed action: ${response.status}`
    );
  }

  return response.json();
}

export async function getProposedActions(
  workItemId: string
): Promise<ProposedAction[]> {
  const response = await fetch(
    `http://localhost:8080/api/work-items/${workItemId}/proposed-actions`
  );

  if (!response.ok) {
    throw new Error("Failed to load proposed actions");
  }

  return response.json();
}

export async function createWorkItem(
  organizationId: string,
  message: string
): Promise<WorkItem> {
  const response = await fetch(
    "http://localhost:8080/api/work-items",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        organizationId,
        message,
      }),
    }
  );

  if (!response.ok) {
    throw new Error(
      `Failed to create work item: ${response.status}`
    );
  }

  return response.json();
}
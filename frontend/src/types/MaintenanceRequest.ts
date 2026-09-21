export interface MaintenanceRequest {
  id: string;
  workItemId: string;
  proposedActionId: string;
  organizationId: string;
  address: string | null;
  description: string;
  priority: "LOW" | "MEDIUM" | "HIGH";
  status: "OPEN" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export type MaintenanceRequestStatus =
  | "OPEN"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";
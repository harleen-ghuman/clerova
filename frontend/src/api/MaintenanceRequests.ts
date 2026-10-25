import type {
  MaintenanceRequest,
  MaintenanceRequestStatus,
} from "../types/MaintenanceRequest";
import { apiRequest } from "./client";

export function getMaintenanceRequests(): Promise<
  MaintenanceRequest[]
> {
  return apiRequest("/api/maintenance-requests");
}

export function updateMaintenanceRequest(
  id: string,
  update: {
    status?: MaintenanceRequestStatus;
    notes?: string;
  }
): Promise<MaintenanceRequest> {
  return apiRequest(`/api/maintenance-requests/${id}`, {
    method: "PATCH",
    body: update,
  });
}

import type {
  MaintenanceRequest,
  MaintenanceRequestStatus,
} from "../types/MaintenanceRequest";
import { API_BASE_URL } from "../config";


export async function getMaintenanceRequests(): Promise<
  MaintenanceRequest[]
> {
  const response = await fetch(
    `${API_BASE_URL}/api/maintenance-requests`
  );

  if (!response.ok) {
    throw new Error(
      `Failed to load maintenance requests: ${response.status}`
    );
  }

  return response.json();
}

export async function updateMaintenanceRequest(
  id: string,
  update: {
    status?: MaintenanceRequestStatus;
    notes?: string;
  }
): Promise<MaintenanceRequest> {
  const response = await fetch(
    `${API_BASE_URL}/api/maintenance-requests/${id}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(update),
    }
  );

  if (!response.ok) {
    throw new Error(
      `Failed to update maintenance request: ${response.status}`
    );
  }

  return response.json();
}
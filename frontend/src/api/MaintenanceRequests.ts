import type {
  MaintenanceRequest,
  MaintenanceRequestStatus,
} from "../types/MaintenanceRequest";

const API_BASE_URL = "http://localhost:8080/api";

export async function getMaintenanceRequests(): Promise<
  MaintenanceRequest[]
> {
  const response = await fetch(
    `${API_BASE_URL}/maintenance-requests`
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
    `${API_BASE_URL}/maintenance-requests/${id}`,
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
import type { MaintenanceRequest } from "../types/MaintenanceRequest";

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
import type { BrokerSummary, LoadDetail, LoadStatus, LoadSummary } from "../types/Load";
import { apiRequest } from "./client";

export function getLoads(): Promise<LoadSummary[]> {
  return apiRequest("/api/loads");
}

export function getLoad(id: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${id}`);
}

/** Leave either out to keep it; an empty note clears it. */
export function updateLoad(
  id: string,
  change: { status?: LoadStatus; statusNote?: string }
): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${id}`, { method: "PATCH", body: change });
}

export function resolveFlag(loadId: string, flagId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/flags/${flagId}/resolve`, { method: "POST" });
}

export function sendReply(loadId: string, replyId: string, body: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/replies/${replyId}/send`, {
    method: "POST",
    body: { body },
  });
}

export function dismissReply(loadId: string, replyId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/replies/${replyId}/dismiss`, { method: "POST" });
}

export function getBrokers(): Promise<BrokerSummary[]> {
  return apiRequest("/api/brokers");
}

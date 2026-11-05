import type {
  BrokerSummary,
  DocumentKind,
  LoadDetail,
  LoadStatus,
  LoadSummary,
  UploadedDocument,
} from "../types/Load";
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

/** Files an email from the Inbox under a load. */
export function fileEmail(loadId: string, messageId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/emails/${messageId}`, { method: "POST" });
}

/** Uploads a PDF or photo; without a load, Clerova files it if it can tell which. */
export function uploadDocument(file: File, loadId?: string): Promise<UploadedDocument> {
  const form = new FormData();
  form.append("file", file);

  if (loadId) {
    form.append("loadId", loadId);
  }

  return apiRequest("/api/load-documents", { method: "POST", body: form });
}

export function updateDocument(
  id: string,
  change: { loadId?: string; kind?: DocumentKind }
): Promise<UploadedDocument> {
  return apiRequest(`/api/load-documents/${id}`, { method: "PATCH", body: change });
}

export function deleteDocument(id: string): Promise<void> {
  return apiRequest(`/api/load-documents/${id}`, { method: "DELETE" });
}

/** Uploads waiting to be filed under a load. */
export function getUnfiledDocuments(): Promise<UploadedDocument[]> {
  return apiRequest("/api/load-documents");
}

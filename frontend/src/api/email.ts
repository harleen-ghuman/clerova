import type {
  ConversationEntry,
  EmailSettings,
  InboundMessage,
  WorkItemEvent,
} from "../types/Email";
import type { ProposedAction } from "../types/ProposedAction";
import { apiRequest } from "./client";

export function getInbox(): Promise<InboundMessage[]> {
  return apiRequest("/api/inbox?limit=100");
}

export function reprocessEmail(id: string): Promise<InboundMessage> {
  return apiRequest(`/api/inbox/${id}/reprocess`, { method: "POST" });
}

export function getEmailSettings(): Promise<EmailSettings> {
  return apiRequest("/api/email-settings");
}

export function getConversation(
  workItemId: string
): Promise<ConversationEntry[]> {
  return apiRequest(`/api/work-items/${workItemId}/conversation`);
}

export function getWorkItemEvents(
  workItemId: string
): Promise<WorkItemEvent[]> {
  return apiRequest(`/api/work-items/${workItemId}/events`);
}

export function sendReply(
  proposedActionId: string
): Promise<ProposedAction> {
  return apiRequest(
    `/api/work-items/proposed-actions/${proposedActionId}/send-reply`,
    { method: "POST" }
  );
}

import type {
  ConversationEntry,
  EmailSettings,
  InboundMessage,
  WorkItemEvent,
} from "../types/Email";
import type { ProposedAction } from "../types/ProposedAction";
import type { WorkItem } from "../types/WorkItem";
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

/** A draft answer to the sender's latest email, not saved. */
export function draftReply(workItemId: string): Promise<{ reply: string }> {
  return apiRequest(`/api/work-items/${workItemId}/draft-reply`, {
    method: "POST",
  });
}

/** Emails a message written in Clerova to the request's sender. */
export function sendMessage(
  workItemId: string,
  message: string
): Promise<WorkItem> {
  return apiRequest(`/api/work-items/${workItemId}/reply`, {
    method: "POST",
    body: { message },
  });
}

export function markReplyHandled(workItemId: string): Promise<WorkItem> {
  return apiRequest(`/api/work-items/${workItemId}/reply-handled`, {
    method: "POST",
  });
}

export function sendReply(
  proposedActionId: string
): Promise<ProposedAction> {
  return apiRequest(
    `/api/work-items/proposed-actions/${proposedActionId}/send-reply`,
    { method: "POST" }
  );
}

/** New suggestions or drafts a person can ask for per email. */
export const MAX_SUGGESTIONS = 5;

export interface WorkItem {
  id: string;
  organizationId: string;
  originalMessage: string;
  address: string | null;
  category: string;
  priority: string;
  status: string;
  summary: string;
  source: "MANUAL" | "EMAIL";
  senderEmail: string | null;
  senderName: string | null;
  subject: string | null;
  createdAt: string;
  updatedAt: string;
  /** The sender wrote again while the job is in progress. */
  unreadReplyAt?: string | null;
  /** That new email looks urgent (safety or active damage). */
  unreadReplyUrgent?: boolean;
  /** New suggestions or drafts left for the latest email (5 per email). */
  suggestionsLeft?: number;
}
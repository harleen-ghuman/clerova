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
}
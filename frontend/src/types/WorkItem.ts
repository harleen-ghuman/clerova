export interface WorkItem {
  id: string;
  organizationId: string;
  originalMessage: string;
  address: string | null;
  category: string;
  priority: string;
  status: string;
  summary: string;
  createdAt: string;
  updatedAt: string;
}
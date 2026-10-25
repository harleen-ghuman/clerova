export interface ProposedAction {
  id: string;
  workItemId: string;
  actionType: string;
  status: string;
  reasoning: string;
  draftedResponse: string;
  finalResponse: string | null;
  createdAt: string;
  updatedAt: string;
}

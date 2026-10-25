import type { ProposedAction } from "../types/ProposedAction";

export interface DecisionCopy {
  title: string;
  description: string;
}

/**
 * User-facing description of a reviewed proposed action. Clerova does not
 * send messages yet, so approved responses must be sent by the reviewer.
 */
export function decisionCopy(action: ProposedAction): DecisionCopy {
  if (action.status === "REJECTED") {
    return {
      title: "Action rejected",
      description: "The recommendation was rejected and no action was executed.",
    };
  }

  switch (action.actionType) {
    case "CREATE_MAINTENANCE_REQUEST":
      return {
        title: "Maintenance request created",
        description:
          "The maintenance request was created and is now available in Maintenance.",
      };
    case "RESPOND_TO_CUSTOMER":
      return {
        title: "Response approved",
        description:
          "Copy the approved response and send it to the resident. The request has been closed.",
      };
    case "REQUEST_MORE_INFORMATION":
      return {
        title: "Information request approved",
        description:
          "Copy the approved message and send it. The request stays open until you resolve it.",
      };
    case "ESCALATE_TO_HUMAN":
      return {
        title: "Escalated for human handling",
        description:
          "The request stays open as escalated until someone resolves it.",
      };
    case "NO_ACTION":
      return {
        title: "Closed with no action",
        description: "No further action is required for this request.",
      };
    default:
      return {
        title: "Action approved",
        description: "The recommendation was approved.",
      };
  }
}

/** Whether the approved action has a resident message someone needs to send. */
export function hasResponseToSend(action: ProposedAction): boolean {
  return (
    action.status === "APPROVED" &&
    action.actionType !== "NO_ACTION"
  );
}

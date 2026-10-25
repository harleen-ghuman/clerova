import type { ProposedAction } from "../types/ProposedAction";

export interface DecisionCopy {
  title: string;
  description: string;
}

/**
 * User-facing description of a reviewed proposed action.
 *
 * @param canEmailReply whether the response can be emailed from Clerova;
 *                      otherwise the reviewer copies and sends it
 */
export function decisionCopy(
  action: ProposedAction,
  canEmailReply = false
): DecisionCopy {
  if (action.status === "REJECTED") {
    return {
      title: "Action rejected",
      description: "The recommendation was rejected and no action was executed.",
    };
  }

  if (action.status === "EXECUTED") {
    return {
      title: "Reply sent",
      description: "The approved response was emailed to the sender.",
    };
  }

  const sendInstruction = canEmailReply
    ? "Send the approved response below to the sender."
    : "Copy the approved response and send it to the resident.";

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
        description: `${sendInstruction} The request has been closed.`,
      };
    case "REQUEST_MORE_INFORMATION":
      return {
        title: "Information request approved",
        description: canEmailReply
          ? "Send the approved message below. When the sender replies, the request comes back for review automatically."
          : "Copy the approved message and send it. The request stays open until you resolve it.",
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

/** Whether the reviewed action has a message for the sender. */
export function hasResponseToSend(action: ProposedAction): boolean {
  return (
    (action.status === "APPROVED" || action.status === "EXECUTED") &&
    action.actionType !== "NO_ACTION"
  );
}

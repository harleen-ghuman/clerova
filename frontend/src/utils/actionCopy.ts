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
      title: "Suggestion dismissed",
      description: "Nothing was sent and no job was created.",
    };
  }

  if (action.status === "EXECUTED") {
    switch (action.actionType) {
      case "CREATE_MAINTENANCE_REQUEST":
        return {
          title: "Reply sent and job created",
          description: "The job is on your Jobs board.",
        };
      case "REQUEST_MORE_INFORMATION":
        return {
          title: "Reply sent",
          description: "When they answer, the request comes back here for review.",
        };
      case "ESCALATE_TO_HUMAN":
        return {
          title: "Reply sent and escalated",
          description: "The sender was told your team will follow up. It stays open until you close it.",
        };
      default:
        return {
          title: "Reply sent",
          description: "The request is closed. If they reply, it comes back for review.",
        };
    }
  }

  const sendInstruction = canEmailReply
    ? "Send the approved reply below."
    : "Copy the approved reply below and send it to the resident.";

  // Approved but the reply hasn't gone out (sending failed, or no email)
  const notSentYet = canEmailReply
    ? " The reply hasn't been sent yet. Send it below."
    : " Copy the reply below and send it to the resident.";

  switch (action.actionType) {
    case "CREATE_MAINTENANCE_REQUEST":
      return {
        title: "Maintenance job created",
        description: `The job is on your Jobs board.${notSentYet}`,
      };
    case "RESPOND_TO_CUSTOMER":
      return {
        title: "Reply approved",
        description: `${sendInstruction} The request has been closed.`,
      };
    case "REQUEST_MORE_INFORMATION":
      return {
        title: "Question approved",
        description: canEmailReply
          ? "Send the approved reply below. When they answer, the request comes back for review."
          : "Copy the approved reply below and send it. The request stays open until you close it.",
      };
    case "ESCALATE_TO_HUMAN":
      return {
        title: "Escalated to your team",
        description: `The request stays open until someone on your team resolves it.${notSentYet}`,
      };
    case "NO_ACTION":
      return {
        title: "Closed with no action",
        description: "Nothing else is needed for this request.",
      };
    default:
      return {
        title: "Approved",
        description: "Clerova's suggestion was approved.",
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

export type InboundMessageStatus =
  | "RECEIVED"
  | "PROCESSING"
  | "PROCESSED"
  | "ATTACHED"
  | "IGNORED"
  | "FAILED";

export interface InboundMessage {
  id: string;
  fromEmail: string | null;
  fromName: string | null;
  subject: string | null;
  preview: string;
  receivedAt: string;
  status: InboundMessageStatus;
  statusReason: string | null;
  workItemId: string | null;
  attachments: string[];
}

export interface EmailSettings {
  intakeEnabled: boolean;
  sendingEnabled: boolean;
  forwardingAddress: string | null;
  gmailConfirmation: {
    receivedAt: string;
    subject: string | null;
    text: string | null;
  } | null;
}

export interface ConversationEntry {
  id: string;
  direction: "INBOUND" | "OUTBOUND";
  from: string | null;
  to: string | null;
  subject: string | null;
  text: string | null;
  at: string;
  attachments: string[];
}

export interface WorkItemEvent {
  id: string;
  type: string;
  detail: string | null;
  actor: string | null;
  createdAt: string;
}

export type InboundMessageStatus =
  | "RECEIVED"
  | "PROCESSING"
  | "PROCESSED"
  | "ATTACHED"
  | "IGNORED"
  | "FAILED";

/** A file that came with an email. */
export interface AttachmentFile {
  id: string;
  fileName: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  /** False when only the name was kept (older or very large files). */
  available: boolean;
}

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
  files?: AttachmentFile[];
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
  /** Gets a hidden copy of each reply sent from Clerova; null means off. */
  replyCopyEmail?: string | null;
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
  files?: AttachmentFile[];
}

export interface WorkItemEvent {
  id: string;
  type: string;
  detail: string | null;
  actor: string | null;
  createdAt: string;
}

export type LoadStatus = "BOOKED" | "IN_TRANSIT" | "DELIVERED" | "INVOICED" | "PAID" | "CANCELLED";
export type FlagLevel = "FIX" | "NOTE";
export type FmcsaStatus = "ACTIVE" | "NOT_AUTHORIZED" | "NOT_FOUND";
export type ReplyKind = "STATUS" | "APPOINTMENT" | "PAPERWORK" | "RATE" | "OTHER";
export type ReplyStatus = "DRAFT" | "SENT" | "DISMISSED";

/** Amounts are in cents. */
export interface LoadSummary {
  id: string;
  loadNumber: string | null;
  status: LoadStatus;
  brokerName: string | null;
  origin: string | null;
  destination: string | null;
  pickupAppointment: string | null;
  deliveryAppointment: string | null;
  rateCents: number | null;
  openFlags: number;
  fixFlags: number;
  draftReplies: number;
  statusNote: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface BrokerInfo {
  id: string;
  name: string;
  mcNumber: string | null;
  email: string | null;
  phone: string | null;
  fmcsaStatus: FmcsaStatus | null;
  fmcsaName: string | null;
  fmcsaCheckedAt: string | null;
}

export interface LoadFlag {
  id: string;
  code: string;
  level: FlagLevel;
  title: string;
  detail: string | null;
  sourceText: string | null;
  resolvedAt: string | null;
}

export interface LoadFile {
  id: string;
  name: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  stored: boolean;
}

export interface ConversationEntry {
  id: string;
  incoming: boolean;
  from: string | null;
  to: string | null;
  subject: string | null;
  body: string | null;
  at: string | null;
  files: LoadFile[];
}

export interface LoadReply {
  id: string;
  toEmail: string;
  subject: string | null;
  kind: ReplyKind;
  summary: string | null;
  body: string;
  status: ReplyStatus;
  createdAt: string;
  sentAt: string | null;
}

export type DocumentKind =
  | "RATE_CON"
  | "BOL"
  | "POD"
  | "LUMPER_RECEIPT"
  | "SCALE_TICKET"
  | "INVOICE"
  | "OTHER";

/** A document on a load: from an email filed under it, or uploaded. */
export interface LoadDocument {
  id: string;
  source: "EMAIL" | "UPLOAD";
  kind: DocumentKind | null;
  name: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  stored: boolean;
  at: string | null;
}

/** An uploaded document; loadId is null until it's filed. */
export interface UploadedDocument {
  id: string;
  loadId: string | null;
  kind: DocumentKind;
  name: string;
  contentType: string | null;
  sizeBytes: number;
  at: string;
  /** An uploaded rate con made this load. */
  newLoad: boolean;
}

export interface LoadDetail {
  id: string;
  loadNumber: string | null;
  status: LoadStatus;
  broker: BrokerInfo | null;
  equipment: string | null;
  rateCents: number | null;
  agreedRateCents: number | null;
  detentionFreeMinutes: number | null;
  detentionRateCents: number | null;
  detentionRule: string | null;
  tonuCents: number | null;
  paymentTerms: string | null;
  paperworkRule: string | null;
  rateConFile: string | null;
  stops: { kind: "PICKUP" | "DELIVERY"; place: string | null; appointment: string | null }[];
  flags: LoadFlag[];
  statusNote: string | null;
  draft: LoadReply | null;
  conversation: ConversationEntry[];
  documents: LoadDocument[];
  createdAt: string;
  updatedAt: string;
}

export interface BrokerSummary {
  broker: BrokerInfo;
  loads: number;
  openLoads: number;
}

export type LoadStatus = "BOOKED" | "IN_TRANSIT" | "DELIVERED" | "INVOICED" | "PAID" | "CANCELLED";
export type FlagLevel = "FIX" | "NOTE";
export type FmcsaStatus = "ACTIVE" | "NOT_AUTHORIZED" | "NOT_FOUND";
export type ReplyKind = "STATUS" | "APPOINTMENT" | "PAPERWORK" | "RATE" | "OTHER" | "DETENTION" | "INVOICE";
export type ReplyStatus = "DRAFT" | "SENT" | "DISMISSED";

/** Amounts are in cents. */
export interface LoadSummary {
  /** USD or CAD, from the rate con. */
  currency: "USD" | "CAD";
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
  /** PO, BOL and other numbers from the rate con, for search. */
  referenceNumbers?: string | null;
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
  /** USD or CAD, from the rate con. */
  currency: "USD" | "CAD";
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
  detentionMaxCents: number | null;
  quickPayPercent: number | null;
  invoiceEmail: string | null;
  stops: LoadStop[];
  flags: LoadFlag[];
  statusNote: string | null;
  draft: LoadReply | null;
  conversation: ConversationEntry[];
  documents: LoadDocument[];
  charges: LoadCharge[];
  /** The load's current invoice (not a voided one). */
  invoice: Invoice | null;
  createdAt: string;
  updatedAt: string;
}

export interface BrokerSummary {
  broker: BrokerInfo;
  loads: number;
  openLoads: number;
}

/** Detention so far at a stop, worked out from the logged times. */
export interface StopDetention {
  minutesOnSite: number;
  freeMinutes: number;
  billableMinutes: number;
  amountCents: number | null;
  freeEndsAt: string;
}

export interface LoadStop {
  id: string;
  kind: "PICKUP" | "DELIVERY";
  place: string | null;
  appointment: string | null;
  arrivedAt: string | null;
  leftAt: string | null;
  detention: StopDetention | null;
  /** Arrival and detention notices emailed about this stop, oldest first. */
  emails?: SentEmail[];
}

/** An email sent from a load: to whom, about what, and when. */
export interface SentEmail {
  toEmail: string;
  subject: string | null;
  sentAt: string | null;
}

export type ChargeKind = "DETENTION" | "LUMPER" | "TONU" | "LAYOVER" | "OTHER";
export type ChargeStatus = "TO_CLAIM" | "CLAIMED" | "APPROVED" | "DENIED";

export interface LoadCharge {
  id: string;
  kind: ChargeKind;
  label: string;
  description: string | null;
  amountCents: number;
  status: ChargeStatus;
  stopId: string | null;
  claimedAt: string | null;
  /** Where the claim was emailed from Clerova, if it was. */
  claimEmail?: SentEmail | null;
}

export type InvoiceStatus = "DRAFT" | "SENT" | "PAID" | "VOID";

export interface InvoiceLine {
  description: string;
  amountCents: number;
}

export interface Invoice {
  /** USD or CAD, from the rate con. */
  currency: "USD" | "CAD";
  id: string;
  loadId: string;
  number: string;
  status: InvoiceStatus;
  sendTo: "FACTOR" | "BROKER";
  billTo: string | null;
  remitTo: string | null;
  lines: InvoiceLine[];
  totalCents: number;
  quickPay: boolean;
  quickPayFeeCents: number | null;
  sentVia: "EMAIL" | "PORTAL" | null;
  sentEmail: string | null;
  sentAt: string | null;
  /** YYYY-MM-DD */
  dueDate: string | null;
  paidAt: string | null;
  paidCents: number | null;
  createdAt: string;
  loadNumber: string | null;
  brokerName: string | null;
  origin: string | null;
  destination: string | null;
  /** Only on one invoice (not in lists): what the packet holds and lacks. */
  packet: { name: string | null; kind: DocumentKind | null }[] | null;
  missingPaperwork: string[] | null;
  suggestedEmail: string | null;
  /** The carrier uses a factoring company, so the invoice can go to either. */
  factoring: boolean;
}

export interface CarrierDetails {
  legalName: string | null;
  mcNumber: string | null;
  dotNumber: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  invoicePrefix: string | null;
  nextInvoiceNumber: number;
  usesFactoring: boolean;
  factorName: string | null;
  factorAddress: string | null;
  factorEmail: string | null;
  /** What invoices need that isn't filled in, in plain words. */
  missing: string[];
}

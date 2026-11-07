import type {
  BrokerSummary,
  CarrierDetails,
  ChargeKind,
  ChargeStatus,
  Invoice,
  InvoiceLine,
  DocumentKind,
  LoadDetail,
  LoadStatus,
  LoadSummary,
  UploadedDocument,
} from "../types/Load";
import { apiRequest } from "./client";

export function getLoads(): Promise<LoadSummary[]> {
  return apiRequest("/api/loads");
}

export function getLoad(id: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${id}`);
}

/** Leave either out to keep it; an empty note clears it. */
export function updateLoad(
  id: string,
  change: { status?: LoadStatus; statusNote?: string }
): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${id}`, { method: "PATCH", body: change });
}

export function resolveFlag(loadId: string, flagId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/flags/${flagId}/resolve`, { method: "POST" });
}

export function sendReply(loadId: string, replyId: string, body: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/replies/${replyId}/send`, {
    method: "POST",
    body: { body },
  });
}

export function dismissReply(loadId: string, replyId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/replies/${replyId}/dismiss`, { method: "POST" });
}

export function getBrokers(): Promise<BrokerSummary[]> {
  return apiRequest("/api/brokers");
}

export interface NewLoad {
  loadNumber: string | null;
  brokerName: string;
  brokerEmail: string | null;
  brokerMc: string | null;
  rate: string | null;
  equipment: string | null;
  stops: { kind: "PICKUP" | "DELIVERY"; place: string; appointment: string | null }[];
  detentionFreeHours: string | null;
  detentionRatePerHour: string | null;
  tonu: string | null;
  paymentTerms: string | null;
  invoiceEmail: string | null;
  /** The Inbox email it comes from; it's filed under the new load. */
  fromEmailId: string | null;
}

/** A load typed in by hand. Counts toward the plan like a rate con. */
export function createLoad(load: NewLoad): Promise<LoadDetail> {
  return apiRequest("/api/loads", { method: "POST", body: load });
}

/** Files an email from the Inbox under a load. */
export function fileEmail(loadId: string, messageId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/emails/${messageId}`, { method: "POST" });
}

/** Uploads a PDF or photo; without a load, Clerova files it if it can tell which. */
export function uploadDocument(file: File, loadId?: string): Promise<UploadedDocument> {
  const form = new FormData();
  form.append("file", file);

  if (loadId) {
    form.append("loadId", loadId);
  }

  return apiRequest("/api/load-documents", { method: "POST", body: form });
}

export function updateDocument(
  id: string,
  change: { loadId?: string; kind?: DocumentKind }
): Promise<UploadedDocument> {
  return apiRequest(`/api/load-documents/${id}`, { method: "PATCH", body: change });
}

export function deleteDocument(id: string): Promise<void> {
  return apiRequest(`/api/load-documents/${id}`, { method: "DELETE" });
}

/** Uploads waiting to be filed under a load. */
export function getUnfiledDocuments(): Promise<UploadedDocument[]> {
  return apiRequest("/api/load-documents");
}

/** Sets a broker's MC by hand; Clerova checks it with FMCSA. */
export function setBrokerMc(brokerId: string, mcNumber: string): Promise<BrokerSummary["broker"]> {
  return apiRequest(`/api/brokers/${brokerId}/mc-number`, { method: "PUT", body: { mcNumber } });
}

/** Logs when the truck arrived and left a stop; null clears a time. */
export function setStopTimes(
  loadId: string,
  stopId: string,
  times: { arrivedAt: string | null; leftAt: string | null }
): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/stops/${stopId}/times`, { method: "PUT", body: times });
}

export function addCharge(
  loadId: string,
  charge: { kind: ChargeKind; description?: string; amountCents: number; status?: ChargeStatus }
): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/charges`, { method: "POST", body: charge });
}

export function updateCharge(
  loadId: string,
  chargeId: string,
  change: { description?: string; amountCents?: number; status?: ChargeStatus }
): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/charges/${chargeId}`, { method: "PATCH", body: change });
}

export function deleteCharge(loadId: string, chargeId: string): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/charges/${chargeId}`, { method: "DELETE" });
}

/** Emails the broker about the load (a detention notice or claim). */
export function sendLoadMessage(
  loadId: string,
  message: { to?: string; subject: string; body: string; chargeId?: string; stopId?: string }
): Promise<LoadDetail> {
  return apiRequest(`/api/loads/${loadId}/messages`, { method: "POST", body: message });
}

export function getCarrier(): Promise<CarrierDetails> {
  return apiRequest("/api/carrier");
}

export function saveCarrier(details: Omit<CarrierDetails, "missing">): Promise<CarrierDetails> {
  return apiRequest("/api/carrier", { method: "PUT", body: details });
}

export function getInvoices(): Promise<Invoice[]> {
  return apiRequest("/api/invoices");
}

export function createInvoice(loadId: string): Promise<Invoice> {
  return apiRequest(`/api/loads/${loadId}/invoice`, { method: "POST" });
}

export function updateInvoice(
  id: string,
  change: { lines?: InvoiceLine[]; quickPay?: boolean; sendTo?: Invoice["sendTo"]; rebuild?: boolean }
): Promise<Invoice> {
  return apiRequest(`/api/invoices/${id}`, { method: "PATCH", body: change });
}

export function sendInvoice(id: string, to: string, message: string): Promise<Invoice> {
  return apiRequest(`/api/invoices/${id}/send`, { method: "POST", body: { to, message } });
}

/** Uploaded to the factor's website by hand (or sent some other way). */
export function markInvoiceSent(id: string): Promise<Invoice> {
  return apiRequest(`/api/invoices/${id}/mark-sent`, { method: "POST" });
}

export function markInvoicePaid(id: string, paidCents?: number): Promise<Invoice> {
  return apiRequest(`/api/invoices/${id}/paid`, { method: "POST", body: { paidCents: paidCents ?? null } });
}

export function markInvoiceUnpaid(id: string): Promise<Invoice> {
  return apiRequest(`/api/invoices/${id}/unpaid`, { method: "POST" });
}

export function voidInvoice(id: string): Promise<Invoice> {
  return apiRequest(`/api/invoices/${id}/void`, { method: "POST" });
}

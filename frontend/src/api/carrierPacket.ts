import { apiRequest } from "./client";

export type CarrierDocumentKind = "AUTHORITY" | "INSURANCE" | "W9" | "NOA" | "VOID_CHECK" | "OTHER";

/** One of the carrier's own documents for a broker's carrier packet. */
export interface CarrierDocument {
  id: string;
  kind: CarrierDocumentKind;
  kindLabel: string;
  name: string;
  contentType: string | null;
  sizeBytes: number;
  /** YYYY-MM-DD, like an insurance certificate's end date. */
  expiresOn: string | null;
  expired: boolean;
  at: string;
}

/** What a carrier packet usually holds, in the order brokers expect. */
export const CARRIER_DOCUMENT_KINDS: { kind: CarrierDocumentKind; label: string; hint: string }[] = [
  { kind: "AUTHORITY", label: "MC authority letter", hint: "Your operating authority from FMCSA" },
  { kind: "INSURANCE", label: "Certificate of insurance", hint: "Add the date it ends, so Clerova can remind you" },
  { kind: "W9", label: "W-9", hint: "Signed, with your EIN" },
  { kind: "NOA", label: "Notice of assignment (factoring)", hint: "If you use a factoring company" },
  { kind: "VOID_CHECK", label: "Voided check", hint: "For brokers who pay you directly" },
  { kind: "OTHER", label: "Other", hint: "Anything else brokers ask for" },
];

export function listCarrierDocuments(): Promise<CarrierDocument[]> {
  return apiRequest("/api/carrier/documents");
}

export function uploadCarrierDocument(file: File, kind: CarrierDocumentKind, expiresOn: string | null): Promise<CarrierDocument> {
  const form = new FormData();
  form.append("file", file);
  form.append("kind", kind);

  if (expiresOn) {
    form.append("expiresOn", expiresOn);
  }

  return apiRequest("/api/carrier/documents", { method: "POST", body: form });
}

/** expiresOn "" clears the date. */
export function updateCarrierDocument(id: string, change: { kind?: CarrierDocumentKind; expiresOn?: string }): Promise<CarrierDocument> {
  return apiRequest(`/api/carrier/documents/${id}`, { method: "PATCH", body: change });
}

export function deleteCarrierDocument(id: string): Promise<void> {
  return apiRequest(`/api/carrier/documents/${id}`, { method: "DELETE" });
}

/** Emails the whole packet, as one PDF, to a broker. */
export function sendCarrierPacket(to: string, message?: string): Promise<{ sent: boolean }> {
  return apiRequest("/api/carrier/packet/send", { method: "POST", body: { to, message } });
}

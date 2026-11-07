import { ApiRequestError } from "../api/client";
import type { Me } from "../types/Account";
import type { EmailSettings, InboundMessage } from "../types/Email";
import type {
  BrokerInfo,
  BrokerSummary,
  CarrierDetails,
  ChargeKind,
  ChargeStatus,
  ConversationEntry,
  DocumentKind,
  Invoice,
  InvoiceLine,
  LoadCharge,
  LoadDetail,
  LoadDocument,
  LoadFlag,
  LoadReply,
  LoadStatus,
  LoadStop,
  LoadSummary,
  UploadedDocument,
} from "../types/Load";
import type { CarrierDocument, CarrierDocumentKind } from "../api/carrierPacket";
import { REFRESH_EVENT } from "./mode";
import { makePdf, PageWriter } from "./pdf";

/*
 * The trucking demo's stand-in for the Clerova API, kept in the browser.
 * It follows the server's rules (detention, charges, invoices), but the
 * "AI" answers are pre-written and nothing is ever sent.
 */

const FORWARDING = "sierra-run-k3j9x2@in.clerova-ai.com";
const loadedAt = Date.now();
let counter = 0;

const minutesAgo = (minutes: number) => new Date(loadedAt - minutes * 60_000).toISOString();
const now = () => new Date().toISOString();
const id = (prefix: string) => `${prefix}-${++counter}`;
const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));
/** A sample time that matches the logged times, like "Nov 5, 11:20 AM". */
const clockAt = (minutes: number) =>
  new Date(loadedAt - minutes * 60_000).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
const money = (cents: number) =>
  (cents / 100).toLocaleString("en-US", { style: "currency", currency: "USD" });

interface DemoLoad {
  id: string;
  loadNumber: string | null;
  status: LoadStatus;
  brokerId: string;
  equipment: string;
  rateCents: number;
  agreedRateCents: number | null;
  detentionFreeMinutes: number | null;
  detentionRateCents: number | null;
  detentionMaxCents: number | null;
  detentionRule: string | null;
  tonuCents: number | null;
  paymentTerms: string | null;
  quickPayPercent: number | null;
  invoiceEmail: string | null;
  paperworkRule: string | null;
  rateConFile: string;
  stops: LoadStop[];
  flags: LoadFlag[];
  statusNote: string | null;
  replies: LoadReply[];
  /** Which stop or charge each sent email was about, by reply id (as load_replies.stop_id / charge_id). */
  links?: Record<string, { stopId?: string; chargeId?: string }>;
  conversation: ConversationEntry[];
  documents: LoadDocument[];
  charges: LoadCharge[];
  createdAt: string;
  updatedAt: string;
}

interface DemoInvoice extends Omit<Invoice, "loadNumber" | "brokerName" | "origin" | "destination" | "packet" | "missingPaperwork" | "suggestedEmail" | "factoring"> {
  organizationId: string;
}

interface State {
  me: Me;
  carrier: Omit<CarrierDetails, "missing">;
  brokers: BrokerInfo[];
  loads: DemoLoad[];
  invoices: DemoInvoice[];
  inbox: InboundMessage[];
  uploads: UploadedDocument[];
  carrierDocuments: CarrierDocument[];
  settings: EmailSettings;
  nextLoad: number;
}

// ---------------------------------------------------------------------------
// Sample data
// ---------------------------------------------------------------------------

function broker(name: string, mc: string | null, email: string, phone: string): BrokerInfo {
  return {
    id: id("broker"),
    name,
    mcNumber: mc,
    email,
    phone,
    fmcsaStatus: mc ? "ACTIVE" : null,
    fmcsaName: mc ? name.toUpperCase() : null,
    fmcsaCheckedAt: mc ? minutesAgo(600) : null,
  };
}

/** A date this many days from today, as YYYY-MM-DD. */
function inDays(days: number): string {
  const date = new Date(Date.now() + days * 86_400_000);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

const CARRIER_KIND_LABELS: Record<CarrierDocumentKind, string> = {
  AUTHORITY: "MC authority letter",
  INSURANCE: "Certificate of insurance",
  W9: "W-9",
  NOA: "Notice of assignment (factoring)",
  VOID_CHECK: "Voided check",
  OTHER: "Other",
};
const CARRIER_KIND_ORDER: CarrierDocumentKind[] = ["AUTHORITY", "INSURANCE", "W9", "NOA", "VOID_CHECK", "OTHER"];

function carrierDocument(document: CarrierDocument): CarrierDocument {
  return {
    ...document,
    kindLabel: CARRIER_KIND_LABELS[document.kind],
    expired: Boolean(document.expiresOn && document.expiresOn < inDays(0)),
  };
}

function carrierDocuments(): CarrierDocument[] {
  return [...state.carrierDocuments]
    .sort((a, b) => CARRIER_KIND_ORDER.indexOf(a.kind) - CARRIER_KIND_ORDER.indexOf(b.kind))
    .map(carrierDocument);
}

/**
 * Minutes since 11:10 PM two nights ago, local time: the sample delivery that
 * waited past midnight (left 2h 45m later), whatever time the demo is opened.
 */
const LATE_ARRIVAL = (() => {
  const arrived = new Date();
  arrived.setDate(arrived.getDate() - 2);
  arrived.setHours(23, 10, 0, 0);
  return Math.round((Date.now() - arrived.getTime()) / 60_000);
})();

/** Every rate con check done before the truck rolls, as on the server. */
function requireChecksDone(load: { flags: LoadFlag[] }) {
  const open = load.flags.filter((flag) => !flag.resolvedAt).length;
  if (open > 0) {
    throw new ApiRequestError(
      `Go through the rate con check first: ${open === 1 ? "1 item is" : `${open} items are`} still open. Mark each one done, then the load can move to In transit.`,
      409
    );
  }
}

function stop(kind: "PICKUP" | "DELIVERY", place: string, appointment: string, arrived?: number, left?: number): LoadStop {
  return {
    id: id("stop"),
    kind,
    place,
    appointment,
    arrivedAt: arrived === undefined ? null : minutesAgo(arrived),
    leftAt: left === undefined ? null : minutesAgo(left),
    detention: null,
  };
}

function flag(code: string, level: "FIX" | "NOTE", title: string, detail: string, source: string | null, resolved = false): LoadFlag {
  return { id: id("flag"), code, level, title, detail, sourceText: source, resolvedAt: resolved ? minutesAgo(900) : null };
}

function incoming(from: string, subject: string, body: string, at: number, files: string[] = []): ConversationEntry {
  return {
    id: id("msg"),
    incoming: true,
    from,
    to: null,
    subject,
    body,
    at: minutesAgo(at),
    files: files.map((name) => ({ id: id("file"), name, contentType: "application/pdf", sizeBytes: 182_000, stored: true })),
  };
}

function emailDocument(kind: DocumentKind, name: string, at: number): LoadDocument {
  return { id: id("file"), source: "EMAIL", kind, name, contentType: "application/pdf", sizeBytes: 182_000, stored: true, at: minutesAgo(at) };
}

function baseLoad(
  brokerInfo: BrokerInfo,
  loadNumber: string,
  status: LoadStatus,
  rateCents: number,
  stops: LoadStop[],
  createdMinutesAgo: number
): DemoLoad {
  return {
    id: id("load"),
    loadNumber,
    status,
    brokerId: brokerInfo.id,
    equipment: "53' Dry Van",
    rateCents,
    agreedRateCents: null,
    detentionFreeMinutes: 120,
    detentionRateCents: 5000,
    detentionMaxCents: 20000,
    detentionRule: null,
    tonuCents: 15000,
    paymentTerms: "Net 30",
    quickPayPercent: 3,
    invoiceEmail: `billing@${brokerInfo.email!.split("@")[1]}`,
    paperworkRule: "Signed BOL/POD within 24 hours of delivery",
    rateConFile: `RC_${loadNumber}.pdf`,
    stops,
    flags: [],
    statusNote: null,
    replies: [],
    conversation: [],
    documents: [],
    charges: [],
    createdAt: minutesAgo(createdMinutesAgo),
    updatedAt: minutesAgo(Math.max(1, createdMinutesAgo - 30)),
  };
}

function seed(): State {
  counter = 0;
  const crestline = broker("Crestline Freight", "845210", "dana@crestline.example", "(916) 555-0182");
  const mesa = broker("Mesa Ridge Logistics", "772301", "lee@mesaridge.example", "(775) 555-0100");
  const harbor = broker("Blue Harbor Brokerage", null, "ops@blueharbor.example", "(209) 555-0147");

  // 1. A new rate con to check before signing
  const booked = baseLoad(crestline, "88213", "BOOKED", 215_000, [
    stop("PICKUP", "Sunridge Packing, 1200 S Elm Ave, Fresno, CA 93706", "Tomorrow 08:00 - 10:00"),
    stop("DELIVERY", "Desert Foods DC, 4410 W Buckeye Rd, Phoenix, AZ 85043", "In two days, 07:00 APPT"),
  ], 25);
  booked.agreedRateCents = 225_000;
  booked.detentionRule = "Tell the broker within 30 minutes of arrival.";
  booked.flags = [
    flag("RATE_MISMATCH", "FIX", "The rate con is $100 less than agreed",
      "Dana's email says $2,250 all in. The rate con says $2,150. Ask for a corrected rate con before you sign.",
      "TOTAL CARRIER PAY .... $2,150.00"),
    flag("DETENTION_CONDITION", "NOTE", "Detention pay has a condition",
      "To get paid for detention: tell the broker within 30 minutes of arrival.",
      "Detention: $50.00/hr after 2 hours free, max $200.00 per stop. Carrier must notify broker within 30 minutes of arrival."),
  ];
  booked.conversation = [
    incoming("Dana Ruiz <dana@crestline.example>", "Rate Confirmation 88213",
      "Hi,\n\nConfirming 88213 Fresno to Phoenix at $2,250 all in. Rate con attached, please sign and return.\n\nDana", 25, ["RC_88213.pdf"]),
  ];
  booked.documents = [emailDocument("RATE_CON", "RC_88213.pdf", 25)];

  // 2. On the road: waiting at delivery, and the broker wants an ETA
  const transit = baseLoad(mesa, "MR-5002", "IN_TRANSIT", 140_000, [
    stop("PICKUP", "Valley Fresh Farms, 88 Road 24, Madera, CA 93637", clockAt(430), 420, 370),
    stop("DELIVERY", "Cold Hub, 1200 Port Rd, Oakland, CA 94607", `${clockAt(170)} APPT`, 160),
  ], 1500);
  transit.equipment = "Reefer";
  transit.statusNote = `At Cold Hub since ${clockAt(160)}, waiting for a door.`;
  transit.flags = [flag("DETENTION_CONDITION", "NOTE", "Detention needs written approval",
    "To get paid for detention: get the broker's approval in writing while you wait.", null, true)];
  transit.conversation = [
    incoming("Lee Park <lee@mesaridge.example>", "Load MR-5002", "Sign and return please.", 1500, ["RC_MR-5002.pdf"]),
    incoming("Lee Park <lee@mesaridge.example>", "Re: Load MR-5002", "Hi, receiver is asking when you'll be unloaded. Any update?", 12),
  ];
  transit.documents = [emailDocument("RATE_CON", "RC_MR-5002.pdf", 1500)];
  transit.replies = [{
    id: id("reply"),
    toEmail: "lee@mesaridge.example",
    subject: "Re: Load MR-5002",
    kind: "STATUS",
    summary: "Lee asks when the truck will be unloaded.",
    body: `Hi Lee,\n\nOur driver has been at Cold Hub since ${clockAt(160)} and is waiting for a door. We'll let you know as soon as we're unloaded. Detention started at ${clockAt(40)}.\n\nThanks,`,
    status: "DRAFT",
    createdAt: minutesAgo(11),
    sentAt: null,
  }];

  // 3. Delivered: detention to claim, ready to invoice
  const delivered = baseLoad(harbor, "BH-7781", "DELIVERED", 185_000, [
    stop("PICKUP", "Delta Paper Co, 500 Industrial Dr, Stockton, CA 95206", clockAt(LATE_ARRIVAL + 410), LATE_ARRIVAL + 400, LATE_ARRIVAL + 340),
    stop("DELIVERY", "Sierra Supply, 2600 E 4th St, Reno, NV 89512", `${clockAt(LATE_ARRIVAL + 10)} APPT`, LATE_ARRIVAL, LATE_ARRIVAL - 165),
  ], LATE_ARRIVAL + 1200);
  delivered.paymentTerms = "Net 31";
  delivered.quickPayPercent = 2.5;
  delivered.flags = [flag("MC_MISSING", "NOTE", "No MC number for the broker",
    "The rate con doesn't print the broker's MC, so Clerova couldn't check it with FMCSA. Add it on the Brokers page.", null, true)];
  delivered.conversation = [
    incoming("Blue Harbor Ops <ops@blueharbor.example>", "Load BH-7781 confirmation", "Rate con attached.", LATE_ARRIVAL + 1200, ["RC_BH-7781.pdf"]),
    incoming("Sam Ortiz <dispatch@sierrarun.example>", "Fwd: POD BH-7781", "Signed POD from the driver.", 1100, ["POD_BH-7781.pdf"]),
  ];
  delivered.documents = [emailDocument("RATE_CON", "RC_BH-7781.pdf", LATE_ARRIVAL + 1200), emailDocument("POD", "POD_BH-7781.pdf", 1100)];

  // 4. Invoiced and waiting on payment
  const invoiced = baseLoad(crestline, "87990", "INVOICED", 240_000, [
    stop("PICKUP", "Golden Grain Mill, 31 Ave 280, Visalia, CA 93277", clockAt(17_410), 17_400, 17_330),
    stop("DELIVERY", "Strip Foods Warehouse, 5100 W Sunset Rd, Las Vegas, NV 89118", `${clockAt(16_810)} APPT`, 16_800, 16_750),
  ], 18_000);
  invoiced.documents = [emailDocument("RATE_CON", "RC_87990.pdf", 18_000), emailDocument("BOL", "BOL_87990.pdf", 16_700)];

  // 5. Paid
  const paid = baseLoad(mesa, "MR-4950", "PAID", 165_000, [
    stop("PICKUP", "Valley Fresh Farms, 88 Road 24, Madera, CA 93637", clockAt(30_010), 30_000, 29_900),
    stop("DELIVERY", "Bay Produce, 300 Alameda St, San Jose, CA 95112", `${clockAt(29_710)} APPT`, 29_700, 29_640),
  ], 31_000);
  paid.equipment = "Reefer";
  paid.documents = [emailDocument("RATE_CON", "RC_MR-4950.pdf", 31_000), emailDocument("POD", "POD_MR-4950.pdf", 29_600)];

  const loads = [booked, transit, delivered, invoiced, paid];
  loads.forEach(refreshDetention);
  syncDetention(delivered, delivered.stops[1], true);

  const invoice = (load: DemoLoad, number: string, status: Invoice["status"], sentMinutes: number, paidMinutes?: number): DemoInvoice => ({
    id: id("invoice"),
    organizationId: "demo",
    loadId: load.id,
    number,
    status,
    sendTo: "BROKER",
    billTo: null,
    remitTo: null,
    lines: [{ description: `Line haul, load ${load.loadNumber}`, amountCents: load.rateCents }],
    totalCents: load.rateCents,
    quickPay: false,
    quickPayFeeCents: null,
    sentVia: "EMAIL",
    sentEmail: load.invoiceEmail,
    sentAt: minutesAgo(sentMinutes),
    dueDate: dateOnly(new Date(loadedAt - sentMinutes * 60_000 + 30 * 86_400_000)),
    paidAt: paidMinutes === undefined ? null : minutesAgo(paidMinutes),
    paidCents: paidMinutes === undefined ? null : load.rateCents,
    createdAt: minutesAgo(sentMinutes + 5),
  });

  const state: State = {
    me: {
      userId: "demo-user",
      email: "sam@sierrarun.example",
      fullName: "Sam Ortiz",
      organization: {
        id: "demo-trucking",
        name: "Sierra Run Transport",
        industry: "TRUCKING",
        plan: "TRIAL",
        trialEndsAt: null,
        aiMonthlyLimit: 500,
        aiRequestsUsedThisMonth: 5,
        subscriptionStatus: null,
        billingInterval: null,
        currentPeriodEnd: null,
        cancelAtPeriodEnd: false,
        foundingCustomer: false,
        billingEnabled: false,
        subscriptionRequired: false,
      },
    },
    carrier: {
      legalName: "Sierra Run Transport LLC",
      mcNumber: "1048817",
      dotNumber: "3311200",
      address: "410 W Main St\nVisalia, CA 93291",
      phone: "(559) 555-0142",
      email: "billing@sierrarun.example",
      invoicePrefix: "SR-",
      nextInvoiceNumber: 1042,
      usesFactoring: false,
      factorName: null,
      factorAddress: null,
      factorEmail: null,
    },
    brokers: [crestline, mesa, harbor],
    loads,
    invoices: [invoice(invoiced, "SR-1041", "SENT", 12 * 1440), invoice(paid, "SR-1040", "PAID", 20 * 1440, 6 * 1440)],
    inbox: [],
    carrierDocuments: [
      { id: "carrier-authority", kind: "AUTHORITY", kindLabel: "MC authority letter", name: "MC authority 1048817.pdf", contentType: "application/pdf", sizeBytes: 81_204, expiresOn: null, expired: false, at: minutesAgo(40_000) },
      { id: "carrier-insurance", kind: "INSURANCE", kindLabel: "Certificate of insurance", name: "COI 2026.pdf", contentType: "application/pdf", sizeBytes: 152_330, expiresOn: inDays(24), expired: false, at: minutesAgo(39_000) },
      { id: "carrier-w9", kind: "W9", kindLabel: "W-9", name: "W9 Sierra Run.pdf", contentType: "application/pdf", sizeBytes: 64_918, expiresOn: null, expired: false, at: minutesAgo(38_000) },
    ],
    uploads: [],
    settings: { intakeEnabled: true, sendingEnabled: true, forwardingAddress: FORWARDING, gmailConfirmation: null, replyCopyEmail: null },
    nextLoad: 0,
  };

  state.inbox = [
    inbox(booked.conversation[0], "PROCESSED", "Made load 88213.", booked.id),
    inbox(transit.conversation[1], "ATTACHED", null, transit.id),
    inbox(delivered.conversation[1], "ATTACHED", null, delivered.id),
    {
      id: id("in"), fromEmail: "news@loadboardweekly.example", fromName: "Load Board Weekly", subject: "Spot rates this week",
      preview: "Van rates rose 3 cents a mile in the West...", receivedAt: minutesAgo(300), status: "IGNORED",
      statusReason: "Automated or bulk email.", workItemId: null, loadId: null, attachments: [],
    },
    {
      id: id("in"), fromEmail: "driver.mike@example.com", fromName: "Mike (driver)", subject: "Lumper receipt",
      preview: "Here's the lumper receipt from this morning, $185.", receivedAt: minutesAgo(45), status: "IGNORED",
      statusReason: "Clerova couldn't tell which load this email is about.", workItemId: null, loadId: null,
      attachments: ["lumper-receipt.jpg"],
    },
  ];

  return state;
}

function inbox(entry: ConversationEntry, status: InboundMessage["status"], reason: string | null, loadId: string): InboundMessage {
  const match = entry.from?.match(/^(.*) <(.+)>$/);
  return {
    id: entry.id,
    fromEmail: match?.[2] ?? entry.from,
    fromName: match?.[1] ?? null,
    subject: entry.subject,
    preview: (entry.body ?? "").slice(0, 140),
    receivedAt: entry.at!,
    status,
    statusReason: reason,
    workItemId: null,
    loadId,
    attachments: entry.files.map((file) => file.name ?? "file"),
  };
}

let state = seed();

// ---------------------------------------------------------------------------
// Rules, as on the server
// ---------------------------------------------------------------------------

function dateOnly(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function refreshDetention(load: DemoLoad) {
  for (const item of load.stops) {
    if (!item.arrivedAt) {
      item.detention = null;
      continue;
    }

    const free = load.detentionFreeMinutes ?? 120;
    const end = item.leftAt ? new Date(item.leftAt).getTime() : Date.now();
    const onSite = Math.max(0, Math.floor((end - new Date(item.arrivedAt).getTime()) / 60_000));
    const billable = Math.floor(Math.max(0, onSite - free) / 15) * 15;
    let amount: number | null = null;

    if (load.detentionRateCents !== null) {
      amount = Math.round((load.detentionRateCents * billable) / 60);
      if (load.detentionMaxCents !== null) amount = Math.min(amount, load.detentionMaxCents);
    }

    item.detention = {
      minutesOnSite: onSite,
      freeMinutes: free,
      billableMinutes: billable,
      amountCents: amount,
      freeEndsAt: new Date(new Date(item.arrivedAt).getTime() + free * 60_000).toISOString(),
    };
  }
}

function hours(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h === 0 ? `${m}m` : m === 0 ? `${h}h` : `${h}h ${m}m`;
}

function placeName(place: string | null): string {
  const first = (place ?? "the stop").split("\n")[0];
  const comma = first.indexOf(",");
  return comma > 3 ? first.slice(0, comma) : first;
}

function invoiceSent(load: DemoLoad): boolean {
  return state.invoices.some((invoice) => invoice.loadId === load.id && (invoice.status === "SENT" || invoice.status === "PAID"));
}

/** seeding is true while the sample data is made, before there are invoices to check. */
function syncDetention(load: DemoLoad, item: LoadStop, seeding = false) {
  refreshDetention(load);
  const charge = load.charges.find((candidate) => candidate.kind === "DETENTION" && candidate.stopId === item.id);

  if ((charge && charge.status !== "TO_CLAIM") || (!seeding && invoiceSent(load))) {
    return;
  }

  const detention = item.leftAt ? item.detention : null;

  if (!detention || detention.billableMinutes === 0 || detention.amountCents === null) {
    load.charges = load.charges.filter((candidate) => candidate !== charge);
    return;
  }

  const description = `${placeName(item.place)}, ${hours(detention.minutesOnSite)} on site, ${hours(detention.freeMinutes)} free`;

  if (charge) {
    charge.amountCents = detention.amountCents;
    charge.description = description;
  } else {
    load.charges.push({
      id: id("charge"), kind: "DETENTION", label: "Detention", description, amountCents: detention.amountCents,
      status: "TO_CLAIM", stopId: item.id, claimedAt: null,
    });
  }
}

const CHARGE_LABELS: Record<ChargeKind, string> = {
  DETENTION: "Detention",
  LUMPER: "Lumper",
  TONU: "Truck ordered, not used",
  LAYOVER: "Layover",
  OTHER: "Other",
};

function chargesLocked(load: DemoLoad) {
  if (invoiceSent(load)) {
    conflict("The invoice was already sent, so its charges can't change. Void the invoice first.");
  }
}

function conflict(message: string): never {
  throw new ApiRequestError(message, 409);
}

function invalid(message: string): never {
  throw new ApiRequestError(message, 400);
}

function findLoad(loadId: string): DemoLoad {
  const load = state.loads.find((candidate) => candidate.id === loadId);
  if (!load) throw new ApiRequestError("Load not found", 404);
  return load;
}

function findBroker(load: DemoLoad): BrokerInfo {
  return state.brokers.find((candidate) => candidate.id === load.brokerId)!;
}

function touch(load: DemoLoad) {
  load.updatedAt = now();
}

function currentInvoice(load: DemoLoad): DemoInvoice | undefined {
  return state.invoices.find((invoice) => invoice.loadId === load.id && invoice.status !== "VOID");
}

function missingCarrierDetails(): string[] {
  const carrier = state.carrier;
  const missing: string[] = [];
  if (!carrier.legalName) missing.push("your company name");
  if (!carrier.address) missing.push("your address");
  if (!carrier.mcNumber && !carrier.dotNumber) missing.push("your MC or DOT number");
  if (carrier.usesFactoring) {
    if (!carrier.factorName) missing.push("the factoring company's name");
    if (!carrier.factorAddress) missing.push("where the factoring company is paid");
  }
  return missing;
}

function startingLines(load: DemoLoad): InvoiceLine[] {
  const lines: InvoiceLine[] = [];

  if (load.status !== "CANCELLED") {
    lines.push({ description: `Line haul, load ${load.loadNumber}`, amountCents: load.rateCents });
  }

  for (const charge of load.charges) {
    if (charge.status === "APPROVED") {
      lines.push({ description: charge.description ? `${charge.label}: ${charge.description}` : charge.label, amountCents: charge.amountCents });
    }
  }

  return lines.length > 0 ? lines : [{ description: "Truck ordered, not used", amountCents: load.tonuCents ?? 0 }];
}

function quickPayFee(invoice: DemoInvoice, load: DemoLoad): number | null {
  return invoice.quickPay && load.quickPayPercent !== null ? Math.round((invoice.totalCents * load.quickPayPercent) / 100) : null;
}

function invoiceInfo(invoice: DemoInvoice, withPacket: boolean): Invoice {
  const load = findLoad(invoice.loadId);
  const brokerInfo = findBroker(load);
  const carrier = state.carrier;
  const packetDocs = packetDocuments(load);
  const kinds = new Set(packetDocs.map((document) => document.kind));
  const missing: string[] = [];

  if (!kinds.has("RATE_CON")) missing.push("rate con");
  if (load.status !== "CANCELLED" && !kinds.has("BOL") && !kinds.has("POD")) missing.push("signed BOL or POD");
  if (load.charges.some((charge) => charge.kind === "LUMPER" && charge.status === "APPROVED") && !kinds.has("LUMPER_RECEIPT")) {
    missing.push("lumper receipt");
  }

  return {
    ...invoice,
    billTo: invoice.status === "DRAFT" || !invoice.billTo
      ? [brokerInfo.name, brokerInfo.mcNumber ? `MC ${brokerInfo.mcNumber}` : null, load.invoiceEmail ?? brokerInfo.email].filter(Boolean).join("\n")
      : invoice.billTo,
    remitTo: invoice.status === "DRAFT" || !invoice.remitTo
      ? invoice.sendTo === "FACTOR"
        ? `${carrier.factorName}\n${carrier.factorAddress ?? ""}`
        : `${carrier.legalName}\n${carrier.address ?? ""}`
      : invoice.remitTo,
    loadNumber: load.loadNumber,
    brokerName: brokerInfo.name,
    origin: load.stops[0]?.place ?? null,
    destination: load.stops.at(-1)?.place ?? null,
    packet: withPacket ? packetDocs.map((document) => ({ name: document.name, kind: document.kind })) : null,
    missingPaperwork: withPacket ? missing : null,
    suggestedEmail: withPacket
      ? invoice.sendTo === "FACTOR" ? carrier.factorEmail : load.invoiceEmail ?? brokerInfo.email
      : null,
    factoring: carrier.usesFactoring,
  };
}

const PACKET_ORDER: DocumentKind[] = ["RATE_CON", "BOL", "POD", "LUMPER_RECEIPT", "SCALE_TICKET"];

function packetDocuments(load: DemoLoad): LoadDocument[] {
  return PACKET_ORDER.flatMap((kind) => load.documents.filter((document) => document.kind === kind));
}

/** Sample PO and BOL numbers, so searching the board works like the real one. */
const DEMO_REFERENCES: Record<string, string> = {
  "88213": "PO 55120",
  "MR-5002": "PO 77812, BOL 30014",
  "BH-7781": "PO 61930",
  "87990": "BOL 28890",
  "MR-4950": "PO 70441",
};

function summary(load: DemoLoad): LoadSummary {
  const open = load.flags.filter((item) => !item.resolvedAt);
  return {
    id: load.id,
    loadNumber: load.loadNumber,
    status: load.status,
    brokerName: findBroker(load).name,
    origin: load.stops[0]?.place ?? null,
    destination: load.stops.length > 1 ? load.stops.at(-1)!.place : null,
    pickupAppointment: load.stops[0]?.appointment ?? null,
    deliveryAppointment: load.stops.length > 1 ? load.stops.at(-1)!.appointment : null,
    rateCents: load.rateCents,
    openFlags: open.length,
    fixFlags: open.filter((item) => item.level === "FIX").length,
    draftReplies: load.replies.filter((reply) => reply.status === "DRAFT").length,
    statusNote: load.statusNote,
    referenceNumbers: (load.loadNumber && DEMO_REFERENCES[load.loadNumber]) || null,
    createdAt: load.createdAt,
    updatedAt: load.updatedAt,
  };
}

function detail(load: DemoLoad): LoadDetail {
  refreshDetention(load);
  const sent: ConversationEntry[] = load.replies
    .filter((reply) => reply.status === "SENT")
    .map((reply) => ({ id: reply.id, incoming: false, from: null, to: reply.toEmail, subject: reply.subject, body: reply.body, at: reply.sentAt, files: [] }));
  const invoice = currentInvoice(load);

  return {
    id: load.id,
    loadNumber: load.loadNumber,
    status: load.status,
    broker: findBroker(load),
    equipment: load.equipment,
    rateCents: load.rateCents,
    agreedRateCents: load.agreedRateCents,
    detentionFreeMinutes: load.detentionFreeMinutes,
    detentionRateCents: load.detentionRateCents,
    detentionRule: load.detentionRule,
    tonuCents: load.tonuCents,
    paymentTerms: load.paymentTerms,
    paperworkRule: load.paperworkRule,
    rateConFile: load.rateConFile,
    detentionMaxCents: load.detentionMaxCents,
    quickPayPercent: load.quickPayPercent,
    invoiceEmail: load.invoiceEmail,
    stops: load.stops.map((stop) => ({ ...stop, emails: linkedEmails(load, "stopId", stop.id) })),
    flags: load.flags,
    statusNote: load.statusNote,
    draft: load.replies.find((reply) => reply.status === "DRAFT") ?? null,
    conversation: [...load.conversation, ...sent]
      .map((entry) => ({ ...entry, files: entry.files.map((file) => ({ ...file, id: sameDocument(load, file.name)?.id ?? file.id })) }))
      .sort((a, b) => (a.at ?? "").localeCompare(b.at ?? "")),
    documents: load.documents,
    charges: load.charges.map((charge) => ({ ...charge, claimEmail: linkedEmails(load, "chargeId", charge.id).at(-1) ?? null })),
    invoice: invoice ? invoiceInfo(invoice, true) : null,
    createdAt: load.createdAt,
    updatedAt: load.updatedAt,
  };
}

/** As on the server, an emailed file and its document share one id. */
function sameDocument(load: DemoLoad, name: string | null): LoadDocument | undefined {
  return load.documents.find((document) => document.source === "EMAIL" && document.name === name);
}

function signOff(text: string): string {
  const trimmed = text.trimEnd();
  const name = state.me.organization!.name;
  const last = trimmed.split("\n").at(-1) ?? "";
  if (last.toLowerCase().includes(name.toLowerCase())) return trimmed;
  return `${trimmed}${last.trim().endsWith(",") ? "\n" : "\n\n"}${name}`;
}

function recordSent(
  load: DemoLoad, to: string, subject: string, body: string, kind: LoadReply["kind"],
  link?: { stopId?: string; chargeId?: string }
) {
  const replyId = id("reply");
  load.replies.push({
    id: replyId, toEmail: to, subject, kind, summary: subject, body: signOff(body), status: "SENT",
    createdAt: now(), sentAt: now(),
  });
  if (link) {
    load.links = { ...(load.links ?? {}), [replyId]: link };
  }
  touch(load);
}

/** The sent emails linked to a stop or a charge, like the server's detail. */
function linkedEmails(load: DemoLoad, key: "stopId" | "chargeId", value: string) {
  return load.replies
    .filter((reply) => reply.status === "SENT" && load.links?.[reply.id]?.[key] === value)
    .map((reply) => ({ toEmail: reply.toEmail, subject: reply.subject, sentAt: reply.sentAt }));
}

function markSent(invoice: DemoInvoice, load: DemoLoad, via: "EMAIL" | "PORTAL", email: string | null) {
  if (invoice.status === "DRAFT") {
    invoice.sentAt = now();
    const days = Number(load.paymentTerms?.match(/net\s*(\d{1,3})/i)?.[1]);
    invoice.dueDate = invoice.sendTo === "BROKER" && !invoice.quickPay && days ? dateOnly(new Date(Date.now() + days * 86_400_000)) : null;
    const info = invoiceInfo(invoice, false);
    invoice.billTo = info.billTo;
    invoice.remitTo = info.remitTo;
  }
  invoice.status = "SENT";
  invoice.sentVia = via;
  invoice.sentEmail = email;
  if (load.status !== "PAID" && load.status !== "INVOICED" && load.status !== "CANCELLED") load.status = "INVOICED";
  touch(load);
}

// ---------------------------------------------------------------------------
// Files: sample rate cons, PODs and invoices drawn in the browser
// ---------------------------------------------------------------------------

function samplePaper(load: DemoLoad | undefined, kind: DocumentKind | null, name: string): PageWriter {
  const writer = new PageWriter();
  const brokerInfo = load ? findBroker(load) : null;

  if (kind === "RATE_CON" && load) {
    writer.line(brokerInfo!.name.toUpperCase(), { size: 14, bold: true });
    writer.line(`MC# ${brokerInfo!.mcNumber ?? "-"}   ${brokerInfo!.email}`, { grey: true });
    writer.space();
    writer.line(`CARRIER RATE AND LOAD CONFIRMATION     Load # ${load.loadNumber}`, { bold: true });
    writer.line(`Carrier: SIERRA RUN TRANSPORT  MC# 1048817     Equipment: ${load.equipment}`);
    writer.space();
    load.stops.forEach((item, index) => {
      writer.line(`STOP ${index + 1} - ${item.kind}   ${item.appointment ?? ""}`, { bold: true });
      writer.line(item.place ?? "");
    });
    writer.space();
    writer.row("TOTAL CARRIER PAY", money(load.rateCents), { bold: true });
    writer.space();
    writer.line("TERMS AND CONDITIONS", { bold: true });
    writer.line("1. Detention: $50.00/hr after 2 hours free, max $200.00 per stop.");
    writer.line(`2. ${load.paperworkRule ?? "Signed BOL/POD within 24 hours."}`);
    writer.line(`3. Payment terms: ${load.paymentTerms ?? "Net 30"}. Quick pay available.`);
    writer.line(`4. Email invoices with signed POD to ${load.invoiceEmail ?? "billing"}`);
  } else {
    writer.line(kind === "BOL" ? "BILL OF LADING" : kind === "POD" ? "PROOF OF DELIVERY" : name, { size: 14, bold: true });
    writer.space();
    writer.line(load ? `Load ${load.loadNumber}` : "Sample document");
    if (load) writer.line(`Delivered to ${load.stops.at(-1)?.place ?? ""}`);
    writer.space();
    writer.line("Received in good order.   Signed: M. Ortega", { grey: true });
    writer.space(20);
    writer.line("(A sample document for the Clerova demo.)", { grey: true, size: 9 });
  }

  return writer;
}

function invoicePages(invoice: DemoInvoice): PageWriter {
  const load = findLoad(invoice.loadId);
  const info = invoiceInfo(invoice, false);
  const carrier = state.carrier;
  const writer = new PageWriter();

  writer.line(carrier.legalName ?? state.me.organization!.name, { size: 16, bold: true });
  for (const line of (carrier.address ?? "").split("\n")) writer.line(line, { grey: true });
  writer.line([carrier.mcNumber && `MC ${carrier.mcNumber}`, carrier.dotNumber && `DOT ${carrier.dotNumber}`].filter(Boolean).join("  |  "), { grey: true });
  writer.space();
  writer.line(`INVOICE ${invoice.number}`, { size: 18, bold: true });
  writer.line(`Date ${new Date(invoice.sentAt ?? now()).toLocaleDateString("en-US")}   Load ${load.loadNumber}   Terms ${load.paymentTerms ?? "-"}`);
  if (invoice.dueDate) writer.line(`Due ${invoice.dueDate}`);
  writer.space();
  writer.line("BILL TO", { bold: true, size: 9, grey: true });
  for (const line of (info.billTo ?? "").split("\n")) writer.line(line);
  writer.space(6);
  writer.line("REMIT PAYMENT TO", { bold: true, size: 9, grey: true });
  for (const line of (info.remitTo ?? "").split("\n")) writer.line(line);
  writer.space();
  for (const item of load.stops) writer.line(`${item.kind === "PICKUP" ? "Pickup" : "Delivery"}: ${item.place}`);
  writer.space();
  writer.row("DESCRIPTION", "AMOUNT", { bold: true });
  for (const line of invoice.lines) writer.row(line.description, money(line.amountCents));
  writer.space(6);
  writer.row("Total", money(invoice.totalCents), { bold: true });
  if (invoice.quickPayFeeCents !== null) {
    writer.line(`Quick pay requested: pay ${money(invoice.totalCents - invoice.quickPayFeeCents)}`, { grey: true });
  }
  if (invoice.sendTo === "FACTOR") {
    writer.space();
    writer.line("Notice of assignment", { bold: true });
    writer.line(`This invoice has been sold and assigned to ${carrier.factorName}. Pay only ${carrier.factorName}.`);
  }
  writer.space(20);
  writer.line("Thank you for your business.", { grey: true, size: 9 });
  return writer;
}

/** The demo's answer to file downloads: a sample made on the spot. */
export async function demoTruckingFile(path: string): Promise<Blob> {
  await wait(250);
  let match: RegExpMatchArray | null;

  if ((match = path.match(/^\/api\/invoices\/([^/]+)\/(pdf|packet)$/))) {
    const invoice = state.invoices.find((candidate) => candidate.id === match![1]);
    if (!invoice) throw new ApiRequestError("This file isn't available.", 404);
    const pages = invoicePages(invoice).pages;

    if (match[2] === "packet") {
      const load = findLoad(invoice.loadId);
      for (const document of packetDocuments(load)) {
        pages.push(...samplePaper(load, document.kind, document.name ?? "Document").pages);
      }
    }

    return makePdf(pages);
  }

  if (path === "/api/carrier/packet" || (match = path.match(/^\/api\/carrier\/documents\/([^/]+)\/file$/))) {
    const documents = path === "/api/carrier/packet"
      ? carrierDocuments()
      : carrierDocuments().filter((document) => document.id === match![1]);
    if (documents.length === 0) throw new ApiRequestError("This file isn't available.", 404);
    const carrier = state.carrier;
    const pages = documents.map((document) => {
      const writer = new PageWriter();
      writer.line(document.kindLabel, { size: 16, bold: true });
      writer.line(carrier.legalName ?? "Sierra Run Transport LLC", { size: 12, bold: true });
      writer.line(`MC ${carrier.mcNumber ?? "-"}   DOT ${carrier.dotNumber ?? "-"}`, { grey: true });
      writer.line(document.expiresOn ? `Valid until ${document.expiresOn}` : "Sample document for the demo", { grey: true });
      return writer.pages[0];
    });
    if (path === "/api/carrier/packet") {
      const cover = new PageWriter();
      cover.line("Carrier packet", { size: 20, bold: true });
      cover.line(carrier.legalName ?? "Sierra Run Transport LLC", { size: 13, bold: true });
      documents.forEach((document, index) => cover.line(`${index + 1}. ${document.kindLabel}`));
      pages.unshift(cover.pages[0]);
    }
    return makePdf(pages);
  }

  if ((match = path.match(/^\/api\/invoices\/export\?month=(\d{4}-\d{2})$/))) {
    // Same columns as the server: each kind of pay gets its own column
    const kinds = ["Line haul", "Detention", "Lumper", "TONU", "Layover", "Other"];
    const column = (description: string) => {
      const text = description.toLowerCase();
      if (text.includes("line haul") || text.includes("linehaul") || text.startsWith("freight") || text.startsWith("rate")) return 0;
      if (text.includes("detention")) return 1;
      if (text.includes("lumper")) return 2;
      if (text.includes("tonu") || text.includes("truck ordered")) return 3;
      if (text.includes("layover")) return 4;
      return 5;
    };
    const dollars = (cents: number) => (cents / 100).toFixed(2);
    const cell = (value: unknown) => `"${String(value ?? "").replace(/"/g, '""')}"`;
    const rows = state.invoices
      .filter((invoice) => invoice.status === "SENT" || invoice.status === "PAID")
      .map((invoice) => {
        const load = findLoad(invoice.loadId);
        const amounts: (number | null)[] = kinds.map(() => null);
        invoice.lines.forEach((line) => {
          const index = column(line.description);
          amounts[index] = (amounts[index] ?? 0) + line.amountCents;
        });
        return [invoice.number, (invoice.sentAt ?? "").slice(0, 10), load.loadNumber, findBroker(load).name,
          invoice.status === "PAID" ? "Paid" : "Waiting on payment",
          ...amounts.map((cents) => (cents === null ? "" : dollars(cents))), dollars(invoice.totalCents),
          invoice.quickPayFeeCents === null ? "" : dollars(invoice.quickPayFeeCents),
          invoice.paidCents === null ? "" : dollars(invoice.paidCents)]
          .map(cell).join(",");
      });
    const header = ["Invoice", "Invoice date", "Load", "Broker", "Status", ...kinds, "Total", "Quick pay fee", "Amount paid"].join(",");
    return new Blob([`\uFEFF${header}\r\n${rows.join("\r\n")}\r\n`], { type: "text/csv" });
  }

  // A document on a load or an email attachment
  const fileId = path.match(/\/api\/(?:load-documents|attachments)\/([^/]+)/)?.[1];
  for (const load of state.loads) {
    const document = load.documents.find((candidate) => candidate.id === fileId)
      ?? load.conversation.flatMap((entry) => entry.files).find((file) => file.id === fileId);
    if (document) {
      const kind: DocumentKind | null = "kind" in document
        ? (document.kind as DocumentKind | null)
        : document.name?.startsWith("RC") ? "RATE_CON" : "POD";
      return makePdf(samplePaper(load, kind, document.name ?? "Document").pages);
    }
  }

  return makePdf(samplePaper(undefined, null, "Sample document").pages);
}

// ---------------------------------------------------------------------------
// Demo controls
// ---------------------------------------------------------------------------

function refreshApp() {
  window.dispatchEvent(new Event(REFRESH_EVENT));
}

export function resetTruckingDemo() {
  state = seed();
  refreshApp();
}

/** A new rate con arrives by email and becomes a load to check. */
export async function simulateRateCon(): Promise<string> {
  await wait(900);
  const crestline = state.brokers[0];
  const number = `8${8300 + state.nextLoad++}`;
  const load = baseLoad(crestline, number, "BOOKED", 198_000, [
    stop("PICKUP", "Kern Citrus, 1400 E Pacheco Rd, Bakersfield, CA 93307", "Tomorrow 05:00 - 07:00"),
    stop("DELIVERY", "Pacific Grocers DC, 2001 E Dominguez St, Long Beach, CA 90810", "Tomorrow 13:00 APPT"),
  ], 0);
  load.equipment = "Reefer";
  load.flags = [flag("TONU_MISSING", "NOTE", "No TONU on this rate con",
    "If the load is cancelled after you dispatch, there's nothing to bill. Ask the broker to add TONU pay.", null)];
  load.tonuCents = null;
  load.conversation = [incoming("Dana Ruiz <dana@crestline.example>", `Rate Confirmation ${number}`,
    `Hi,\n\nNew load ${number} Bakersfield to Long Beach, $1,980. Please sign and return.\n\nDana`, 0, [`RC_${number}.pdf`])];
  load.documents = [emailDocument("RATE_CON", `RC_${number}.pdf`, 0)];
  state.loads.unshift(load);
  state.inbox.unshift(inbox(load.conversation[0], "PROCESSED", `Made load ${number}.`, load.id));
  refreshApp();
  return `A rate con for load ${number} arrived. It's on Today under "Check before signing".`;
}

/** The broker asks for an update; Clerova drafts the reply. */
export async function simulateBrokerEmail(): Promise<string> {
  await wait(900);
  const load = state.loads.find((candidate) => candidate.status === "IN_TRANSIT") ?? state.loads.find((candidate) => candidate.status === "BOOKED");
  if (!load) return "There's no open load to ask about. Reset the demo to start again.";
  const brokerInfo = findBroker(load);
  const entry = incoming(`${brokerInfo.name} <${brokerInfo.email}>`, `Re: Load ${load.loadNumber}`,
    `Hi, can you send a quick update on ${load.loadNumber}? The customer is asking.`, 0);
  load.conversation.push(entry);
  load.replies.forEach((reply) => { if (reply.status === "DRAFT") reply.status = "DISMISSED"; });
  load.replies.push({
    id: id("reply"), toEmail: brokerInfo.email!, subject: `Re: Load ${load.loadNumber}`, kind: "STATUS",
    summary: `${brokerInfo.name} asks for an update.`,
    body: `Hi,\n\n${load.statusNote ? `${load.statusNote} ` : ""}We'll send another update as soon as anything changes.\n\nThanks,`,
    status: "DRAFT", createdAt: now(), sentAt: null,
  });
  state.inbox.unshift(inbox(entry, "ATTACHED", null, load.id));
  touch(load);
  refreshApp();
  return `${brokerInfo.name} asked about load ${load.loadNumber}. A reply is drafted on Today under "Replies to approve".`;
}

// ---------------------------------------------------------------------------
// The API
// ---------------------------------------------------------------------------

const copy = <T,>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

export async function handleTruckingDemoRequest<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? "GET";
  const url = new URL(path, "https://demo.clerova");
  const route = `${method} ${url.pathname}`;
  const body = (options.body instanceof FormData ? {} : options.body ?? {}) as Record<string, unknown>;
  await wait(/\/invoice$|\/send$|\/messages$/.test(route) ? 600 : 150);
  return copy(handle(route, body, options.body instanceof FormData ? options.body : null)) as T;
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function handle(route: string, body: Record<string, unknown>, form: FormData | null): unknown {
  let match: RegExpMatchArray | null;

  // Account
  if (route === "GET /api/me") return state.me;
  if (route === "PATCH /api/me") {
    const name = text(body.organizationName);
    if (!name) invalid("Company name is required.");
    state.me.fullName = body.fullName === undefined ? state.me.fullName : text(body.fullName) || null;
    state.me.organization!.name = name;
    return state.me;
  }
  if (route === "GET /api/billing/offer") return { enabled: false, foundingSpotsLeft: 0 };
  if (route === "GET /api/email-settings") return state.settings;
  if (route === "PUT /api/email-settings/reply-copy") {
    state.settings.replyCopyEmail = text(body.email) || null;
    return state.settings;
  }

  // Carrier details
  if (route === "GET /api/carrier") return { ...state.carrier, missing: missingCarrierDetails() };
  if (route === "PUT /api/carrier") {
    const digits = (value: unknown, label: string) => {
      const cleaned = text(value).replace(/^(mc|dot|usdot)[\s#:.-]*/i, "");
      if (cleaned && !/^\d{1,10}$/.test(cleaned)) invalid(`Enter the ${label} as digits, like 845210.`);
      return cleaned || null;
    };
    const next = Number(body.nextInvoiceNumber);
    if (!Number.isInteger(next) || next < 1) invalid("Enter the next invoice number, like 1001.");
    state.carrier = {
      legalName: text(body.legalName) || null,
      mcNumber: digits(body.mcNumber, "MC number"),
      dotNumber: digits(body.dotNumber, "DOT number"),
      address: text(body.address) || null,
      phone: text(body.phone) || null,
      email: text(body.email) || null,
      invoicePrefix: text(body.invoicePrefix) || null,
      nextInvoiceNumber: next,
      usesFactoring: body.usesFactoring === true,
      factorName: text(body.factorName) || null,
      factorAddress: text(body.factorAddress) || null,
      factorEmail: text(body.factorEmail) || null,
    };
    return { ...state.carrier, missing: missingCarrierDetails() };
  }

  // Loads
  if (route === "GET /api/loads") {
    return state.loads.map(summary).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }
  if ((match = route.match(/^GET \/api\/loads\/([^/]+)$/))) return detail(findLoad(match[1]));
  if ((match = route.match(/^PATCH \/api\/loads\/([^/]+)$/))) {
    const load = findLoad(match[1]);
    const to = body.status as LoadStatus | undefined;
    if (to && to !== load.status) {
      // Same steps as the server: one at a time, rate con checked before the truck rolls
      const steps: Record<LoadStatus, LoadStatus[]> = {
        BOOKED: ["IN_TRANSIT", "CANCELLED"],
        IN_TRANSIT: ["BOOKED", "DELIVERED", "CANCELLED"],
        DELIVERED: ["IN_TRANSIT"],
        CANCELLED: ["BOOKED"],
        INVOICED: [],
        PAID: [],
      };
      if (!steps[load.status].includes(to)) {
        conflict(["INVOICED", "PAID"].includes(load.status)
          ? "This load is invoiced. Void the invoice first to change it."
          : "A load goes Booked, In transit, Delivered, one step at a time.");
      }
      if (load.status === "BOOKED" && to === "IN_TRANSIT") requireChecksDone(load);
      if (load.status === "CANCELLED" && state.invoices.some((invoice) => invoice.loadId === load.id && invoice.status !== "VOID")) {
        conflict("This load has an invoice. Void it first to reopen the load.");
      }
      if (load.status === "DELIVERED" && state.invoices.some((invoice) => invoice.loadId === load.id && invoice.status !== "VOID")) {
        conflict("This load has an invoice. Void it first to move the load back.");
      }
      load.status = to;
    }
    if (typeof body.statusNote === "string") load.statusNote = text(body.statusNote) || null;
    touch(load);
    return detail(load);
  }
  if ((match = route.match(/^POST \/api\/loads\/([^/]+)\/flags\/([^/]+)\/resolve$/))) {
    const load = findLoad(match[1]);
    const item = load.flags.find((candidate) => candidate.id === match![2]);
    if (item && !item.resolvedAt) item.resolvedAt = now();
    return detail(load);
  }
  if ((match = route.match(/^POST \/api\/loads\/([^/]+)\/replies\/([^/]+)\/(send|dismiss)$/))) {
    const load = findLoad(match[1]);
    const reply = load.replies.find((candidate) => candidate.id === match![2]);
    if (!reply) throw new ApiRequestError("Reply not found", 404);
    if (reply.status !== "DRAFT") conflict("This reply was already sent.");
    if (match[3] === "dismiss") {
      reply.status = "DISMISSED";
    } else {
      if (!text(body.body)) conflict("The reply is empty. Add a message before sending.");
      reply.body = signOff(text(body.body));
      reply.status = "SENT";
      reply.sentAt = now();
    }
    touch(load);
    return detail(load);
  }
  if ((match = route.match(/^PUT \/api\/loads\/([^/]+)\/stops\/([^/]+)\/times$/))) {
    const load = findLoad(match[1]);
    const item = load.stops.find((candidate) => candidate.id === match![2]);
    if (!item) throw new ApiRequestError("Stop not found", 404);
    const arrived = (body.arrivedAt as string | null) ?? null;
    const left = (body.leftAt as string | null) ?? null;
    const latest = Date.now() + 10 * 60_000;
    if (left && !arrived) invalid("Log when the truck arrived first.");
    if ((arrived && Date.parse(arrived) > latest) || (left && Date.parse(left) > latest)) invalid("That time is in the future.");
    if (arrived && left && Date.parse(left) < Date.parse(arrived)) invalid("The truck can't leave before it arrived. Check the times.");
    if (arrived && item !== load.stops[0] && load.status === "BOOKED") conflict("Mark the load picked up first, or log the pickup times.");
    if (left && load.status === "BOOKED") requireChecksDone(load);
    item.arrivedAt = arrived;
    item.leftAt = left;
    if (left && item.kind === "PICKUP" && load.status === "BOOKED") load.status = "IN_TRANSIT";
    if (left && item === load.stops.at(-1) && (load.status === "BOOKED" || load.status === "IN_TRANSIT")) load.status = "DELIVERED";
    syncDetention(load, item);
    touch(load);
    return detail(load);
  }
  if ((match = route.match(/^POST \/api\/loads\/([^/]+)\/charges$/))) {
    const load = findLoad(match[1]);
    chargesLocked(load);
    const amount = Number(body.amountCents);
    if (!amount || amount <= 0) invalid("Enter the amount, like 75.00.");
    const kind = body.kind as ChargeKind;
    load.charges.push({
      id: id("charge"), kind, label: CHARGE_LABELS[kind], description: text(body.description) || null, amountCents: amount,
      status: (body.status as ChargeStatus) ?? "TO_CLAIM", stopId: null, claimedAt: null,
    });
    touch(load);
    return detail(load);
  }
  if ((match = route.match(/^(PATCH|DELETE) \/api\/loads\/([^/]+)\/charges\/([^/]+)$/))) {
    const load = findLoad(match[2]);
    chargesLocked(load);
    const charge = load.charges.find((candidate) => candidate.id === match![3]);
    if (!charge) throw new ApiRequestError("Charge not found", 404);
    if (match[1] === "DELETE") {
      load.charges = load.charges.filter((candidate) => candidate !== charge);
    } else {
      if (body.status) {
        if (body.status === "CLAIMED" && !charge.claimedAt) charge.claimedAt = now();
        charge.status = body.status as ChargeStatus;
      }
      if (typeof body.amountCents === "number") charge.amountCents = body.amountCents;
      if (typeof body.description === "string") charge.description = text(body.description) || null;
    }
    touch(load);
    return detail(load);
  }
  if ((match = route.match(/^POST \/api\/loads\/([^/]+)\/messages$/))) {
    const load = findLoad(match[1]);
    const to = text(body.to) || findBroker(load).email || "";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) invalid("Enter the broker's email address.");
    if (!text(body.body)) conflict("The message is empty. Add a message before sending.");
    const charge = load.charges.find((candidate) => candidate.id === body.chargeId);
    const stop = load.stops.find((candidate) => candidate.id === body.stopId);
    if (body.stopId && !stop) throw new ApiRequestError("Stop not found", 404);
    recordSent(load, to, text(body.subject) || `Load ${load.loadNumber}`, text(body.body), "DETENTION", {
      stopId: stop?.id,
      chargeId: charge?.id,
    });
    if (charge && charge.status === "TO_CLAIM") {
      charge.status = "CLAIMED";
      charge.claimedAt = now();
    }
    return detail(load);
  }
  if ((match = route.match(/^POST \/api\/loads\/([^/]+)\/emails\/([^/]+)$/))) {
    const load = findLoad(match[1]);
    const email = state.inbox.find((candidate) => candidate.id === match![2]);
    if (!email) throw new ApiRequestError("Email not found", 404);
    email.status = "ATTACHED";
    email.loadId = load.id;
    email.statusReason = "Filed under this load by you.";
    load.conversation.push({
      id: email.id, incoming: true, from: `${email.fromName ?? ""} <${email.fromEmail}>`, to: null, subject: email.subject,
      body: email.preview, at: email.receivedAt, files: [],
    });
    if (email.attachments.some((name) => /lumper/i.test(name))) {
      load.documents.push({ id: id("file"), source: "EMAIL", kind: "LUMPER_RECEIPT", name: email.attachments[0], contentType: "image/jpeg", sizeBytes: 240_000, stored: true, at: email.receivedAt });
    }
    touch(load);
    return detail(load);
  }

  // Brokers
  if (route === "GET /api/brokers") {
    return state.brokers.map((item): BrokerSummary => {
      const loads = state.loads.filter((load) => load.brokerId === item.id);
      return { broker: item, loads: loads.length, openLoads: loads.filter((load) => ["BOOKED", "IN_TRANSIT", "DELIVERED"].includes(load.status)).length };
    });
  }
  if ((match = route.match(/^PUT \/api\/brokers\/([^/]+)\/mc-number$/))) {
    const item = state.brokers.find((candidate) => candidate.id === match![1]);
    if (!item) throw new ApiRequestError("Broker not found", 404);
    const mc = text(body.mcNumber).replace(/\D/g, "");
    if (!mc) invalid("Enter the MC number, like 555609.");
    item.mcNumber = mc;
    item.fmcsaStatus = "ACTIVE";
    item.fmcsaName = item.name.toUpperCase();
    item.fmcsaCheckedAt = now();
    for (const load of state.loads.filter((candidate) => candidate.brokerId === item.id)) {
      load.flags = load.flags.filter((candidate) => candidate.code !== "MC_MISSING");
    }
    return item;
  }

  // Documents
  if (route === "GET /api/load-documents") return state.uploads.filter((document) => !document.loadId);
  if (route === "POST /api/load-documents") {
    const file = form?.get("file") as File | null;
    const loadId = form?.get("loadId") as string | null;
    const name = file?.name ?? "document.pdf";
    const kind: DocumentKind = /pod|deliver/i.test(name) ? "POD" : /bol|lading/i.test(name) ? "BOL" : /lumper/i.test(name) ? "LUMPER_RECEIPT" : "OTHER";
    const target = loadId ? findLoad(loadId) : state.loads.find((load) => load.status === "DELIVERED");
    const document: UploadedDocument = {
      id: id("file"), loadId: target?.id ?? null, kind, name, contentType: file?.type ?? "application/pdf",
      sizeBytes: file?.size ?? 0, at: now(), newLoad: false,
    };
    state.uploads.unshift(document);
    if (target) {
      target.documents.push({ id: document.id, source: "UPLOAD", kind, name, contentType: document.contentType, sizeBytes: document.sizeBytes, stored: true, at: document.at });
      touch(target);
    }
    return document;
  }
  if ((match = route.match(/^(PATCH|DELETE) \/api\/load-documents\/([^/]+)$/))) {
    const document = state.uploads.find((candidate) => candidate.id === match![2]);
    for (const load of state.loads) {
      const onLoad = load.documents.find((candidate) => candidate.id === match![2]);
      if (!onLoad) continue;
      if (match[1] === "DELETE") load.documents = load.documents.filter((candidate) => candidate !== onLoad);
      else if (body.kind) onLoad.kind = body.kind as DocumentKind;
    }
    if (match[1] === "DELETE") {
      state.uploads = state.uploads.filter((candidate) => candidate.id !== match![2]);
      return null;
    }
    if (document) {
      if (body.kind) document.kind = body.kind as DocumentKind;
      if (typeof body.loadId === "string") {
        const load = findLoad(body.loadId);
        document.loadId = load.id;
        load.documents.push({ id: document.id, source: "UPLOAD", kind: document.kind, name: document.name, contentType: document.contentType, sizeBytes: document.sizeBytes, stored: true, at: document.at });
      }
      return document;
    }
    return { id: match[2], loadId: null, kind: body.kind ?? "OTHER", name: "document", contentType: null, sizeBytes: 0, at: now(), newLoad: false };
  }

  // Carrier packet
  if (route === "GET /api/carrier/documents") return carrierDocuments();
  if (route === "POST /api/carrier/documents") {
    const file = form?.get("file") as File | null;
    const kind = ((form?.get("kind") as string | null) ?? "OTHER") as CarrierDocumentKind;
    const expiresOn = (form?.get("expiresOn") as string | null) || null;
    if (state.carrierDocuments.length >= 20) conflict("The carrier packet holds up to 20 documents. Remove an old one first.");
    const document = carrierDocument({
      id: id("carrier"), kind, kindLabel: "", name: file?.name ?? "document.pdf", contentType: file?.type ?? "application/pdf",
      sizeBytes: file?.size ?? 0, expiresOn, expired: false, at: now(),
    });
    state.carrierDocuments.push(document);
    return document;
  }
  if ((match = route.match(/^(PATCH|DELETE) \/api\/carrier\/documents\/([^/]+)$/))) {
    const document = state.carrierDocuments.find((candidate) => candidate.id === match![2]);
    if (!document) throw new ApiRequestError("Document not found", 404);
    if (match[1] === "DELETE") {
      state.carrierDocuments = state.carrierDocuments.filter((candidate) => candidate !== document);
      return undefined;
    }
    if (body.kind) document.kind = body.kind as CarrierDocumentKind;
    if (typeof body.expiresOn === "string") document.expiresOn = body.expiresOn || null;
    return carrierDocument(document);
  }
  if (route === "POST /api/carrier/packet/send") {
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text(body.to))) invalid("Enter the broker's email address.");
    if (state.carrierDocuments.length === 0) conflict("Add your documents first, like your W-9, MC authority and insurance certificate.");
    return { sent: true };
  }

  // Inbox
  if (route === "GET /api/inbox") return [...state.inbox].sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  if ((match = route.match(/^POST \/api\/inbox\/([^/]+)\/draft-reply$/))) {
    // Same rule as the server: 10 drafts per email, not counted toward the plan
    const email = state.inbox.find((candidate) => candidate.id === match![1]);
    if (!email) throw new ApiRequestError("Email not found", 404);
    const left = email.replyDraftsLeft ?? 10;
    if (left <= 0) conflict("You've used the 10 drafts for this email. Edit the last one, or write your own.");
    email.replyDraftsLeft = left - 1;
    const first = (email.fromName ?? "").split(" ")[0];
    const draft = `Hi${first ? ` ${first}` : ""},\n\nThanks for your email${email.subject ? ` about "${email.subject}"` : ""}. We're checking and will get back to you shortly.\n\nThanks,`;
    return { draft, draftsLeft: email.replyDraftsLeft };
  }
  if ((match = route.match(/^POST \/api\/inbox\/([^/]+)\/reply$/))) {
    const email = state.inbox.find((candidate) => candidate.id === match![1]);
    if (!email) throw new ApiRequestError("Email not found", 404);
    const to = text(body.to) || email.fromEmail || "";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) invalid("Enter the email address to reply to.");
    if (!text(body.body)) conflict("The reply is empty. Add a message before sending.");
    const subject = /^re:/i.test(email.subject ?? "") ? email.subject! : `Re: ${email.subject ?? ""}`;
    const load = email.loadId ? state.loads.find((candidate) => candidate.id === email.loadId) : undefined;
    if (load) recordSent(load, to, subject, text(body.body), "OTHER");
    email.repliedAt = now();
    return { ...email };
  }
  if ((match = route.match(/^POST \/api\/inbox\/([^/]+)\/reprocess$/))) {
    const email = state.inbox.find((candidate) => candidate.id === match![1]);
    if (!email) throw new ApiRequestError("This email no longer exists.", 404);
    return { ...email, statusReason: "Read again. Clerova still couldn't tell which load it's about. File it by hand." };
  }

  // Invoices
  if (route === "GET /api/invoices") {
    return state.invoices.filter((invoice) => invoice.status !== "VOID").sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((invoice) => invoiceInfo(invoice, false));
  }
  if ((match = route.match(/^POST \/api\/loads\/([^/]+)\/invoice$/))) {
    const load = findLoad(match[1]);
    const existing = currentInvoice(load);
    if (existing) return invoiceInfo(existing, true);
    if (load.status === "CANCELLED" && !load.charges.some((charge) => charge.status === "APPROVED")) {
      conflict("This load is cancelled. To bill a TONU, add it under Extra pay as approved first.");
    }
    const missing = missingCarrierDetails();
    if (missing.length > 0) {
      throw new ApiRequestError(`Add ${missing.join(", ")} in Settings first. They go on every invoice.`, 409);
    }
    const lines = startingLines(load);
    const invoice: DemoInvoice = {
      id: id("invoice"), organizationId: "demo", loadId: load.id,
      number: `${state.carrier.invoicePrefix ?? ""}${state.carrier.nextInvoiceNumber++}`, status: "DRAFT",
      sendTo: state.carrier.usesFactoring ? "FACTOR" : "BROKER", billTo: null, remitTo: null, lines,
      totalCents: lines.reduce((sum, line) => sum + line.amountCents, 0), quickPay: false, quickPayFeeCents: null,
      sentVia: null, sentEmail: null, sentAt: null, dueDate: null, paidAt: null, paidCents: null, createdAt: now(),
    };
    state.invoices.unshift(invoice);
    return invoiceInfo(invoice, true);
  }
  if ((match = route.match(/^(GET|PATCH) \/api\/invoices\/([^/]+)$/))) {
    const invoice = state.invoices.find((candidate) => candidate.id === match![2]);
    if (!invoice) throw new ApiRequestError("Invoice not found", 404);
    if (match[1] === "PATCH") {
      const load = findLoad(invoice.loadId);
      if (invoice.status !== "DRAFT") conflict("This invoice was already sent, so it can't be changed. Void it and make a new one.");
      if (body.rebuild === true) invoice.lines = startingLines(load);
      else if (Array.isArray(body.lines)) {
        const lines = body.lines as InvoiceLine[];
        if (lines.some((line) => !text(line.description))) invalid("Give each line a short description.");
        invoice.lines = lines.map((line) => ({ description: text(line.description), amountCents: line.amountCents }));
      }
      if (body.sendTo === "FACTOR" && (!state.carrier.usesFactoring || missingCarrierDetails().length > 0)) {
        conflict("Add your factoring company's name and payment address in Settings first.");
      }
      if (body.sendTo) invoice.sendTo = body.sendTo as Invoice["sendTo"];
      if (typeof body.quickPay === "boolean") invoice.quickPay = body.quickPay;
      if (invoice.sendTo !== "BROKER" || load.quickPayPercent === null) invoice.quickPay = false;
      invoice.totalCents = invoice.lines.reduce((sum, line) => sum + line.amountCents, 0);
      invoice.quickPayFeeCents = quickPayFee(invoice, load);
    }
    return invoiceInfo(invoice, true);
  }
  if ((match = route.match(/^POST \/api\/invoices\/([^/]+)\/(send|mark-sent|paid|unpaid|void)$/))) {
    const invoice = state.invoices.find((candidate) => candidate.id === match![1]);
    if (!invoice) throw new ApiRequestError("Invoice not found", 404);
    const load = findLoad(invoice.loadId);

    switch (match[2]) {
      case "send": {
        const to = text(body.to);
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to)) invalid("Enter the email address to send it to.");
        if (invoice.status === "VOID" || invoice.status === "PAID") conflict(`This invoice is ${invoice.status.toLowerCase()}, so it can't be sent.`);
        recordSent(load, to, `Invoice ${invoice.number} - Load ${load.loadNumber} - ${state.carrier.legalName}`,
          `${text(body.message)}\n\n[Attached: Invoice ${invoice.number} packet.pdf]`, "INVOICE");
        markSent(invoice, load, "EMAIL", to);
        break;
      }
      case "mark-sent":
        if (invoice.status === "DRAFT") markSent(invoice, load, "PORTAL", null);
        break;
      case "paid":
        if (invoice.status === "DRAFT") conflict("Send the invoice before marking it paid.");
        if (invoice.status === "SENT") {
          invoice.status = "PAID";
          invoice.paidAt = now();
          invoice.paidCents = typeof body.paidCents === "number" ? body.paidCents : invoice.totalCents - (invoice.quickPayFeeCents ?? 0);
          if (load.status !== "CANCELLED") load.status = "PAID";
        }
        break;
      case "unpaid":
        if (invoice.status === "PAID") {
          invoice.status = "SENT";
          invoice.paidAt = null;
          invoice.paidCents = null;
          if (load.status === "PAID") load.status = "INVOICED";
        }
        break;
      case "void":
        if (invoice.status === "PAID") conflict("This invoice is paid. Mark it unpaid first if that was a mistake.");
        invoice.status = "VOID";
        if (load.status === "INVOICED") load.status = "DELIVERED";
        break;
    }

    touch(load);
    return invoiceInfo(invoice, match[2] !== "void");
  }

  throw new ApiRequestError("That isn't part of the demo.", 404);
}

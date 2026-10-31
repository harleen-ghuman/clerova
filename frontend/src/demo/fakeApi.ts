import { ApiRequestError } from "../api/client";
import type { Me, Organization } from "../types/Account";
import {
  COMPANY_INFO_FIELDS,
  MAX_FIELD_LENGTH,
  MAX_TOTAL_LENGTH,
  type CompanyInfo,
} from "../api/companyInfo";
import type {
  AttachmentFile,
  ConversationEntry,
  EmailSettings,
  InboundMessage,
  WorkItemEvent,
} from "../types/Email";
import type { MaintenanceRequest } from "../types/MaintenanceRequest";
import type { ProposedAction } from "../types/ProposedAction";
import { MAX_SUGGESTIONS, type WorkItem } from "../types/WorkItem";
import type { WeekSummary } from "../api/summary";
import { REFRESH_EVENT } from "./mode";

/*
 * A small stand-in for the Clerova API, kept in the browser for the demo.
 * It follows the same rules as the server (statuses, jobs, replies), but
 * the "AI" answers are pre-written examples and nothing is ever sent.
 */

type Category = "MAINTENANCE" | "BILLING" | "LEASING" | "COMPLAINT" | "GENERAL";
type Priority = "LOW" | "MEDIUM" | "HIGH";
type ActionType =
  | "CREATE_MAINTENANCE_REQUEST"
  | "RESPOND_TO_CUSTOMER"
  | "REQUEST_MORE_INFORMATION"
  | "ESCALATE_TO_HUMAN"
  | "NO_ACTION";

interface Suggestion {
  actionType: ActionType;
  reasoning: string;
  draftedResponse: string;
}

interface DemoState {
  me: Me;
  items: WorkItem[];
  actions: ProposedAction[];
  jobs: MaintenanceRequest[];
  inbox: InboundMessage[];
  conversations: Record<string, ConversationEntry[]>;
  events: Record<string, WorkItemEvent[]>;
  summary: WeekSummary;
  settings: EmailSettings;
  companyInfo: CompanyInfo;
  nextEmail: number;
}

const ORG_ID = "demo-org";
const DEFAULT_PARKING = "One assigned spot per unit. Guests park on the street.";
const AC_JOB_NOTES = "CoolAir HVAC booked for Thursday, 10 to 12. Tenant home all day.";
const CLOSED = ["COMPLETED", "CANCELLED"];
const loadedAt = Date.now();
let counter = 0;

function ago(minutes: number): string {
  return new Date(loadedAt - minutes * 60_000).toISOString();
}

function now(): string {
  return new Date().toISOString();
}

function id(prefix: string): string {
  counter += 1;
  return `${prefix}-${counter}`;
}

function wait(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function firstName(name: string | null): string | null {
  return name ? name.split(" ")[0] : null;
}

function greeting(name: string | null): string {
  const first = firstName(name);
  return first ? `Hi ${first},` : "Hi there,";
}

// ---------------------------------------------------------------------------
// Sample workspace
// ---------------------------------------------------------------------------

interface Seed {
  key: string;
  name: string;
  email: string;
  subject: string;
  message: string;
  category: Category;
  priority: Priority;
  summary: string;
  address: string | null;
  minutesAgo: number;
  /** Sample files that came with the email. */
  files?: { name: string; type: string; size: number }[];
}

const SEEDS: Seed[] = [
  {
    key: "leak",
    name: "Tina Alvarez",
    email: "tina.alvarez@gmail.com",
    subject: "Kitchen sink leaking",
    message:
      "Hi, water is leaking under the kitchen sink and the floor is getting wet. I put a towel down but it keeps coming. I'm in 12 Oak St, Unit 4B.",
    category: "MAINTENANCE",
    priority: "HIGH",
    summary: "Water leaking under the kitchen sink; the floor is getting wet.",
    address: "12 Oak St, Unit 4B",
    minutesAgo: 18,
    files: [{ name: "under-the-sink.jpg", type: "image/jpeg", size: 245_760 }],
  },
  {
    key: "fee",
    name: "Marcus Lee",
    email: "marcus.lee@outlook.com",
    subject: "Question about a late fee",
    message:
      "I paid rent on the 1st through the portal, but there's a $95 late fee on my account this month. Can you check?",
    category: "BILLING",
    priority: "MEDIUM",
    summary: "Resident paid on the 1st but sees a $95 late fee.",
    address: "310 Cedar Ave, Unit 2",
    minutesAgo: 52,
  },
  {
    key: "parking",
    name: "Priya Shah",
    email: "priya.shah@gmail.com",
    subject: "Guest parking this weekend",
    message:
      "My parents are visiting Saturday and Sunday. Where can they park, and do they need a pass?",
    category: "GENERAL",
    priority: "LOW",
    summary: "Asks where weekend guests can park and whether they need a pass.",
    address: "225 Market St, Unit 7C",
    minutesAgo: 140,
  },
  {
    key: "noise",
    name: "Dan Ortiz",
    email: "dan.ortiz@gmail.com",
    subject: "Noise from the upstairs unit",
    message:
      "The upstairs unit has been loud past midnight several nights this week. It's hard to sleep.",
    category: "COMPLAINT",
    priority: "MEDIUM",
    summary: "Noise from the upstairs unit past midnight on several nights.",
    address: "742 Oak St, Unit 3A",
    minutesAgo: 5 * 24 * 60,
  },
  {
    key: "ac",
    name: "Leah Kim",
    email: "leah.kim@gmail.com",
    subject: "AC not cooling",
    message: "The AC is running but the air isn't cold at all. Unit 5, 5667 Sparrow St.",
    category: "MAINTENANCE",
    priority: "MEDIUM",
    summary: "AC runs but doesn't cool.",
    address: "5667 Sparrow St, Unit 5",
    minutesAgo: 2 * 24 * 60,
  },
  {
    key: "heater",
    name: "Sam Rivera",
    email: "sam.rivera@gmail.com",
    subject: "Heater won't turn on",
    message: "The wall heater in the bedroom won't turn on. Everything else works. 18 Pine Rd, Apt 2.",
    category: "MAINTENANCE",
    priority: "MEDIUM",
    summary: "Bedroom wall heater won't turn on.",
    address: "18 Pine Rd, Apt 2",
    minutesAgo: 26 * 60,
  },
  {
    key: "lease",
    name: "Jordan Park",
    email: "jordan.park@yahoo.com",
    subject: "Breaking my lease early",
    message:
      "I got a job in another state and need to move out in 6 weeks, 5 months before my lease ends. What are my options?",
    category: "LEASING",
    priority: "MEDIUM",
    summary: "Needs to move out 5 months early for a job and asks about options.",
    address: "90 Bay St, Unit 12",
    minutesAgo: 3 * 24 * 60,
  },
  {
    key: "smoke",
    name: "Nina Patel",
    email: "nina.patel@gmail.com",
    subject: "Smoke detector beeping",
    message: "The smoke detector in the hallway keeps chirping every minute. 1200 Park Ave, Unit 11C.",
    category: "MAINTENANCE",
    priority: "LOW",
    summary: "Hallway smoke detector chirping; likely needs a new battery.",
    address: "1200 Park Ave, Unit 11C",
    minutesAgo: 4 * 24 * 60,
  },
];

function seedState(): DemoState {
  counter = 0;

  const organization: Organization = {
    id: ORG_ID,
    name: "Maple Property Group",
    industry: "PROPERTY_MANAGEMENT",
    plan: "STARTER",
    trialEndsAt: null,
    aiMonthlyLimit: 500,
    aiRequestsUsedThisMonth: 138,
    subscriptionStatus: "active",
    billingInterval: "month",
    currentPeriodEnd: new Date(loadedAt + 19 * 24 * 60 * 60_000).toISOString(),
    cancelAtPeriodEnd: false,
    foundingCustomer: true,
    // Plans and billing aren't part of the demo
    billingEnabled: false,
    subscriptionRequired: false,
  };

  const state: DemoState = {
    me: {
      userId: "demo-user",
      email: "alex@mapleproperty.com",
      fullName: "Alex Morgan",
      organization,
    },
    items: [],
    actions: [],
    jobs: [],
    inbox: [],
    conversations: {},
    events: {},
    summary: {
      requestsOrganized: 23,
      emailsReceived: 31,
      repliesSent: 19,
      jobsCompleted: 6,
      medianReplyMinutes: 14,
      minutesSaved: 115,
      minutesPerRequest: 5,
      allTimeRequests: 140,
      allTimeApprovals: 121,
    },
    settings: {
      intakeEnabled: true,
      sendingEnabled: true,
      forwardingAddress: "maple-property-k3j9x2@in.clerova-ai.com",
      gmailConfirmation: null,
      replyCopyEmail: null,
    },
    companyInfo: {
      rentAndPayments:
        "Rent is due on the 1st. Pay in the resident portal at pay.mapleproperty.com. There is a 5-day grace period, then a $50 late fee.",
      officeAndContact: "Monday to Friday, 9am to 5pm. Call (555) 201-3300.",
      emergencies:
        "After hours, call (555) 201-3399. Emergencies are fire, gas, flooding, no heat in winter and lockouts.",
      pets: "Cats and dogs under 40 lb, two at most, with a $300 pet deposit.",
      parking: DEFAULT_PARKING,
    },
    nextEmail: 0,
  };

  const byKey: Record<string, WorkItem> = {};

  for (const seed of SEEDS) {
    const item = receiveEmail(state, seed, seed.minutesAgo);
    byKey[seed.key] = item;
  }

  // Waiting for review: suggestions ready
  suggest(state, byKey.leak, 17);
  suggest(state, byKey.fee, 51);
  suggest(state, byKey.parking, 139);

  // Asked for details 5 days ago, no answer yet: shows "Remind" on Today
  approveAndSend(state, byKey.noise, 5 * 24 * 60 - 8);

  // Jobs in progress; Leah wrote again asking for an update
  approveAndSend(state, byKey.ac, 2 * 24 * 60 - 10);
  const acJob = state.jobs.find((job) => job.workItemId === byKey.ac.id)!;
  acJob.status = "IN_PROGRESS";
  acJob.notes = AC_JOB_NOTES;
  acJob.updatedAt = ago(30 * 60);
  record(state, byKey.ac.id, "MAINTENANCE_UPDATED", "Job started", "Alex Morgan", 30 * 60);
  followUp(state, byKey.ac, "Hi, any update on when the technician is coming? It's getting really hot in here.", 25);

  approveAndSend(state, byKey.heater, 26 * 60 - 12);

  // Escalated to a person
  approveAndSend(state, byKey.lease, 3 * 24 * 60 - 20);

  // Done last week
  approveAndSend(state, byKey.smoke, 4 * 24 * 60 - 15);
  const smokeJob = state.jobs.find((job) => job.workItemId === byKey.smoke.id)!;
  smokeJob.status = "COMPLETED";
  smokeJob.notes = "Battery replaced in-house.";
  smokeJob.updatedAt = ago(3 * 24 * 60);
  byKey.smoke.status = "COMPLETED";
  byKey.smoke.updatedAt = ago(3 * 24 * 60);
  record(state, byKey.smoke.id, "MAINTENANCE_UPDATED", "Job done", "Alex Morgan", 3 * 24 * 60);

  // A newsletter Clerova set aside
  state.inbox.push({
    id: id("email"),
    fromEmail: "news@rentaltimes.com",
    fromName: "Rental Times",
    subject: "This week in property management",
    preview: "Five trends shaping rentals this fall, plus a webinar invite...",
    receivedAt: ago(6 * 60),
    status: "IGNORED",
    statusReason: "Automated or bulk email. Click Process anyway if it's a real request.",
    workItemId: null,
    attachments: [],
  });

  return state;
}

/** An email arrives and becomes a request, as the server does. */
function receiveEmail(state: DemoState, seed: Omit<Seed, "key" | "minutesAgo">, minutesAgo: number): WorkItem {
  const at = ago(minutesAgo);
  const files: AttachmentFile[] = (seed.files ?? []).map((file) => ({
    id: id("file"),
    fileName: file.name,
    contentType: file.type,
    sizeBytes: file.size,
    available: true,
  }));
  const item: WorkItem = {
    id: id("request"),
    organizationId: ORG_ID,
    originalMessage: seed.message,
    address: seed.address,
    category: seed.category,
    priority: seed.priority,
    status: "NEW",
    summary: seed.summary,
    source: "EMAIL",
    senderEmail: seed.email,
    senderName: seed.name,
    subject: seed.subject,
    createdAt: at,
    updatedAt: at,
    unreadReplyAt: null,
    unreadReplyUrgent: false,
    suggestionsLeft: MAX_SUGGESTIONS,
  };

  state.items.push(item);
  state.conversations[item.id] = [
    {
      id: id("message"),
      direction: "INBOUND",
      from: `${seed.name} <${seed.email}>`,
      to: null,
      subject: seed.subject,
      text: seed.message,
      at,
      attachments: files.map((file) => file.fileName ?? ""),
      files,
    },
  ];
  state.events[item.id] = [];
  record(state, item.id, "CREATED", `Received by email from ${seed.email}`, null, minutesAgo);

  state.inbox.push({
    id: id("email"),
    fromEmail: seed.email,
    fromName: seed.name,
    subject: seed.subject,
    preview: seed.message.slice(0, 140),
    receivedAt: at,
    status: "PROCESSED",
    statusReason: null,
    workItemId: item.id,
    attachments: files.map((file) => file.fileName ?? ""),
    files,
  });

  return item;
}

function record(
  state: DemoState,
  workItemId: string,
  type: string,
  detail: string,
  actor: string | null,
  minutesAgo?: number
) {
  (state.events[workItemId] ??= []).push({
    id: id("event"),
    type,
    detail,
    actor,
    createdAt: minutesAgo === undefined ? now() : ago(minutesAgo),
  });
}

// ---------------------------------------------------------------------------
// Pre-written "AI"
// ---------------------------------------------------------------------------

const URGENT_WORDS = /flood|pouring|gas|fire|smoke|spark|burning|no heat|locked out|break-?in|carbon monoxide|sewage|burst/i;

function classify(text: string): { category: Category; priority: Priority; summary: string; address: string | null } {
  const lower = text.toLowerCase();
  const category: Category =
    /leak|broken|not working|won'?t|heat|\bac\b|air condition|water|toilet|sink|door|lock|smoke|mold|outlet|light|appliance|repair|stuck|clog|dishwasher|fridge|washer|dryer|disposal|ceiling|window/.test(lower)
      ? "MAINTENANCE"
      : /rent|fee|charge|payment|deposit|invoice|balance|refund/.test(lower)
        ? "BILLING"
        : /lease|renew|move[- ]?out|move[- ]?in|application|sublet/.test(lower)
          ? "LEASING"
          : /noise|neighbou?r|loud|complain|rude|smell/.test(lower)
            ? "COMPLAINT"
            : "GENERAL";

  const priority: Priority = URGENT_WORDS.test(text)
    ? "HIGH"
    : category === "MAINTENANCE" || category === "BILLING" || category === "COMPLAINT"
      ? "MEDIUM"
      : "LOW";

  const address =
    text.match(
      /\d+\s+[A-Z][a-zA-Z]+(?:\s+[A-Z][a-zA-Z]+)?\s+(?:St|Street|Ave|Avenue|Rd|Road|Blvd|Lane|Ln|Dr|Drive|Way|Ct|Court)\.?(?:,?\s*(?:Unit|Apt|Apartment|#)\s*\w+)?/
    )?.[0] ?? null;

  const firstSentence = text.trim().split(/(?<=[.!?])\s+/)[0] ?? text.trim();
  const summary = firstSentence.length > 140 ? `${firstSentence.slice(0, 137)}...` : firstSentence;

  return { category, priority, summary, address };
}

/** What the resident can safely do before someone arrives, by problem. */
function meanwhile(text: string): string {
  if (/ceiling|unit above|upstairs/.test(text)) {
    return "In the meantime, if it's safe, move belongings away from the drip and put a bucket under it. We'll also check the unit above.";
  }

  if (/sink|faucet/.test(text)) {
    return "In the meantime, if it's safe, turn off the water valve under the sink (or the main valve) and keep towels down to limit damage.";
  }

  if (/toilet/.test(text)) {
    return "In the meantime, if it's safe, turn off the water valve behind the toilet and keep towels down to limit damage.";
  }

  if (/pipe|valve/.test(text)) {
    return "In the meantime, if it's safe, turn off the main water valve and keep towels down to limit damage.";
  }

  if (/water|leak|flood/.test(text)) {
    return "In the meantime, if it's safe, keep towels or a bucket down to limit damage.";
  }

  return "In the meantime, please keep clear of the problem if it isn't safe.";
}

function suggestionFor(state: DemoState, item: WorkItem): Suggestion {
  const hi = greeting(item.senderName);
  const text = item.originalMessage.toLowerCase();

  if (item.category === "MAINTENANCE") {
    const urgent = item.priority === "HIGH";
    return {
      actionType: "CREATE_MAINTENANCE_REQUEST",
      reasoning: urgent
        ? "Active problem that could cause damage or a safety risk. Needs a maintenance job right away."
        : "A repair is needed. Create a maintenance job so it can be scheduled and tracked.",
      draftedResponse: urgent
        ? `${hi}\n\nThank you for letting us know. We've logged this as an urgent maintenance request, and our team will follow up shortly to arrange the repair.\n\n${meanwhile(text)} If there's ever a gas smell, fire or other danger, leave the unit and call 911.`
        : `${hi}\n\nThanks for reaching out. We've logged this as a maintenance request, and our team will follow up to arrange the repair.\n\nIf anything changes or gets worse, just reply to this email.`,
    };
  }

  if (item.category === "BILLING") {
    return {
      actionType: "RESPOND_TO_CUSTOMER",
      reasoning: "Billing question. The team needs to check the account before confirming anything, so reply without guessing.",
      draftedResponse: `${hi}\n\nThanks for reaching out about this. We'll check your account and the payment records and follow up within one business day.\n\nYou don't need to send anything else for now.`,
    };
  }

  if (item.category === "LEASING") {
    return {
      actionType: "ESCALATE_TO_HUMAN",
      reasoning: "Ending a lease early depends on this resident's lease and local rules, so a person should decide. Check the lease for an early-termination clause, the notice period and any fee, then reply with the options.",
      draftedResponse: `${hi}\n\nThanks for letting us know, and congratulations on the new job. We've passed your question to the right person on our team, who will follow up with your options.`,
    };
  }

  if (item.category === "COMPLAINT") {
    return {
      actionType: "REQUEST_MORE_INFORMATION",
      reasoning: "To follow up with the other unit, the team needs specific dates and times.",
      draftedResponse: `${hi}\n\nSorry to hear about this. To help us follow up, could you share:\n\n- the dates it happened\n- the approximate times\n\nThank you.`,
    };
  }

  if (/park/.test(text)) {
    const parking = state.companyInfo.parking?.trim();

    if (!parking) {
      return {
        actionType: "RESPOND_TO_CUSTOMER",
        reasoning: "Parking question, but your company info has no parking rules, so the reply doesn't guess.",
        draftedResponse: `${hi}\n\nThanks for checking. We'll confirm where your guests can park and follow up before the weekend.`,
      };
    }

    return {
      actionType: "RESPOND_TO_CUSTOMER",
      reasoning: "Parking question, answered from the parking rules in your company info.",
      draftedResponse:
        parking === DEFAULT_PARKING
          ? `${hi}\n\nThanks for checking. Your guests can park on the street, and they don't need a pass. Each unit's assigned spot is for residents only.\n\nEnjoy the visit!`
          : `${hi}\n\nThanks for checking. Here's how parking works for guests: ${parking}\n\nEnjoy the visit!`,
    };
  }

  if (item.originalMessage.trim().length < 40) {
    return {
      actionType: "REQUEST_MORE_INFORMATION",
      reasoning: "The message is too short to know what's needed.",
      draftedResponse: `${hi}\n\nThanks for your message. Could you tell us a little more about what you need, and your unit number?`,
    };
  }

  return {
    actionType: "RESPOND_TO_CUSTOMER",
    reasoning: "General question that can be answered with a reply.",
    draftedResponse: `${hi}\n\nThanks for reaching out. Our team will look into this and get back to you shortly.`,
  };
}

function draftFor(state: DemoState, item: WorkItem): string {
  const hi = greeting(item.senderName);

  if (item.status === "WAITING_FOR_INFORMATION") {
    return `${hi}\n\nJust following up on our last email. Could you share the dates and approximate times this happened? That will help us follow up.\n\nThanks.`;
  }

  if (item.status === "ESCALATED") {
    return `${hi}\n\nThanks for your patience. The team member handling this is working on it and will follow up with you soon.`;
  }

  if (item.unreadReplyUrgent) {
    return `${hi}\n\nThank you for telling us. If you smell gas, please leave the unit right away and call 911 and the gas company from outside. Don't use switches, the stove or any flames.\n\nWe're contacting our maintenance team now and will follow up as soon as we can.`;
  }

  const job = state.jobs.find(
    (candidate) => candidate.workItemId === item.id && (candidate.status === "OPEN" || candidate.status === "IN_PROGRESS")
  );

  // The visit in the sample job notes, in words for the resident
  if (job?.notes === AC_JOB_NOTES) {
    return `${hi}\n\nThanks for checking in, and sorry it's so hot. A technician from CoolAir HVAC is booked for Thursday between 10am and 12pm.\n\nIf that time no longer works, just reply and we'll find another one.`;
  }

  if (job?.status === "IN_PROGRESS") {
    return `${hi}\n\nThanks for checking in. Your repair is underway, and we'll let you know as soon as it's done or if we need anything from you.`;
  }

  return `${hi}\n\nThanks for checking in. Your repair is logged, and our maintenance team is arranging the visit. We'll follow up as soon as we have a confirmed time.`;
}

const DESCRIBE: Record<ActionType, string> = {
  CREATE_MAINTENANCE_REQUEST: "a maintenance job and a reply",
  RESPOND_TO_CUSTOMER: "a reply",
  REQUEST_MORE_INFORMATION: "asking for more details",
  ESCALATE_TO_HUMAN: "escalating to your team",
  NO_ACTION: "closing with no action",
};

// ---------------------------------------------------------------------------
// Workflow rules (same as the server)
// ---------------------------------------------------------------------------

function suggest(state: DemoState, item: WorkItem, minutesAgo?: number): ProposedAction {
  const suggestion = suggestionFor(state, item);
  const at = minutesAgo === undefined ? now() : ago(minutesAgo);
  const action: ProposedAction = {
    id: id("suggestion"),
    workItemId: item.id,
    actionType: suggestion.actionType,
    status: "PENDING_APPROVAL",
    reasoning: suggestion.reasoning,
    draftedResponse: suggestion.draftedResponse,
    finalResponse: null,
    createdAt: at,
    updatedAt: at,
  };

  state.actions.push(action);
  item.status = "WAITING_FOR_APPROVAL";
  item.updatedAt = at;
  record(state, item.id, "PROPOSAL_GENERATED", `Clerova suggested ${DESCRIBE[suggestion.actionType]}`, null, minutesAgo);

  return action;
}

function approve(state: DemoState, action: ProposedAction, finalResponse: string | undefined, minutesAgo?: number) {
  const item = findItem(state, action.workItemId);
  item.suggestionsLeft = MAX_SUGGESTIONS;
  const at = minutesAgo === undefined ? now() : ago(minutesAgo);

  action.status = "APPROVED";
  action.finalResponse = finalResponse?.trim() ? finalResponse.trim() : action.draftedResponse;
  action.updatedAt = at;

  switch (action.actionType as ActionType) {
    case "CREATE_MAINTENANCE_REQUEST":
      if (!state.jobs.some((job) => job.proposedActionId === action.id)) {
        state.jobs.push({
          id: id("job"),
          workItemId: item.id,
          proposedActionId: action.id,
          organizationId: ORG_ID,
          address: item.address,
          description: item.summary,
          priority: item.priority as Priority,
          status: "OPEN",
          notes: null,
          createdAt: at,
          updatedAt: at,
        });
      }
      setStatus(item, "IN_PROGRESS");
      break;
    case "REQUEST_MORE_INFORMATION":
      setStatus(item, "WAITING_FOR_INFORMATION");
      break;
    case "ESCALATE_TO_HUMAN":
      setStatus(item, "ESCALATED");
      break;
    default:
      setStatus(item, "COMPLETED");
  }

  item.updatedAt = at;
  record(state, item.id, "PROPOSAL_APPROVED", `Approved ${DESCRIBE[action.actionType as ActionType]}`, "Alex Morgan", minutesAgo);
}

function sendApproved(state: DemoState, action: ProposedAction, minutesAgo?: number) {
  const item = findItem(state, action.workItemId);
  const at = minutesAgo === undefined ? now() : ago(minutesAgo);

  action.status = "EXECUTED";
  action.updatedAt = at;
  outbound(state, item, action.finalResponse ?? action.draftedResponse, at);
  record(state, item.id, "REPLY_SENT", `Reply sent to ${item.senderEmail}`, "Alex Morgan", minutesAgo);
}

function approveAndSend(state: DemoState, item: WorkItem, minutesAgo: number) {
  const action = suggest(state, item, minutesAgo + 1);
  approve(state, action, undefined, minutesAgo);
  sendApproved(state, action, minutesAgo);
}

/** Like the server: replies end with the company's name, unless they already do. */
function withSignOff(text: string, company: string): string {
  const trimmed = text.trimEnd();
  const lastLine = trimmed.split(/\r?\n/).pop() ?? "";

  if (!company || lastLine.toLowerCase().includes(company.toLowerCase())) return trimmed;
  // "Thanks," is followed by the name on the next line
  return `${trimmed}${lastLine.trim().endsWith(",") ? "\n" : "\n\n"}${company}`;
}

function outbound(state: DemoState, item: WorkItem, text: string, at: string) {
  item.suggestionsLeft = MAX_SUGGESTIONS;
  state.conversations[item.id].push({
    id: id("message"),
    direction: "OUTBOUND",
    from: null,
    to: item.senderEmail,
    subject: item.subject ? `Re: ${item.subject}` : "Re: Your request",
    text: withSignOff(text, state.me.organization?.name ?? ""),
    at,
    attachments: [],
  });
  state.summary.repliesSent += minutesAgoIsNow(at) ? 1 : 0;
}

function minutesAgoIsNow(at: string): boolean {
  return Date.now() - new Date(at).getTime() < 60_000;
}

function setStatus(item: WorkItem, status: string) {
  item.status = status;

  if (status !== "IN_PROGRESS") {
    item.unreadReplyAt = null;
    item.unreadReplyUrgent = false;
  }
}

/** The sender writes again on an existing request. */
function followUp(state: DemoState, item: WorkItem, text: string, minutesAgo: number, urgentReason?: string) {
  item.suggestionsLeft = MAX_SUGGESTIONS;
  const at = ago(minutesAgo);

  state.conversations[item.id].push({
    id: id("message"),
    direction: "INBOUND",
    from: `${item.senderName} <${item.senderEmail}>`,
    to: null,
    subject: item.subject ? `Re: ${item.subject}` : null,
    text,
    at,
    attachments: [],
  });
  state.inbox.push({
    id: id("email"),
    fromEmail: item.senderEmail,
    fromName: item.senderName,
    subject: item.subject ? `Re: ${item.subject}` : null,
    preview: text.slice(0, 140),
    receivedAt: at,
    status: "ATTACHED",
    statusReason: null,
    workItemId: item.id,
    attachments: [],
  });
  record(state, item.id, "FOLLOW_UP_RECEIVED", `New email from ${item.senderEmail}`, null, minutesAgo);

  if (item.status === "IN_PROGRESS") {
    item.unreadReplyAt = at;

    if (urgentReason) {
      item.unreadReplyUrgent = true;
      record(state, item.id, "REPLY_URGENT", `Marked urgent: ${urgentReason}`, null, minutesAgo);
    }
  }

  item.updatedAt = at;
}

function findItem(state: DemoState, workItemId: string): WorkItem {
  const item = state.items.find((candidate) => candidate.id === workItemId);

  if (!item) {
    throw new ApiRequestError("This request no longer exists. Refresh to see the latest.", 404);
  }

  return item;
}

function findAction(state: DemoState, actionId: string): ProposedAction {
  const action = state.actions.find((candidate) => candidate.id === actionId);

  if (!action) {
    throw new ApiRequestError("This suggestion no longer exists. Refresh to see the latest.", 404);
  }

  return action;
}

/** Like the server: 5 new suggestions or drafts per email, either way. */
function takeSuggestion(item: WorkItem) {
  const left = item.suggestionsLeft ?? MAX_SUGGESTIONS;
  if (left <= 0) {
    conflict(`You've used all ${MAX_SUGGESTIONS} new suggestions for this email. Edit the reply yourself. You get ${MAX_SUGGESTIONS} more with the next email.`);
  }
  item.suggestionsLeft = left - 1;
}

function conflict(message: string): never {
  throw new ApiRequestError(message, 409);
}

function latestAction(state: DemoState, workItemId: string): ProposedAction | undefined {
  return state.actions
    .filter((action) => action.workItemId === workItemId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))[0];
}

function requireCanWrite(item: WorkItem) {
  if (CLOSED.includes(item.status)) {
    conflict("This request is already closed.");
  }

  if (!["IN_PROGRESS", "ESCALATED", "WAITING_FOR_INFORMATION"].includes(item.status)) {
    conflict("Reply using Clerova's suggestion for this request.");
  }
}

// ---------------------------------------------------------------------------
// Demo controls
// ---------------------------------------------------------------------------

let state = seedState();

const SIMULATED: Omit<Seed, "key" | "minutesAgo">[] = [
  {
    name: "Carlos Mendez",
    email: "carlos.mendez@gmail.com",
    subject: "Water coming through the ceiling",
    message:
      "Water is dripping from the bathroom ceiling and it's getting faster. I think it's coming from the unit above. 48 Elm St, Unit 3.",
    category: "MAINTENANCE",
    priority: "HIGH",
    summary: "Water dripping from the bathroom ceiling, getting faster; likely from the unit above.",
    address: "48 Elm St, Unit 3",
  },
  {
    name: "Emily Chen",
    email: "emily.chen@icloud.com",
    subject: "Copy of my lease",
    message: "Hi, could you send me a copy of my signed lease? I need it for a car loan application.",
    category: "LEASING",
    priority: "LOW",
    summary: "Asks for a copy of the signed lease for a loan application.",
    address: null,
  },
  {
    name: "Omar Haddad",
    email: "omar.haddad@gmail.com",
    subject: "Garbage disposal jammed",
    message: "The garbage disposal makes a humming sound but doesn't spin. 77 Lake Dr, Unit 9.",
    category: "MAINTENANCE",
    priority: "MEDIUM",
    summary: "Garbage disposal hums but doesn't spin.",
    address: "77 Lake Dr, Unit 9",
  },
];

/** A new tenant email arrives and Clerova organizes it. */
export async function simulateNewEmail(): Promise<string> {
  await wait(700);

  const seed = SIMULATED[state.nextEmail % SIMULATED.length];
  state.nextEmail += 1;

  const item = receiveEmail(state, seed, 0);

  // A lease copy is a plain reply, not an escalation
  if (/copy of my/i.test(seed.message)) {
    item.category = "GENERAL";
  }

  suggest(state, item);
  bumpUsage();
  state.summary.requestsOrganized += 1;
  state.summary.emailsReceived += 1;
  state.summary.allTimeRequests += 1;
  state.summary.minutesSaved += state.summary.minutesPerRequest;
  refreshApp();

  return `New email from ${seed.name}: "${seed.subject}". It's in Needs review with a suggested reply.`;
}

/** A tenant whose job is in progress writes again about something urgent. */
export async function simulateUrgentReply(): Promise<string> {
  await wait(700);

  const item =
    state.items.find((candidate) => candidate.status === "IN_PROGRESS" && !candidate.unreadReplyUrgent && candidate.subject === "Heater won't turn on") ??
    state.items.find((candidate) => candidate.status === "IN_PROGRESS" && !candidate.unreadReplyUrgent);

  if (!item) {
    return "There's no job in progress to reply on. Approve a repair first, then try again.";
  }

  followUp(
    state,
    item,
    "Now there's a gas smell near the furnace closet. It's stronger than this morning.",
    0,
    "Resident smells gas near the furnace."
  );
  refreshApp();

  return `${item.senderName} wrote again about "${item.subject}", and it looks urgent. It's at the top of Today.`;
}

export function resetDemo() {
  state = seedState();
  refreshApp();
}

/** A sample photo for any file in the demo, drawn in the browser. */
export async function demoAttachment(fileId: string): Promise<Blob> {
  const known = Object.values(state.conversations)
    .flat()
    .some((entry) => entry.files?.some((file) => file.id === fileId));

  if (!known) {
    throw new ApiRequestError("This file isn't available.", 404);
  }

  await wait(300);

  const canvas = document.createElement("canvas");
  canvas.width = 800;
  canvas.height = 600;
  const context = canvas.getContext("2d");

  if (context) {
    // Cabinet, pipe and a puddle on the floor
    context.fillStyle = "#e9e4dc";
    context.fillRect(0, 0, 800, 600);
    context.fillStyle = "#8a6f52";
    context.fillRect(80, 60, 640, 330);
    context.fillStyle = "#6e573f";
    context.fillRect(100, 80, 600, 290);
    context.fillStyle = "#b8bcc4";
    context.fillRect(380, 80, 40, 200);
    context.fillRect(380, 260, 160, 36);
    context.fillStyle = "#c9b79c";
    context.fillRect(0, 390, 800, 210);
    context.fillStyle = "rgba(43, 74, 203, 0.35)";
    context.beginPath();
    context.ellipse(420, 470, 260, 70, 0, 0, Math.PI * 2);
    context.fill();
    context.fillStyle = "#16181d";
    context.font = "600 28px system-ui, sans-serif";
    context.fillText("Sample photo (demo)", 24, 572);
  }

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new ApiRequestError("Couldn't open this file.", 500))),
      "image/jpeg",
      0.85
    );
  });
}

function bumpUsage() {
  const organization = state.me.organization!;
  organization.aiRequestsUsedThisMonth += 1;
}

function refreshApp() {
  window.dispatchEvent(new Event(REFRESH_EVENT));
}

// ---------------------------------------------------------------------------
// The API
// ---------------------------------------------------------------------------

function copy<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

export async function handleDemoRequest<T>(
  path: string,
  options: { method?: string; body?: unknown } = {}
): Promise<T> {
  const method = options.method ?? "GET";
  const url = new URL(path, "https://demo.clerova");
  const route = `${method} ${url.pathname}`;
  const body = (options.body ?? {}) as Record<string, unknown>;

  // Feels like a real round trip; the "AI" takes a little longer
  const slow = /proposed-actions$|draft-reply$|^POST \/api\/work-items$/.test(route);
  await wait(slow ? 900 : 150);

  return copy(route_(route, url, body)) as T;
}

function route_(route: string, url: URL, body: Record<string, unknown>): unknown {
  let match: RegExpMatchArray | null;

  // Account
  if (route === "GET /api/me") return state.me;
  if (route === "PATCH /api/me") {
    const name = String(body.organizationName ?? "").trim();
    if (!name) throw new ApiRequestError("Company name is required.", 400);
    state.me.fullName = body.fullName === undefined ? state.me.fullName : String(body.fullName).trim() || null;
    state.me.organization!.name = name;
    return state.me;
  }

  // Requests
  if (route === "GET /api/work-items") {
    const closed = url.searchParams.get("closed") === "true";
    return state.items
      .filter((item) => CLOSED.includes(item.status) === closed)
      .sort((a, b) => (closed ? b.updatedAt.localeCompare(a.updatedAt) : b.createdAt.localeCompare(a.createdAt)));
  }

  if (route === "POST /api/work-items") {
    const message = String(body.message ?? "").trim();
    if (!message) throw new ApiRequestError("Paste or type the request first.", 400);
    const details = classify(message);
    const at = now();
    const item: WorkItem = {
      id: id("request"),
      organizationId: ORG_ID,
      originalMessage: message,
      ...details,
      status: "NEW",
      source: "MANUAL",
      senderEmail: null,
      senderName: null,
      subject: null,
      createdAt: at,
      updatedAt: at,
      unreadReplyAt: null,
      unreadReplyUrgent: false,
      suggestionsLeft: MAX_SUGGESTIONS,
    };
    state.items.push(item);
    state.conversations[item.id] = [];
    state.events[item.id] = [];
    record(state, item.id, "CREATED", "Added with New request", "Alex Morgan");
    suggest(state, item);
    bumpUsage();
    return item;
  }

  if (route === "GET /api/work-items/proposed-actions/latest") {
    return state.items
      .filter((item) => !CLOSED.includes(item.status))
      .map((item) => latestAction(state, item.id))
      .filter(Boolean);
  }

  if ((match = route.match(/^POST \/api\/work-items\/proposed-actions\/([^/]+)\/approve$/))) {
    const action = findAction(state, match[1]);
    if (action.status !== "PENDING_APPROVAL") conflict("This was already handled, maybe in another tab. This is the latest.");
    const finalResponse = typeof body.finalResponse === "string" ? body.finalResponse : undefined;
    if (finalResponse && finalResponse.length > 10_000) throw new ApiRequestError("The reply must be 10,000 characters or fewer.", 400);
    approve(state, action, finalResponse);
    return action;
  }

  if ((match = route.match(/^POST \/api\/work-items\/proposed-actions\/([^/]+)\/reject$/))) {
    const action = findAction(state, match[1]);
    if (action.status !== "PENDING_APPROVAL") conflict("This was already handled, maybe in another tab. This is the latest.");
    action.status = "REJECTED";
    action.updatedAt = now();
    const item = findItem(state, action.workItemId);
    setStatus(item, "NEW");
    item.updatedAt = now();
    record(state, item.id, "PROPOSAL_REJECTED", "Dismissed the suggestion", "Alex Morgan");
    return action;
  }

  if ((match = route.match(/^POST \/api\/work-items\/proposed-actions\/([^/]+)\/send-reply$/))) {
    const action = findAction(state, match[1]);
    if (action.status === "EXECUTED") conflict("This reply was already sent.");
    if (action.status !== "APPROVED") conflict("Approve the reply before sending it.");
    const item = findItem(state, action.workItemId);
    if (item.source !== "EMAIL" || !item.senderEmail) conflict("This request didn't come in by email, so there's no one to reply to.");
    sendApproved(state, action);
    return action;
  }

  if ((match = route.match(/^GET \/api\/work-items\/([^/]+)$/))) return findItem(state, match[1]);

  if ((match = route.match(/^GET \/api\/work-items\/([^/]+)\/conversation$/))) {
    findItem(state, match[1]);
    return state.conversations[match[1]] ?? [];
  }

  if ((match = route.match(/^GET \/api\/work-items\/([^/]+)\/events$/))) {
    findItem(state, match[1]);
    return state.events[match[1]] ?? [];
  }

  if ((match = route.match(/^GET \/api\/work-items\/([^/]+)\/proposed-actions$/))) {
    findItem(state, match[1]);
    return state.actions
      .filter((action) => action.workItemId === match![1])
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  if ((match = route.match(/^POST \/api\/work-items\/([^/]+)\/proposed-actions$/))) {
    const item = findItem(state, match[1]);
    if (CLOSED.includes(item.status)) conflict("This request is already closed.");
    const latest = latestAction(state, item.id);
    if (latest?.status === "PENDING_APPROVAL") conflict("This request already has a suggestion waiting for review.");
    takeSuggestion(item);
    return suggest(state, item);
  }

  if ((match = route.match(/^POST \/api\/work-items\/([^/]+)\/close$/))) {
    const item = findItem(state, match[1]);
    const status = String(body.status ?? "");
    if (!CLOSED.includes(status)) conflict("Choose whether the request is done or cancelled.");
    if (CLOSED.includes(item.status)) conflict("This request is already closed.");
    if (item.status === "WAITING_FOR_APPROVAL") conflict("Approve or dismiss Clerova's suggestion first.");
    if (state.jobs.some((job) => job.workItemId === item.id && (job.status === "OPEN" || job.status === "IN_PROGRESS"))) {
      conflict("Finish or cancel the job for this request first.");
    }
    setStatus(item, status);
    item.updatedAt = now();
    record(state, item.id, "CLOSED", status === "COMPLETED" ? "Marked done" : "Request cancelled", "Alex Morgan");
    return item;
  }

  if ((match = route.match(/^POST \/api\/work-items\/([^/]+)\/draft-reply$/))) {
    const item = findItem(state, match[1]);
    if (item.source !== "EMAIL" || !item.senderEmail) conflict("This request didn't come in by email, so there's no one to reply to.");
    requireCanWrite(item);
    takeSuggestion(item);
    return { reply: draftFor(state, item), suggestionsLeft: item.suggestionsLeft };
  }

  if ((match = route.match(/^POST \/api\/work-items\/([^/]+)\/reply$/))) {
    const item = findItem(state, match[1]);
    const message = String(body.message ?? "").trim();
    if (!message) throw new ApiRequestError("Write a reply before sending.", 400);
    if (message.length > 10_000) throw new ApiRequestError("The reply is too long. Keep it under 10,000 characters.", 400);
    if (item.source !== "EMAIL" || !item.senderEmail) conflict("This request didn't come in by email, so there's no one to reply to.");
    requireCanWrite(item);
    outbound(state, item, message, now());
    record(state, item.id, "REPLY_SENT", `Reply sent to ${item.senderEmail}`, "Alex Morgan");
    item.unreadReplyAt = null;
    item.unreadReplyUrgent = false;
    item.updatedAt = now();
    return item;
  }

  if ((match = route.match(/^POST \/api\/work-items\/([^/]+)\/reply-handled$/))) {
    const item = findItem(state, match[1]);
    if (item.unreadReplyAt) {
      item.unreadReplyAt = null;
      item.unreadReplyUrgent = false;
      record(state, item.id, "REPLY_HANDLED", "Marked the new email as handled", "Alex Morgan");
    }
    return item;
  }

  // Jobs
  if (route === "GET /api/maintenance-requests") {
    const since = Date.now() - 90 * 24 * 60 * 60_000;
    return state.jobs
      .filter((job) => job.status === "OPEN" || job.status === "IN_PROGRESS" || new Date(job.updatedAt).getTime() > since)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  if ((match = route.match(/^GET \/api\/maintenance-requests\/work-item\/([^/]+)$/))) {
    return state.jobs.filter((job) => job.workItemId === match![1]);
  }

  if ((match = route.match(/^PATCH \/api\/maintenance-requests\/([^/]+)$/))) {
    const job = state.jobs.find((candidate) => candidate.id === match![1]);
    if (!job) throw new ApiRequestError("This job no longer exists. Refresh to see the latest.", 404);

    const terminal = job.status === "COMPLETED" || job.status === "CANCELLED";
    const status = body.status as MaintenanceRequest["status"] | undefined;
    const changed = status !== undefined && status !== job.status;

    if (terminal && changed) conflict("This job is already done or cancelled.");

    if (changed) {
      const allowed =
        (job.status === "OPEN" && (status === "IN_PROGRESS" || status === "CANCELLED")) ||
        (job.status === "IN_PROGRESS" && (status === "COMPLETED" || status === "CANCELLED"));
      if (!allowed) conflict("This job can't move to that step. Refresh and try again.");
      job.status = status!;
      record(state, job.workItemId, "MAINTENANCE_UPDATED",
        { OPEN: "Job reopened", IN_PROGRESS: "Job started", COMPLETED: "Job done", CANCELLED: "Job cancelled" }[status!], "Alex Morgan");

      if (status === "COMPLETED" || status === "CANCELLED") {
        const item = findItem(state, job.workItemId);
        setStatus(item, status);
        item.updatedAt = now();
        if (status === "COMPLETED") state.summary.jobsCompleted += 1;
      }
    }

    if (typeof body.notes === "string") {
      if (body.notes.length > 5000) throw new ApiRequestError("Notes must be 5,000 characters or fewer.", 400);
      const cleaned = body.notes.trim() || null;
      if (cleaned !== job.notes) {
        job.notes = cleaned;
        record(state, job.workItemId, "MAINTENANCE_UPDATED", cleaned ? "Job notes updated" : "Job notes cleared", "Alex Morgan");
      }
    }

    job.updatedAt = now();
    return job;
  }

  // Email
  if (route === "GET /api/inbox") {
    return [...state.inbox].sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  }

  if ((match = route.match(/^POST \/api\/inbox\/([^/]+)\/reprocess$/))) {
    const email = state.inbox.find((candidate) => candidate.id === match![1]);
    if (!email) throw new ApiRequestError("This email no longer exists.", 404);
    if (email.status !== "IGNORED" && email.status !== "FAILED") conflict("This email was already processed.");
    const item = receiveEmail(state, {
      name: email.fromName ?? "Sender",
      email: email.fromEmail ?? "sender@example.com",
      subject: email.subject ?? "(no subject)",
      message: email.preview,
      category: "GENERAL",
      priority: "LOW",
      summary: "Newsletter about property management trends.",
      address: null,
    }, 0);
    // The copy made by receiveEmail replaces the original row
    state.inbox = state.inbox.filter((candidate) => candidate.id !== email.id);
    suggest(state, item);
    window.setTimeout(refreshApp, 0);
    return { ...email, status: "RECEIVED", statusReason: null };
  }

  if (route === "GET /api/email-settings") return state.settings;
  if (route === "PUT /api/email-settings/reply-copy") {
    const email = String(body.email ?? "").trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new ApiRequestError("Enter a valid email address.", 400);
    if (email.toLowerCase().endsWith("@in.clerova-ai.com")) conflict("Use your own email here, not your Clerova address.");
    state.settings.replyCopyEmail = email || null;
    return state.settings;
  }
  if (route === "GET /api/summary") return state.summary;

  if (route === "GET /api/company-info") return { ...state.companyInfo };
  if (route === "PUT /api/company-info") {
    const info: CompanyInfo = {};
    let total = 0;
    for (const field of COMPANY_INFO_FIELDS) {
      const value = typeof body[field.key] === "string" ? String(body[field.key]).trim() : "";
      if (value.length > MAX_FIELD_LENGTH) throw new ApiRequestError("Keep each answer under 1,500 characters.", 400);
      total += value.length;
      info[field.key] = value || null;
    }
    if (total > MAX_TOTAL_LENGTH) {
      throw new ApiRequestError("That's more than Clerova can use. Keep all answers together under 8,000 characters.", 400);
    }
    state.companyInfo = info;
    return { ...info };
  }

  if (route === "GET /api/billing/offer") return { enabled: false, foundingSpotsLeft: 0 };

  throw new ApiRequestError("That isn't part of the demo.", 404);
}

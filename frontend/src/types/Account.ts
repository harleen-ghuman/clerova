export type Industry = "PROPERTY_MANAGEMENT" | "TRUCKING";

export interface Organization {
  id: string;
  name: string;
  industry: Industry;
  plan: "TRIAL" | "STARTER" | "GROWTH" | "FLEET" | "INTERNAL";
  trialEndsAt: string | null;
  aiMonthlyLimit: number;
  aiRequestsUsedThisMonth: number;
  /** Stripe status: trialing, active, past_due, canceled, ... */
  subscriptionStatus: string | null;
  billingInterval: "month" | "year" | null;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  foundingCustomer: boolean;
  billingEnabled: boolean;
  /** A plan must be chosen before the app can be used. */
  subscriptionRequired: boolean;
  /** Trucks on a trucking plan (priced by truck); null otherwise. */
  trucks?: number | null;
  /** IANA time zone for invoice dates, like America/Chicago; null until set. */
  timeZone?: string | null;
  /** US or CA. Canadian carriers pay in CAD. */
  country?: "US" | "CA";
}

export interface Me {
  userId: string;
  email: string;
  fullName: string | null;
  organization: Organization | null;
}

export interface OnboardingDetails {
  organizationName: string;
  industry: Industry;
  companySize: string;
  referralSource: string;
  fullName: string;
  /** CA for a Canadian carrier. */
  country?: "US" | "CA";
}

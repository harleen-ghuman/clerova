export type Industry = "PROPERTY_MANAGEMENT" | "TRUCKING";

export interface Organization {
  id: string;
  name: string;
  industry: Industry;
  plan: "TRIAL" | "STARTER" | "GROWTH" | "INTERNAL";
  trialEndsAt: string | null;
  aiMonthlyLimit: number;
  aiRequestsUsedThisMonth: number;
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
}

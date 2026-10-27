import { apiRequest } from "./client";

export const COMPANY_INFO_FIELDS = [
  {
    key: "rentAndPayments",
    label: "Rent and payments",
    example: "Rent is due on the 1st. Pay in the resident portal. 5-day grace period, then a $50 late fee.",
  },
  {
    key: "officeAndContact",
    label: "Office hours and contact",
    example: "Monday to Friday, 9am to 5pm. Call (555) 201-3300 or reply to any email.",
  },
  {
    key: "emergencies",
    label: "Emergencies and after hours",
    example: "After hours, call (555) 201-3399. Emergencies are fire, gas, flooding, no heat or lockouts.",
  },
  {
    key: "maintenance",
    label: "Maintenance and repairs",
    example: "Most repairs are scheduled within 2 business days. We give 24 hours' notice before entering.",
  },
  {
    key: "utilities",
    label: "Utilities",
    example: "Water and trash are included. Residents set up electricity and internet themselves.",
  },
  {
    key: "pets",
    label: "Pets",
    example: "Cats and dogs under 40 lb, two at most, with a $300 pet deposit.",
  },
  {
    key: "parking",
    label: "Parking",
    example: "One assigned spot per unit. Guests park on the street.",
  },
  {
    key: "trashAndRecycling",
    label: "Trash and recycling",
    example: "Pickup is Tuesday morning. Bins go back by Tuesday night.",
  },
  {
    key: "noiseAndGuests",
    label: "Noise, guests and quiet hours",
    example: "Quiet hours are 10pm to 8am. Guests can stay up to 14 nights.",
  },
  {
    key: "keysAndLockouts",
    label: "Keys and lockouts",
    example: "Lockouts after hours cost $75. Replacement keys are $25 from the office.",
  },
  {
    key: "moveInAndOut",
    label: "Moving in and out",
    example: "Give 30 days' written notice. Deposits are returned within 21 days.",
  },
  {
    key: "renewals",
    label: "Lease renewals",
    example: "Renewal offers go out 60 days before the lease ends.",
  },
  {
    key: "other",
    label: "Anything else residents often ask",
    example: "Package room hours, laundry, smoking rules, and so on.",
  },
] as const;

export type CompanyInfoKey = (typeof COMPANY_INFO_FIELDS)[number]["key"];
export type CompanyInfo = Partial<Record<CompanyInfoKey, string | null>>;

export const MAX_FIELD_LENGTH = 1500;
export const MAX_TOTAL_LENGTH = 8000;

export function getCompanyInfo(): Promise<CompanyInfo> {
  return apiRequest("/api/company-info");
}

export function saveCompanyInfo(info: CompanyInfo): Promise<CompanyInfo> {
  return apiRequest("/api/company-info", { method: "PUT", body: info });
}

/** Whether any answer is filled in. */
export function hasCompanyInfo(info: CompanyInfo | null): boolean {
  return Boolean(info && Object.values(info).some((value) => value && value.trim() !== ""));
}

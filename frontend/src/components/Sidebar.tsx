import type { Organization } from "../types/Account";
import { planName } from "../utils/plans";

export type AppView = "requests" | "jobs" | "inbox" | "email-setup";

interface SidebarProps {
  activeView: AppView;
  reviewCount: number;
  openJobCount: number;
  organization: Organization;
  userEmail: string;
  onNavigate: (view: AppView) => void;
  onSignOut: () => void;
  onManageBilling: () => void;
}

const DAY_MS = 24 * 60 * 60 * 1000;

function shortDate(value: string | null): string {
  return value
    ? new Date(value).toLocaleDateString([], { month: "short", day: "numeric" })
    : "";
}

/** One line about the plan, e.g. "Starter · trial, 12 days left". */
function planStatus(organization: Organization): string {
  const days = trialDaysLeft(organization.trialEndsAt);

  if (!organization.billingEnabled || organization.plan === "TRIAL") {
    return days === null
      ? planName(organization.plan)
      : days === 0
        ? "Free trial ended"
        : `Free trial · ${days} ${days === 1 ? "day" : "days"} left`;
  }

  const name = planName(organization.plan);

  switch (organization.subscriptionStatus) {
    case "trialing":
      return `${name} · trial, ${days ?? 0} ${days === 1 ? "day" : "days"} left`;
    case "past_due":
      return `${name} · payment failed`;
    default:
      return organization.cancelAtPeriodEnd
        ? `${name} · ends ${shortDate(organization.currentPeriodEnd)}`
        : `${name} plan`;
  }
}

function trialDaysLeft(trialEndsAt: string | null): number | null {
  if (!trialEndsAt) {
    return null;
  }

  return Math.max(
    0,
    Math.ceil((new Date(trialEndsAt).getTime() - Date.now()) / DAY_MS)
  );
}

export function Sidebar({
  activeView,
  reviewCount,
  openJobCount,
  organization,
  userEmail,
  onNavigate,
  onSignOut,
  onManageBilling,
}: SidebarProps) {
  const items: { view: AppView; label: string; count?: number; badge?: boolean }[] = [
    { view: "requests", label: "Requests", count: reviewCount, badge: true },
    { view: "jobs", label: "Jobs", count: openJobCount },
    { view: "inbox", label: "Inbox" },
    { view: "email-setup", label: "Email setup" },
  ];

  return (
    <aside className="sidebar">
      <button
        type="button"
        className="sidebar-brand"
        onClick={() => onNavigate("requests")}
        aria-label="Go to requests"
      >
        <span className="sidebar-mark">C</span>
        <span>
          <strong>Clerova</strong>
          <small>{organization.name}</small>
        </span>
      </button>

      <nav className="sidebar-nav" aria-label="Main">
        {items.map((item) => (
          <button
            key={item.view}
            type="button"
            className={`sidebar-link ${activeView === item.view ? "active" : ""}`}
            aria-current={activeView === item.view ? "page" : undefined}
            onClick={() => onNavigate(item.view)}
          >
            {item.label}
            {item.count !== undefined && item.count > 0 && (
              <span className={item.badge ? "sidebar-badge" : "sidebar-count"}>
                {item.count}
              </span>
            )}
          </button>
        ))}
      </nav>

      <div className="sidebar-footer">
        {organization.plan !== "INTERNAL" && (
          <p>
            {planStatus(organization)}
            <br />
            {organization.aiRequestsUsedThisMonth} / {organization.aiMonthlyLimit}{" "}
            requests this month
          </p>
        )}

        {organization.billingEnabled && organization.subscriptionStatus && (
          <button type="button" className="sidebar-billing" onClick={onManageBilling}>
            Manage billing
          </button>
        )}

        <div className="sidebar-account">
          <small title={userEmail}>{userEmail}</small>
          <button type="button" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </div>
    </aside>
  );
}

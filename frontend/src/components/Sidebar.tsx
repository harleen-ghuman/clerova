import type { Organization } from "../types/Account";

export type AppView = "requests" | "jobs" | "inbox" | "email-setup";

interface SidebarProps {
  activeView: AppView;
  reviewCount: number;
  openJobCount: number;
  organization: Organization;
  userEmail: string;
  onNavigate: (view: AppView) => void;
  onSignOut: () => void;
}

const DAY_MS = 24 * 60 * 60 * 1000;

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
}: SidebarProps) {
  const daysLeft =
    organization.plan === "TRIAL"
      ? trialDaysLeft(organization.trialEndsAt)
      : null;

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
        {daysLeft !== null && (
          <p>
            {daysLeft === 0
              ? "Free trial ended"
              : `Free trial · ${daysLeft} ${daysLeft === 1 ? "day" : "days"} left`}
            <br />
            {organization.aiRequestsUsedThisMonth} / {organization.aiMonthlyLimit}{" "}
            AI requests
          </p>
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

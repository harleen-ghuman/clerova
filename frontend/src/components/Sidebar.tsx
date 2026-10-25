import type { Organization } from "../types/Account";

export type AppView =
  | "dashboard"
  | "inbox"
  | "work-items"
  | "maintenance"
  | "pending-actions"
  | "email-setup";

interface SidebarProps {
  activeView: AppView;
  pendingActionCount: number;
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
  pendingActionCount,
  organization,
  userEmail,
  onNavigate,
  onSignOut,
}: SidebarProps) {
  const daysLeft =
    organization.plan === "TRIAL"
      ? trialDaysLeft(organization.trialEndsAt)
      : null;

  return (
    <aside className="sidebar">
      <button
        type="button"
        className="brand brand-button"
        onClick={() => onNavigate("dashboard")}
        aria-label="Go to dashboard"
      >
        <div className="brand-mark">C</div>

        <div>
          <strong>Clerova</strong>
          <span>
            {organization.industry === "TRUCKING"
              ? "Trucking Operations"
              : "Property Operations"}
          </span>
        </div>
      </button>

      <button
        type="button"
        className="sidebar-mobile-signout"
        onClick={onSignOut}
      >
        Sign out
      </button>

      <nav className="sidebar-nav">
        <button
          type="button"
          className={`nav-item ${
            activeView === "dashboard" ? "active" : ""
          }`}
          onClick={() => onNavigate("dashboard")}
        >
          Dashboard
        </button>

        <button
          type="button"
          className={`nav-item ${
            activeView === "inbox" ? "active" : ""
          }`}
          onClick={() => onNavigate("inbox")}
        >
          Inbox
        </button>

        <button
          type="button"
          className={`nav-item ${
            activeView === "work-items" ? "active" : ""
          }`}
          onClick={() => onNavigate("work-items")}
        >
          Work Items
        </button>

        <button
          type="button"
          className={`nav-item ${
            activeView === "maintenance" ? "active" : ""
          }`}
          onClick={() => onNavigate("maintenance")}
        >
          Maintenance
        </button>

        <button
          type="button"
          className={`nav-item ${
            activeView === "pending-actions" ? "active" : ""
          }`}
          onClick={() => onNavigate("pending-actions")}
        >
          <span>Pending Actions</span>

          {pendingActionCount > 0 && (
            <span className="nav-count">
              {pendingActionCount}
            </span>
          )}
        </button>

        <button
          type="button"
          className={`nav-item ${
            activeView === "email-setup" ? "active" : ""
          }`}
          onClick={() => onNavigate("email-setup")}
        >
          Email Setup
        </button>
      </nav>

      <div className="sidebar-footer">
        <span>Organization</span>
        <strong>{organization.name}</strong>

        {daysLeft !== null && (
          <p className="sidebar-trial">
            {daysLeft === 0
              ? "Free trial ended"
              : `Free trial · ${daysLeft} ${daysLeft === 1 ? "day" : "days"} left`}
            <br />
            {organization.aiRequestsUsedThisMonth} /{" "}
            {organization.aiMonthlyLimit} AI requests this month
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
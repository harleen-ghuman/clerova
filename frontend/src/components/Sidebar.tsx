export type AppView =
  | "dashboard"
  | "work-items"
  | "maintenance"
  | "pending-actions";

interface SidebarProps {
  activeView: AppView;
  pendingActionCount: number;
  onNavigate: (view: AppView) => void;
}

export function Sidebar({
  activeView,
  pendingActionCount,
  onNavigate,
}: SidebarProps) {
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
          <span>Property Operations</span>
        </div>
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
      </nav>

      <div className="sidebar-footer">
        <span>Organization</span>
        <strong>Demo Property Management</strong>
      </div>
    </aside>
  );
}
export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-mark">C</div>

        <div>
          <strong>Clerova</strong>
          <span>Property Operations</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        <button className="nav-item active">
          Dashboard
        </button>

        <button className="nav-item">
          Work Items
        </button>

        <button className="nav-item">
          Pending Actions
        </button>
      </nav>

      <div className="sidebar-footer">
        <span>Organization</span>
        <strong>Demo Property Management</strong>
      </div>
    </aside>
  );
}
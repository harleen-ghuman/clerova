interface StatCardProps {
  label: string;
  value: number;
  helperText?: string;
}

export function StatCard({
  label,
  value,
  helperText,
}: StatCardProps) {
  return (
    <div className="stat-card">
      <span className="stat-label">{label}</span>

      <strong className="stat-value">{value}</strong>

      {helperText && (
        <span className="stat-helper">
          {helperText}
        </span>
      )}
    </div>
  );
}
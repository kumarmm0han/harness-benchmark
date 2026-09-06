interface FilterBarProps {
  domain: string;
  risk: string;
  onDomain: (v: string) => void;
  onRisk: (v: string) => void;
  disabled?: boolean;
}

export function FilterBar({ domain, risk, onDomain, onRisk }: FilterBarProps) {
  return (
    <div className="actions-bar" role="group" aria-label="Publish filters">
      <label htmlFor="filter-domain">Domain</label>
      <select id="filter-domain" value={domain} onChange={(e) => onDomain(e.target.value)}>
        <option value="">All</option>
        <option value="Billing">Billing</option>
        <option value="Support">Support</option>
      </select>
      <label htmlFor="filter-risk">Risk</label>
      <select id="filter-risk" value={risk} onChange={(e) => onRisk(e.target.value)}>
        <option value="">All</option>
        <option value="low">low</option>
        <option value="medium">medium</option>
      </select>
    </div>
  );
}

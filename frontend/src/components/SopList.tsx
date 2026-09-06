import type { SopSummary } from '../api/types';

interface SopListProps {
  sops: SopSummary[];
  onOpen: (sopId: string) => void;
}

export function SopList({ sops, onOpen }: SopListProps) {
  if (sops.length === 0) {
    return <div className="toast ok" role="status">No published SOPs match. Try clearing the filters.</div>;
  }
  return (
    <table aria-label="Published SOPs">
      <thead>
        <tr>
          <th> SOP ID </th>
          <th>Title</th>
          <th>Version</th>
          <th>Domain</th>
          <th>Risk</th>
        </tr>
      </thead>
      <tbody>
        {sops.map((s) => (
          <tr key={s.sop_id}>
            <td>
              <a
                className="row-link"
                href={`#/sop/${encodeURIComponent(s.sop_id)}`}
                onClick={(e) => {
                  e.preventDefault();
                  onOpen(s.sop_id);
                }}
              >
                {s.sop_id}
              </a>
            </td>
            <td>{s.title}</td>
            <td>{s.version}</td>
            <td>{s.domain}</td>
            <td>{s.risk}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

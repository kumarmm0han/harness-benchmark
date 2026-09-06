import type { DraftSummary } from '../api/types';

interface DraftListProps {
  drafts: DraftSummary[];
  onOpen: (sopId: string) => void;
}

export function DraftList({ drafts, onOpen }: DraftListProps) {
  if (drafts.length === 0) {
    return <div className="toast ok" role="status">No saved drafts yet.</div>;
  }
  return (
    <table aria-label="Saved drafts">
      <thead>
        <tr>
          <th>SOP ID</th>
          <th>Revision</th>
          <th>Publish status</th>
        </tr>
      </thead>
      <tbody>
        {drafts.map((d) => (
          <tr key={d.sop_id}>
            <td>
              <a
                className="row-link"
                href={`#/editor/${encodeURIComponent(d.sop_id)}`}
                onClick={(e) => {
                  e.preventDefault();
                  onOpen(d.sop_id);
                }}
              >
                {d.sop_id}
              </a>
            </td>
            <td>{d.revision}</td>
            <td>
              {d.publish_failed ? (
                <span className="failed-indicator" data-testid="draft-failed">
                  Publish failed — previous version retained
                </span>
              ) : (
                <span>No known failures</span>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

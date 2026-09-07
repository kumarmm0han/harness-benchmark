import { useEffect, useState } from 'react';
import { api, ApiError } from '../api';
import type { DraftSummary, Identity } from '../types';

interface Props {
  identity: Identity;
  onOpen: (sopId: string) => void;
  onNew: () => void;
}

type Status =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'loaded'; rows: DraftSummary[] };

/**
 * Author-only saved drafts (FR-050, FR-045): revision plus the
 * publication-failure indicator in text (never color-only, NFR-050).
 */
export default function Drafts({ identity, onOpen, onNew }: Props) {
  const [status, setStatus] = useState<Status>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    api
      .getDrafts(identity)
      .then((res) => {
        if (!cancelled) setStatus({ kind: 'loaded', rows: res.drafts });
      })
      .catch((e: unknown) => {
        if (!cancelled) setStatus({ kind: 'error', message: e instanceof ApiError ? e.message : 'Failed to load drafts.' });
      });
    return () => {
      cancelled = true;
    };
  }, [identity]);

  return (
    <section aria-labelledby="drafts-heading">
      <h2 id="drafts-heading">My drafts (author)</h2>
      <p className="hint">
        One editable draft per SOP. Saving never changes published content; the revision increments on
        every save.
      </p>
      <button type="button" onClick={onNew}>New draft</button>
      {status.kind === 'loading' && <p role="status">Loading drafts…</p>}
      {status.kind === 'error' && <p role="alert" className="bad">{status.message}</p>}
      {status.kind === 'loaded' &&
        (status.rows.length === 0 ? (
          <p className="empty">No saved drafts yet. Create one to start authoring.</p>
        ) : (
          <table className="draft-list">
            <caption className="sr-only">Saved drafts</caption>
            <thead>
              <tr>
                <th scope="col">SOP ID</th>
                <th scope="col">Saved revision</th>
                <th scope="col">Publication status</th>
                <th scope="col">
                  <span className="sr-only">Open</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {status.rows.map((row) => (
                <tr key={row.sop_id}>
                  <td>
                    <code>{row.sop_id}</code>
                  </td>
                  <td>{row.revision}</td>
                  <td>
                    {row.publish_failed ? (
                      <span className="fail" role="status">
                        Last publication failed — the previously published version remains current.
                      </span>
                    ) : (
                      <span>—</span>
                    )}
                  </td>
                  <td>
                    <button type="button" onClick={() => onOpen(row.sop_id)}>
                      Open
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}
    </section>
  );
}

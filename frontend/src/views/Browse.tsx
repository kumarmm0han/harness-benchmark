import { useEffect, useState } from 'react';
import { api, ApiError } from '../api';
import type { Identity, SopSummary } from '../types';

interface Props {
  identity: Identity;
  onOpen: (sopId: string) => void;
}

type Status = { kind: 'idle' } | { kind: 'loading' } | {
  kind: 'loaded';
  rows: SopSummary[];
} |
{ kind: 'error'; message: string };

/**
 * Published SOP list with domain/risk filters (FR-050). The backend enforces
 * fixed filter semantics, ORDER BY sop_id, and 400 for invalid values — the UI
 * only offers the supported values and shows the server's message on errors.
 */
export default function Browse({ identity, onOpen }: Props) {
  const [domain, setDomain] = useState('');
  const [risk, setRisk] = useState('');
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  useEffect(() => {
    let cancelled = false;
    setStatus({ kind: 'loading' });
    api
      .listSops(identity, domain || undefined, risk || undefined)
      .then((res) => {
        if (!cancelled) setStatus({ kind: 'loaded', rows: res.sops });
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          const message = e instanceof ApiError ? `${e.code}: ${e.message}` : 'Failed to load the list.';
          setStatus({ kind: 'error', message });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [identity, domain, risk]);

  return (
    <section aria-labelledby="browse-heading">
      <h2 id="browse-heading">Published SOPs</h2>
      <p className="hint">
        Filters combine with AND; results are sorted by SOP ID. Invalid filter values are rejected by the API.
      </p>
      <form
        className="filters"
        onSubmit={(e) => e.preventDefault()}
        aria-label="Filter published SOPs"
      >
        <label>
          Domain{' '}
          <select value={domain} onChange={(e) => setDomain(e.target.value)}>
            <option value="">All</option>
            <option value="Billing">Billing</option>
            <option value="Support">Support</option>
          </select>
        </label>{' '}
        <label>
          Risk{' '}
          <select value={risk} onChange={(e) => setRisk(e.target.value)}>
            <option value="">All</option>
            <option value="low">low</option>
            <option value="medium">medium</option>
          </select>
        </label>
      </form>

      {status.kind === 'loading' && <p role="status">Loading published SOPs…</p>}
      {status.kind === 'error' && (
        <p role="alert" className="bad">
          {status.message}
        </p>
      )}
      {status.kind === 'loaded' &&
        (status.rows.length === 0 ? (
          <p className="empty">
            No published SOPs match. As an author, save a draft and publish it to see it here.
          </p>
        ) : (
          <table className="sop-list">
            <caption className="sr-only">Current published SOPs</caption>
            <thead>
              <tr>
                <th scope="col">SOP ID</th>
                <th scope="col">Title</th>
                <th scope="col">Current version</th>
                <th scope="col">Domain</th>
                <th scope="col">Risk</th>
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
                  <td>{row.title ?? '—'}</td>
                  <td>{row.version}</td>
                  <td>{row.domain ?? '—'}</td>
                  <td>{row.risk_level ?? '—'}</td>
                  <td>
                    <button type="button" onClick={() => onOpen(row.sop_id)}>
                      View
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

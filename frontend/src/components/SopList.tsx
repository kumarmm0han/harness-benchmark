import { useCallback, useEffect, useState } from "react";
import { ApiClient, ApiClientError } from "../api";
import type { DraftSummary, Identity, SopSummary } from "../types";

interface Props {
  identity: Identity;
  client: ApiClient;
  onOpenSop: (sopId: string) => void;
  onOpenDraft: (sopId: string) => void;
}

const DOMAINS = ["Any", "Billing", "Support"];
const RISKS = ["Any", "low", "medium"];

function describe(e: unknown): string {
  if (e instanceof ApiClientError) {
    const base = `${e.code}: ${e.message}`;
    if (e.issues.length > 0) {
      const detail = e.issues
        .map((i) => (i.path ? `${i.path}: ${i.message}` : i.message))
        .join("; ");
      return `${base} — ${detail}`;
    }
    return base;
  }
  return e instanceof Error ? `Error: ${e.message}` : "Something went wrong.";
}

/**
 * Published SOP list with domain/risk AND-filters (FR-050), keyboard-usable controls,
 * text-only status/errors (NFR-050, NFR-020), and the author-only draft list (FR-001/050).
 * Consumers see no draft controls.
 */
export default function SopList({ identity, client, onOpenSop, onOpenDraft }: Props) {
  const author = identity === "demo-author";
  const [domain, setDomain] = useState<string>("Any");
  const [risk, setRisk] = useState<string>("Any");
  const [sops, setSops] = useState<SopSummary[]>([]);
  const [drafts, setDrafts] = useState<DraftSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await client.listSops({
        domain: domain === "Any" ? undefined : domain,
        risk: risk === "Any" ? undefined : risk,
      });
      setSops(data);
      setDrafts(author ? await client.listDrafts() : []);
    } catch (e) {
      setSops([]);
      setDrafts([]);
      setError(describe(e));
    } finally {
      setLoading(false);
    }
  }, [client, domain, risk, author]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <section aria-label="SOP catalog and drafts">
      <div className="filters" role="group" aria-label="Filters">
        <label className="filter">
          Domain
          <select value={domain} onChange={(e) => setDomain(e.target.value)} aria-label="Filter by domain">
            {DOMAINS.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </select>
        </label>
        <label className="filter">
          Risk
          <select value={risk} onChange={(e) => setRisk(e.target.value)} aria-label="Filter by risk level">
            {RISKS.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="ghost" onClick={() => void load()}>
          Refresh
        </button>
      </div>

      <h2>Published SOPs</h2>
      {loading && <p role="status">Loading…</p>}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {!loading && !error && sops.length === 0 && (
        <p className="empty" data-testid="empty-state">
          No published SOPs match.
        </p>
      )}
      {!loading && !error && sops.length > 0 && (
        <ul className="sop-list">
          {sops.map((s) => (
            <li key={s.sop_id} className="sop-item">
              <span className="sop-id">{s.sop_id}</span>
              <span className="sop-title">{s.title}</span>
              <span className="sop-meta">
                v{s.version} · {s.domain} · {s.risk}
              </span>
              <button type="button" onClick={() => onOpenSop(s.sop_id)}>
                Open
              </button>
            </li>
          ))}
        </ul>
      )}

      {author && (
        <>
          <h2>Your drafts</h2>
          {!loading && !error && drafts.length === 0 && <p className="empty">No saved drafts yet.</p>}
          {!loading && !error && drafts.length > 0 && (
            <ul className="draft-list">
              {drafts.map((d) => (
                <li key={d.sop_id} className="draft-item">
                  <span className="sop-id">{d.sop_id}</span>
                  <span className="sop-meta">rev {d.revision}</span>
                  {d.publication_failed && (
                    <span className="flag" role="status">
                      ⚠ publishing failed (previous version still current)
                    </span>
                  )}
                  <button type="button" onClick={() => onOpenDraft(d.sop_id)}>
                    Open
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}

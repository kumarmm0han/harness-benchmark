// SOP detail (TASK-009, DES-014, FR-052, FR-053, NFR-020).
//
// Fetches the CURRENT published snapshot once and renders it in two views — Human and
// JSON — from the SAME object, so a publication happening during viewing cannot mix
// versions. All authored strings are rendered as text (no dangerouslySetInnerHTML /
// innerHTML), so `<script>` etc. are literal text, never executed (NFR-020). No mutation
// happens here; this is a pure read.
import { useCallback, useEffect, useState } from "react";
import { ApiClientError } from "../api";
import type { ApiClient } from "../api";
import type { Published } from "../types";

interface Props {
  sopId: string;
  client: ApiClient;
  onBack: () => void;
}

type View = "human" | "json";

function describeError(e: unknown): string {
  if (e instanceof ApiClientError) {
    const base = `${e.code}: ${e.message}`;
    if (e.issues.length > 0) {
      return `${base} — ${e.issues.map((i) => (i.path ? `${i.path}: ${i.message}` : i.message)).join("; ")}`;
    }
    return base;
  }
  return e instanceof Error ? e.message : "Failed to load this SOP.";
}

/** Render an authored scalar as its string form (booleans/numbers included). */
function text(v: unknown): string {
  if (v == null) return "";
  if (typeof v === "string") return v;
  if (typeof v === "number" || typeof v === "boolean") return String(v);
  return JSON.stringify(v);
}

export default function SopDetail({ sopId, client, onBack }: Props) {
  const [published, setPublished] = useState<Published | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<View>("human");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPublished(await client.getSop(sopId));
    } catch (e) {
      const notFound = e instanceof ApiClientError && e.status === 404;
      setError(notFound ? "No published version yet for this SOP." : describeError(e));
    } finally {
      setLoading(false);
    }
  }, [client, sopId]);

  useEffect(() => {
    void load();
  }, [load]);

  const c = published?.content ?? null;

  return (
    <section className="detail" aria-label={`Published SOP ${sopId}`}>
      <button type="button" className="ghost" onClick={onBack}>
        &larr; Back
      </button>
      <h2>SOP {sopId}</h2>

      {loading && <p role="status">Loading published snapshot…</p>}
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      {!loading && !error && published && c && (
        <>
          <div className="identity-badges">
            <span className="badge" data-testid="sop-id">
              SOP: {published.sop_id}
            </span>
            <span className="badge" data-testid="version">
              Version: {published.version}
            </span>
            <span className="badge">Domain: {c.domain}</span>
            <span className="badge">Risk: {c.risk_level}</span>
            <span className="badge">Intent: {c.intent}</span>
            <span className="badge">Autonomy: {c.max_autonomy}</span>
            <span className="badge">Published: {published.published_at}</span>
          </div>
          <p className="note muted">
            Read-only. The actions and messages below describe this SOP&apos;s policy; they are not
            executed by this app.
          </p>

          <div className="view-toggle" role="group" aria-label="Content view">
            <button
              type="button"
              aria-pressed={view === "human"}
              onClick={() => setView("human")}
              className={view === "human" ? "active" : ""}
            >
              Human
            </button>
            <button
              type="button"
              aria-pressed={view === "json"}
              onClick={() => setView("json")}
              className={view === "json" ? "active" : ""}
            >
              JSON
            </button>
          </div>

          {view === "human" ? (
            <div className="human">
              <h3>
                {c.title} {c.owner_team && <span className="muted">— {c.owner_team}</span>}
              </h3>

              <h3>When to use</h3>
              <ul>
                {c.policy?.use_when?.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
              <h3>Do not use when</h3>
              <ul>
                {c.policy?.do_not_use_when?.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>

              <h3>Inputs</h3>
              <ul>
                {c.inputs?.map((inp) => (
                  <li key={inp.name}>
                    {inp.name} ({inp.type})
                  </li>
                ))}
              </ul>

              <h3>Eligibility rules</h3>
              <ul>
                {c.rules?.length === 0 && <li className="empty">No rules.</li>}
                {c.rules?.map((r) => (
                  <li key={r.id} className="rule">
                    <strong>{r.id}</strong>: {r.conditions?.map((cd) => `${cd.input} ${cd.op} ${text(cd.value)}`).join(" AND ") || "(none)"}{" "}
                    &rarr; {r.action_ids?.join(", ")}
                  </li>
                ))}
              </ul>

              <h3>Actions</h3>
              <ul>
                {c.actions?.map((a) => (
                  <li key={a.id} className="action">
                    <strong>{a.id}</strong> ({a.kind}) — {a.description}
                    {a.max_amount != null && <span className="muted"> · max {text(a.max_amount)}</span>}
                  </li>
                ))}
              </ul>
              <p className="muted">Action and message text describes the SOP; it is not executed here.</p>

              <h3>Boundaries / escalation</h3>
              <ul>
                {c.boundaries?.escalation?.length === 0 && <li className="empty">No escalation boundaries.</li>}
                {c.boundaries?.escalation?.map((b, i) => (
                  <li key={i}>
                    <strong>{b.action_id}</strong>: {b.input} {b.op} {text(b.amount)} &rarr; {b.target_action_id}
                  </li>
                ))}
              </ul>

              <h3>Customer messages</h3>
              <div>
                <strong>Primary:</strong> {c.customer_messages?.primary}
              </div>
              <div>
                <strong>Escalation:</strong> {c.customer_messages?.escalation}
              </div>
            </div>
          ) : (
            <div className="json-view">
              <pre aria-label="canonical JSON snapshot">{JSON.stringify(published, null, 2)}</pre>
            </div>
          )}
        </>
      )}
    </section>
  );
}

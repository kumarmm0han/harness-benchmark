import { useEffect, useState } from 'react';
import { api, ApiError } from '../api';
import type { Identity, SopDetail, VersionSnapshot } from '../types';

interface Props {
  identity: Identity;
  sopId: string;
  /** When set, the immutable historical snapshot is shown (author only). */
  version?: number;
  onBack: () => void;
  /** Present for authors: enables loading a specific historical version (FR-043). */
  onVersion?: (version: number) => void;
}

type State =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | {
      kind: 'ready';
      identityLine: string;
      snapshot: { sopId: string; version: number; content: Record<string, unknown> };
    };

/**
 * Published-SOP detail. One fetch supplies the identity, version, and the
 * canonical snapshot; both the human view and the JSON view render from that
 * same fetched object, so a concurrent publish cannot mix versions (FR-052,
 * FR-053). All authored strings are rendered as text — React escapes them and
 * no raw HTML is injected (FR-052, NFR-020).
 */
export default function SopDetail({ identity, sopId, version, onBack, onVersion }: Props) {
  const [state, setState] = useState<State>({ kind: 'loading' });
  const [tab, setTab] = useState<'human' | 'json'>('human');
  const [historyInput, setHistoryInput] = useState('');

  useEffect(() => {
    let cancelled = false;
    setState({ kind: 'loading' });
    const load =
      version !== undefined
        ? api.getVersion(identity, sopId, version).then((s: VersionSnapshot) => s)
        : api.getSop(identity, sopId).then((s: SopDetail) => s);
    load
      .then((snap) => {
        if (cancelled) return;
        const s = snap as { sop_id: string; version: number; content: Record<string, unknown> };
        const identityLine =
          version !== undefined
            ? `Immutable historical version ${s.version} of ${s.sop_id}`
            : `Current version ${s.version} of ${s.sop_id}`;
        setState({
          kind: 'ready',
          identityLine,
          snapshot: { sopId: s.sop_id, version: s.version, content: s.content }
        });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const message =
          e instanceof ApiError
            ? e.status === 404
              ? 'This SOP is not published yet. Nothing to show here — draft data is never substituted.'
              : `${e.code}: ${e.message}`
            : 'Failed to load the SOP.';
        setState({ kind: 'error', message });
      });
    return () => {
      cancelled = true;
    };
  }, [identity, sopId, version]);

  if (state.kind === 'loading') {
    return (
      <section>
        <button type="button" onClick={onBack}>
          Back
        </button>
        <p role="status">
          Loading {sopId}…
        </p>
      </section>
    );
  }
  if (state.kind === 'error') {
    return (
      <section>
        <button type="button" onClick={onBack}>
          Back
        </button>
        <h2>{sopId}</h2>
        <p role="alert" className="bad">
          {state.message}
        </p>
      </section>
    );
  }

  const { snapshot, identityLine } = state;
  const c = snapshot.content;

  return (
    <section aria-labelledby="sop-detail-heading">
      <button type="button" onClick={onBack}>
        Back to list
      </button>
      <h2 id="sop-detail-heading">
        {String(c.title ?? sopId)} <span className="version-badge">({sopId})</span>
      </h2>
      <p className="identity">{identityLine}</p>
      <p className="policy-note">
        This is a standard operating procedure: it describes policy an agent should follow. It is not
        the result of any action executed by this demo, and nothing here performs refunds, escalations,
        or other business actions.
      </p>

      {onVersion && (
        <form
          className="version-picker"
          onSubmit={(e) => {
            e.preventDefault();
            const n = Number(historyInput);
            if (Number.isInteger(n) && n > 0) onVersion(n);
          }}
        >
          <label>
            View a specific published version (author)
            <input
              value={historyInput}
              onChange={(e) => setHistoryInput(e.target.value)}
              inputMode="numeric"
              aria-label="Published version number"
            />
          </label>
          <button type="submit">Load version</button>
        </form>
      )}

      <div className="tabs" role="tablist" aria-label="View mode">
        <button type="button" role="tab" aria-selected={tab === 'human'} onClick={() => setTab('human')}>
          Human view
        </button>
        <button type="button" role="tab" aria-selected={tab === 'json'} onClick={() => setTab('json')}>
          JSON view
        </button>
      </div>

      {tab === 'human' ? (
        <HumanView content={c} />
      ) : (
        <pre className="json-view" aria-label="Canonical JSON snapshot">
          {JSON.stringify(snapshot, null, 2)}
        </pre>
      )}
    </section>
  );
}

function text(value: unknown): string {
  return typeof value === 'string' ? value : String(value ?? '');
}

function list(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((v) => (typeof v === 'string' ? v : JSON.stringify(v)));
}

function HumanView({ content }: { content: Record<string, unknown> }) {
  const policy = content.policy as { use_when?: unknown; do_not_use_when?: unknown } | undefined;
  const inputs = (content.inputs as Array<Record<string, unknown>> | undefined) ?? [];
  const rules = (content.rules as Array<Record<string, unknown>> | undefined) ?? [];
  const actions = (content.actions as Array<Record<string, unknown>> | undefined) ?? [];
  const boundaries = (content.boundaries as Record<string, unknown> | undefined) ?? {};
  const messages = (content.customer_messages as Record<string, unknown> | undefined) ?? {};
  const escalation = boundaries.escalation as Array<Record<string, unknown>> | undefined;

  return (
    <div className="human-view">
      <section className="sop-identity" aria-label="SOP identity">
        <table>
          <tbody>
            <tr>
              <th scope="row">SOP ID</th>
              <td>
                <code>{text(content.sop_id)}</code>
              </td>
            </tr>
            <tr>
              <th scope="row">Title</th>
              <td>{text(content.title)}</td>
            </tr>
            <tr>
              <th scope="row">Owner team</th>
              <td>{text(content.owner_team)}</td>
            </tr>
            <tr>
              <th scope="row">Domain</th>
              <td>{text(content.domain)}</td>
            </tr>
            <tr>
              <th scope="row">Intent</th>
              <td>{text(content.intent)}</td>
            </tr>
            <tr>
              <th scope="row">Risk level</th>
              <td>{text(content.risk_level)}</td>
            </tr>
            <tr>
              <th scope="row">Max autonomy</th>
              <td>{text(content.max_autonomy)}</td>
            </tr>
          </tbody>
        </table>
      </section>

      <section aria-label="Policy">
        <h3>Policy</h3>
        <h4>When to use</h4>
        <ul>
          {list(policy?.use_when).map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
        <h4>Do not use when</h4>
        <ul>
          {list(policy?.do_not_use_when).map((item, i) => (
            <li key={i}>{item}</li>
          ))}
        </ul>
      </section>

      <section aria-label="Inputs">
        <h3>Inputs required</h3>
        <table>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Type</th>
            </tr>
          </thead>
          <tbody>
            {inputs.map((input, i) => (
              <tr key={i}>
                <td>
                  <code>{text(input.name)}</code>
                </td>
                <td>{text(input.type)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section aria-label="Eligibility rules">
        <h3>Eligibility rules</h3>
        <ol>
          {rules.map((rule, i) => (
            <li key={i}>
              <strong>{text(rule.id)}</strong>
              {' — '}
              {((rule.conditions as Array<Record<string, unknown>> | undefined) ?? [])
                .map((cond) => `${text(cond.input)} ${text(cond.op)} ${JSON.stringify(cond.value)}`)
                .join('; ')}
              {' → '}
              {`actions: ${list(rule.action_ids).join(', ')}`}
            </li>
          ))}
        </ol>
      </section>

      <section aria-label="Actions">
        <h3>Actions (declared policy — never executed here)</h3>
        <ul>
          {actions.map((action, i) => (
            <li key={i}>
              <code>{text(action.id)}</code> — {text(action.kind)}: {text(action.description)}
              {action.max_amount !== undefined && (
                <span> (limit: {JSON.stringify(action.max_amount)})</span>
              )}
            </li>
          ))}
        </ul>
      </section>

      <section aria-label="Boundaries">
        <h3>Boundaries</h3>
        {escalation && escalation.length > 0 ? (
          <ul>
            {escalation.map((b, i) => (
              <li key={i}>
                escalation: if {text(b.input)} {text(b.op)} {JSON.stringify(b.amount)} for action{' '}
                <code>{text(b.action_id)}</code> → escalate to <code>{text(b.target_action_id)}</code>
              </li>
            ))}
          </ul>
        ) : (
          <p>No boundary entries.</p>
        )}
      </section>

      <section aria-label="Customer messages">
        <h3>Customer messages (template wording — not sent by this app)</h3>
        {Object.entries(messages).map(([kind, value]) => (
          <blockquote key={kind}>
            <cite>{kind}</cite>
            <p>{text(value)}</p>
          </blockquote>
        ))}
      </section>
    </div>
  );
}

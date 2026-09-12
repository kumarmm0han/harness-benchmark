import {useState} from 'react';
import type {Content, Snapshot} from './api';

export function HumanContent({content: c}: {content: Content}) {
  return <div className="human-content">
    <h3>{c.title}</h3>
    <dl className="metadata"><dt>SOP ID</dt><dd>{c.sop_id}</dd><dt>Owner team</dt><dd>{c.owner_team}</dd><dt>Domain</dt><dd>{c.domain}</dd><dt>Intent</dt><dd>{c.intent}</dd><dt>Risk</dt><dd>{c.risk_level}</dd><dt>Maximum autonomy</dt><dd>{c.max_autonomy}</dd></dl>
    <h4>Intent (When to use)</h4><ul>{c.policy.use_when.map((s, i) => <li key={i}>{s}</li>)}</ul>
    <h4>Do Not Use When</h4><ul>{c.policy.do_not_use_when.map((s, i) => <li key={i}>{s}</li>)}</ul>
    <h4>Inputs Required</h4><ul>{c.inputs.map(i => <li key={i.name}>{i.name}: {i.type}</li>)}</ul>
    <h4>Eligibility Rules</h4><p>Declared conditions are joined with AND. The app does not evaluate them.</p>
    {c.rules.map(r => <section key={r.id}><h5>Rule {r.id}</h5><ul>{r.conditions.map((v, i) => <li key={i}>{v.input} {v.op} {String(v.value)}</li>)}</ul><p>Described actions: {r.action_ids.join(', ')}</p></section>)}
    <h4>Actions — policy descriptions</h4><p>No action is executed by this app.</p>
    {c.actions.map(a => <section key={a.id}><h5>{a.id}: {a.kind}</h5><p>{a.description}</p>{a.max_amount !== undefined && <p>Maximum amount: {a.max_amount}</p>}</section>)}
    <h4>Boundaries</h4>{c.boundaries.escalation.length === 0 ? <p>No escalation boundaries declared.</p> : <ul>{c.boundaries.escalation.map((b, i) => <li key={i}>Action {b.action_id}: when {b.input} {b.op} {b.amount}, refer to {b.target_action_id}.</li>)}</ul>}
    <h4>Customer Messages — policy examples</h4><p>These examples do not confirm a real action.</p><dl><dt>Primary</dt><dd>{c.customer_messages.primary}</dd><dt>Escalation</dt><dd>{c.customer_messages.escalation}</dd></dl>
  </div>;
}
export function Detail({snapshot}: {snapshot: Snapshot}) {
  const [view, setView] = useState<'human' | 'json'>('human');
  return <section className="panel" aria-label="Published detail">
    <h2>{snapshot.sop_id} · Version {snapshot.version}</h2><p>Published {snapshot.published_at}</p>
    <div className="actions"><button aria-pressed={view === 'human'} onClick={() => setView('human')}>Human view</button><button aria-pressed={view === 'json'} onClick={() => setView('json')}>AI JSON view</button></div>
    <p>Both views use this same published snapshot.</p>
    {view === 'human' ? <HumanContent content={snapshot.content}/> : <pre aria-label="Canonical JSON">{JSON.stringify(snapshot, null, 2)}</pre>}
  </section>;
}

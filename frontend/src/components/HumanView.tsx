import type { Envelope } from '../api/types';

const DESCRIBES_LABEL = 'Describes the SOP — this app does not execute this action or send this message.';

interface HumanViewProps {
  envelope: Envelope;
}

function asText(v: unknown): string {
  return v == null ? '' : String(v);
}

function asArr(v: unknown): unknown[] {
  return Array.isArray(v) ? (v as unknown[]) : [];
}

export function HumanView({ envelope }: HumanViewProps) {
  const c = envelope.content;
  const policy = { use_when: c.policy.use_when, do_not_use_when: c.policy.do_not_use_when };
  const msgs = c.customer_messages;
  const inputs = c.inputs;
  const rules = c.rules;
  const actions = c.actions;
  const escalation = c.boundaries.escalation;

  return (
    <div className="panel" data-testid="human-view">
      <header>
        <h2>
          SOP <code>{envelope.sop_id}</code> · version {envelope.version} · Owner{' '}
          <span className="chip">{asText(c.owner_team)}</span>
        </h2>
        <div>
          <span className="chip">{asText(c.domain)}</span>
          <span className="chip">risk: {asText(c.risk_level)}</span>
          <span className="chip">{asText(c.intent)}</span>
          <span className="chip">max_autonomy: {asText(c.max_autonomy)}</span>
          <span className="chip muted">Published {envelope.published_at}</span>
        </div>
      </header>

      <h3>{asText(c.title)}</h3>

      <section>
        <h4>Policy — when to use</h4>
        <ul>
          {asArr(policy.use_when).map((l, i) => (
            <li key={i}>{asText(l)}</li>
          ))}
        </ul>
      </section>
      <section>
        <h4>Policy — do not use when</h4>
        <ul>
          {asArr(policy.do_not_use_when).map((l, i) => (
            <li key={i}>{asText(l)}</li>
          ))}
        </ul>
      </section>

      <section>
        <h4>Inputs</h4>
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            {inputs.map((r, i) => (
              <tr key={i}>
                <td>{asText(r.name)}</td>
                <td>{asText(r.type)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h4>Eligibility rules</h4>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Conditions</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r, i) => (
              <tr key={i}>
                <td>{asText(r.id)}</td>
                <td>
                  {r.conditions.map((cond) => `${asText(cond.input)} ${asText(cond.op)} ${JSON.stringify(cond.value)}`).join(' AND ')}
                </td>
                <td>{(r.action_ids ?? []).map((a) => asText(a)).join(', ')}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h4>Actions</h4>
        {' '}
        <p className="msg-label">{DESCRIBES_LABEL}</p>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Kind</th>
              <th>Described behavior</th>
              <th>Limit</th>
            </tr>
          </thead>
          <tbody>
            {actions.map((a, i) => (
              <tr key={i}>
                <td>{asText(a.id)}</td>
                <td>{asText(a.kind)}</td>
                <td>{asText(a.description)}</td>
                <td>{a.max_amount != null ? asText(a.max_amount) : '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h4>Boundaries — escalation</h4>
        <table>
          <thead>
            <tr>
              <th>Action</th>
              <th>Input</th>
              <th>Op</th>
              <th>Amount</th>
              <th>Targets</th>
            </tr>
          </thead>
          <tbody>
            {escalation.map((e, i) => (
              <tr key={i}>
                <td>{asText(e.action_id)}</td>
                <td>{asText(e.input)}</td>
                <td>{asText(e.op)}</td>
                <td>{JSON.stringify(e.amount)}</td>
                <td>{asText(e.target_action_id)}</td>
              </tr>
            ))}
            {escalation.length === 0 ? (
              <tr>
                <td colSpan={5}>None declared.</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </section>

      <section>
        <h4>Customer messages</h4>
        <p className="msg-label">{DESCRIBES_LABEL}</p>
        <blockquote>
          <strong>Primary:</strong> {asText(msgs.primary)}
        </blockquote>
        <blockquote>
          <strong>Escalation:</strong> {asText(msgs.escalation)}
        </blockquote>
      </section>
    </div>
  );
}

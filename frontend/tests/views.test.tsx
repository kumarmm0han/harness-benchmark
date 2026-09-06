import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

import { HumanView } from '../src/components/HumanView';
import { JsonView } from '../src/components/JsonView';

const ENVELOPE = {
  sop_id: 'BILL-REFUND-001',
  version: 2,
  published_at: '2026-01-01T12:00:00Z',
  content: {
    sop_id: 'BILL-REFUND-001',
    title: 'Refund for Duplicate Charge',
    owner_team: 'Billing Operations',
    domain: 'Billing',
    intent: 'refund_duplicate_charge',
    risk_level: 'medium',
    max_autonomy: 'assist',
    policy: {
      use_when: ['Customer reports a duplicate charge.'],
      do_not_use_when: ['Fraud is suspected.'],
    },
    inputs: [
      { name: 'refund_amount', type: 'number' },
      { name: 'duplicate_confirmed', type: 'boolean' },
    ],
    rules: [
      {
        id: 'R1',
        conditions: [{ input: 'duplicate_confirmed', op: 'eq', value: true }],
        action_ids: ['A1'],
      },
    ],
    actions: [
      {
        id: 'A1',
        kind: 'refund',
        description: 'Processes a confirmed refund within the limit.',
        max_amount: 200,
      },
      { id: 'A2', kind: 'escalate', description: 'Escalates over-limit requests.' },
    ],
    boundaries: {
      escalation: [
        { action_id: 'A1', input: 'refund_amount', op: 'gt', amount: 200, target_action_id: 'A2' },
      ],
    },
    customer_messages: { primary: 'We reviewed the charge.', escalation: 'Please contact support.' },
  },
};

describe('HumanView (FR-052, TASK-018)', () => {
  it('shows identity + version + owner + domain + risk', () => {
    render(<HumanView envelope={ENVELOPE as never} />);
    expect(screen.getByTestId('human-view')).toBeInTheDocument();
    expect(screen.getByText('BILL-REFUND-001')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(/version 2/);
    expect(screen.getByText('Billing Operations')).toBeInTheDocument();
    expect(screen.getByText('Billing')).toBeInTheDocument();
    expect(screen.getByText(/risk: medium/)).toBeInTheDocument();
  });

  it('renders policy sections, inputs, rules, actions, boundaries, customer messages', () => {
    render(<HumanView envelope={ENVELOPE as never} />);
    expect(screen.getByText('Customer reports a duplicate charge.')).toBeInTheDocument();
    expect(screen.getByText('Fraud is suspected.')).toBeInTheDocument();
    expect(screen.getAllByText('refund_amount').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('R1').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('A1').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('A2').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('We reviewed the charge.')).toBeInTheDocument();
    expect(screen.getByText('Please contact support.')).toBeInTheDocument();
  });

  it('labels action and message blocks with the "describes the SOP" disclaimer (FR-052)', () => {
    const { container } = render(<HumanView envelope={ENVELOPE as never} />);
    const labels = Array.from(container.querySelectorAll('.msg-label'));
    expect(labels.length).toBeGreaterThanOrEqual(2);
    expect(labels[0]?.textContent).toMatch(/does not execute/i);
    expect(labels[1]?.textContent).toMatch(/does not execute/i);
  });

  it('XSS: a hostile HTML string in an action description renders as literal text, no <img> created, script not executed', () => {
    const hostile = {
      ...ENVELOPE,
      content: {
        ...ENVELOPE.content,
        actions: [
          {
            id: 'A1',
            kind: 'refund',
            description: '<img src=x onerror=window.__pwned=true> <script>window.__pwned=1</script> refund',
            max_amount: 200,
          },
        ],
      },
    };
    const { container } = render(<HumanView envelope={hostile as never} />);
    // No <img> element must be present (React escapes text children).
    expect(container.querySelector('img')).toBeNull();
    // The literal string is visible as text.
    expect(container.textContent).toContain('<img src=x onerror=window.__pwned=true>');
    // The script tag did not execute.
    const w = globalThis as unknown as { __pwned?: boolean };
    expect(w.__pwned).toBeUndefined();
  });
});

describe('JsonView (FR-053, TASK-018)', () => {
  it('renders the full envelope (sop_id, version, published_at, content) as JSON text', () => {
    const { container } = render(<JsonView envelope={ENVELOPE as never} />);
    const pre = container.querySelector('pre.json');
    expect(pre).not.toBeNull();
    const text = pre!.textContent ?? '';
    const parsed = JSON.parse(text);
    expect(parsed).toEqual(
      expect.objectContaining({
        sop_id: 'BILL-REFUND-001',
        version: 2,
        published_at: '2026-01-01T12:00:00Z',
      }),
    );
    expect(parsed.content).toBeTruthy();
  });

  it('human and JSON views agree on sop_id+version for the SAME snapshot object', () => {
    // Simulate App's shared snapshot state by rendering Human + JSON from the
    // same object reference.
    const shared: typeof ENVELOPE = ENVELOPE;
    const { container } = render(
      <div>
        <HumanView envelope={shared as never} />
        <JsonView envelope={shared as never} />
      </div>,
    );
    // Human view shows the sop_id as a heading child.
    expect(container.textContent).toContain('BILL-REFUND-001');
    // JSON view carries the same identifier + version in the body.
    const pre = container.querySelector('pre.json');
    expect(pre).not.toBeNull();
    const text = pre!.textContent ?? '';
    expect(text).toContain('"sop_id": "BILL-REFUND-001"');
    expect(text).toContain('"version": 2');
  });
});

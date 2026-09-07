import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

/**
 * TASK-011 coverage: identity/roles (FR-001), list+filters+empty state
 * (FR-050), human/JSON views from one snapshot (FR-052/FR-053), XSS-safe
 * rendering (NFR-020), editor save/validate/publish flows (FR-010, FR-034,
 * AC-E2E-001/002/004), failure handling without losing editor content.
 */

interface Route {
  method: string;
  url: string;
  status?: number;
  body: unknown;
}

const calls: Array<{ url: string; method: string; headers: Record<string, string>; body?: string }> = [];

function setupRoutes(routes: Route[]) {
  calls.length = 0;
  const handler = (input: unknown, init?: { method?: string; headers?: unknown; body?: unknown }) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    const headers = (init?.headers ?? {}) as Record<string, string>;
    calls.push({ url, method, headers, body: typeof init?.body === 'string' ? init.body : undefined });
    const hit = routes.find((r) => r.url === url && r.method === method);
    const status = hit ? (hit.status ?? 200) : 500;
    const body = hit?.body ?? { code: 'no-route', message: `no route for ${method} ${url}`, issues: [] };
    return Promise.resolve({ ok: status < 400, status, text: async () => JSON.stringify(body) });
  };
  vi.stubGlobal('fetch', handler);
}

function sopList(rows: unknown[]) {
  return { sops: rows };
}

const sampleContent = {
  sop_id: 'BILL-001',
  title: 'Duplicate Charge Refund',
  owner_team: 'Billing Operations',
  domain: 'Billing',
  intent: 'refund_duplicate_charge',
  risk_level: 'medium',
  max_autonomy: 'assist',
  policy: {
    use_when: ['Customer reports a duplicate charge.'],
    do_not_use_when: ['Fraud is suspected.']
  },
  inputs: [
    { name: 'refund_amount', type: 'number' },
    { name: 'duplicate_confirmed', type: 'boolean' }
  ],
  rules: [
    {
      id: 'R1',
      conditions: [
        { input: 'duplicate_confirmed', op: 'eq', value: true },
        { input: 'refund_amount', op: 'lte', value: 200 }
      ],
      action_ids: ['A1']
    }
  ],
  actions: [
    {
      id: 'A1',
      kind: 'refund',
      description: 'Refund <script>alert(1)</script> within the declared limit.',
      max_amount: 200
    },
    { id: 'A2', kind: 'escalate', description: 'Escalate over-limit requests.' }
  ],
  boundaries: {
    escalation: [
      { action_id: 'A1', input: 'refund_amount', op: 'gt', amount: 200, target_action_id: 'A2' }
    ]
  },
  customer_messages: {
    primary: 'A representative can review the confirmed duplicate charge for a refund.',
    escalation: 'This request needs additional review because it exceeds the refund limit.'
  }
};

function detailRoutes() {
  return [
    { method: 'GET', url: '/api/v1/sops', body: sopList([
        { sop_id: 'BILL-001', title: 'Duplicate Charge Refund', version: 2, domain: 'Billing', risk_level: 'medium' }
      ]) },
    { method: 'GET', url: '/api/v1/sops/BILL-001', body: { sop_id: 'BILL-001', version: 2, content: sampleContent } }
  ];
}

beforeEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('identity and navigation (FR-001)', () => {
  it('shows a demo-labeled identity selector and role-aware navigation', async () => {
    const user = userEvent.setup();
    setupRoutes([{ method: 'GET', url: '/api/v1/sops', body: sopList([]) }]);
    render(<App />);

    expect(screen.getByText(/local only/i)).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /demo-author/ })).toBeChecked();
    expect(screen.getByText(/Acting as/)).toBeInTheDocument();

    // Author sees draft navigation; consumer does not.
    expect(screen.getByRole('button', { name: 'My drafts' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New draft' })).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: /demo-consumer/ }));
    expect(screen.getByRole('radio', { name: /demo-consumer/ })).toBeChecked();
    expect(screen.queryByRole('button', { name: 'My drafts' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'New draft' })).not.toBeInTheDocument();
    expect(screen.getByText(/Acting as/)).toBeInTheDocument();
  });

  it('sends the selected identity as X-Demo-User on API requests (FR-001)', async () => {
    setupRoutes([{ method: 'GET', url: '/api/v1/sops', body: sopList([]) }]);
    render(<App />);
    await screen.findByText(/No published SOPs match/i);
    expect(calls[0].headers['X-Demo-User']).toBe('demo-author');
  });
});

describe('published list, filters, empty state (FR-050)', () => {
  it('renders summaries and re-queries with AND-combined filters', async () => {
    const user = userEvent.setup();
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([
          { sop_id: 'BILL-001', title: 'Duplicate Charge Refund', version: 1, domain: 'Billing', risk_level: 'medium' },
          { sop_id: 'SUP-001', title: 'Support FAQ', version: 3, domain: 'Support', risk_level: 'low' }
      ]) },
      { method: 'GET', url: '/api/v1/sops?domain=Billing', body: sopList([
          { sop_id: 'BILL-001', title: 'Duplicate Charge Refund', version: 1, domain: 'Billing', risk_level: 'medium' }
      ]) }
    ]);
    render(<App />);
    await screen.findByText('BILL-001');
    expect(screen.getByText('SUP-001')).toBeInTheDocument();
    expect(screen.getByText('Duplicate Charge Refund')).toBeInTheDocument();

    const selects = screen.getAllByRole('combobox');
    await user.selectOptions(selects[0], 'Billing');
    await waitFor(() => expect(screen.queryByText('SUP-001')).not.toBeInTheDocument());
    expect(screen.getByText('BILL-001')).toBeInTheDocument();
    expect(calls.some((c) => c.url === '/api/v1/sops?domain=Billing')).toBe(true);
  });

  it('shows a useful empty state', async () => {
    setupRoutes([{ method: 'GET', url: '/api/v1/sops', body: sopList([]) }]);
    render(<App />);
    expect(await screen.findByText(/No published SOPs match/i)).toBeInTheDocument();
  });
});

describe('detail views (FR-052, FR-053, NFR-020)', () => {
  it('human and JSON views render the same fetched snapshot with identity and version', async () => {
    const user = userEvent.setup();
    setupRoutes(detailRoutes());
    const { container } = render(<App />);
    await user.click(await screen.findByRole('button', { name: 'View' }));

    expect(await screen.findByText('Current version 2 of BILL-001')).toBeInTheDocument();
    expect(container.textContent).toContain('Duplicate Charge Refund');
    expect(container.textContent).toContain('within the declared limit.');

    await user.click(screen.getByRole('tab', { name: 'JSON view' }));
    const json = (await screen.findByText(/Refund <script>alert\(1\)<\/script> within the declared limit\./))
      .closest('pre.json-view');
    const text = json?.textContent ?? container.querySelector('pre.json-view')?.textContent ?? '';
    expect(text).toContain('"sop_id": "BILL-001"');
    expect(text).toContain('"version": 2');
    expect(text).toContain('"title": "Duplicate Charge Refund"');
    expect(text).toContain('"max_amount": 200');
  });

  it('renders authored strings as text only (no script injection)', async () => {
    const user = userEvent.setup();
    setupRoutes(detailRoutes());
    const { container } = render(<App />);
    await user.click(await screen.findByRole('button', { name: 'View' }));
    await screen.findByText('Current version 2 of BILL-001');

    expect(container.querySelectorAll('script').length).toBe(0);
    expect(container.textContent).toContain('<script>alert(1)</script>');
    expect(screen.getByText('A representative can review the confirmed duplicate charge for a refund.')).toBeInTheDocument();
    expect(screen.getByText(/Escalate over-limit requests\./)).toBeInTheDocument();
  });

  it('404 on unpublished SOP explains drafts are never substituted (FR-045)', async () => {
    const user = userEvent.setup();
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([
          { sop_id: 'HIDDEN-1', title: 'HIDDEN-1', version: 1, domain: 'Billing', risk_level: 'low' }
      ]) },
      {
        method: 'GET',
        url: '/api/v1/sops/HIDDEN-1',
        status: 404,
        body: { code: 'sop-not-found', message: 'No published content.', issues: [] }
      }
    ]);
    render(<App />);
    await user.click(await screen.findByRole('button', { name: 'View' }));
    expect(await screen.findByText(/not published yet.*never substituted/i)).toBeInTheDocument();
  });

  it('version history picker is author-only (FR-043)', async () => {
    const user = userEvent.setup();
    setupRoutes([
      ...detailRoutes(),
      {
        method: 'GET',
        url: '/api/v1/sops/BILL-001/versions/1',
        body: { sop_id: 'BILL-001', version: 1, published_revision: 4, published_at: '2026-01-01T00:00:00Z', content: sampleContent }
      }
    ]);
    const { container } = render(<App />);
    await user.click(await screen.findByRole('button', { name: 'View' }));
    await screen.findByText('Current version 2 of BILL-001');
    expect(container.querySelector('.version-picker')).not.toBeNull();

    // Author loads an immutable historical snapshot.
    const versionInput = container.querySelector<HTMLInputElement>('.version-picker input');
    expect(versionInput).not.toBeNull();
    await user.clear(versionInput!);
    await user.type(versionInput!, '1');
    await user.click(screen.getByRole('button', { name: 'Load version' }));
    expect(await screen.findByText('Immutable historical version 1 of BILL-001')).toBeInTheDocument();

    // Consumer never sees the picker.
    await user.click(screen.getByRole('radio', { name: /demo-consumer/ }));
    await waitFor(() => expect(container.querySelector('.version-picker')).toBeNull());
  });
});

describe('editor journeys (FR-010, FR-034, AC-E2E-001/002/004)', () => {
  it('AC-E2E-001: template insert, save, validate, publish -> version 1', async () => {
    const user = userEvent.setup();
    const publishBody = {
      sop_id: 'BILL-REFUND-001',
      version: 1,
      published_at: '2026-01-01T00:00:00Z',
      content: sampleContent
    };
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([]) },
      { method: 'POST', url: '/api/v1/validate', body: { valid: true, issues: [], content: sampleContent } },
      {
        method: 'PUT',
        url: '/api/v1/drafts/BILL-REFUND-001',
        body: { sop_id: 'BILL-REFUND-001', revision: 1, publish_failed: false }
      },
      { method: 'POST', url: '/api/v1/sops/BILL-REFUND-001/publish', body: publishBody },
      {
        method: 'GET',
        url: '/api/v1/sops/BILL-REFUND-001',
        body: { sop_id: 'BILL-REFUND-001', version: 1, content: sampleContent }
      }
    ]);
    render(<App />);
    await screen.findByText(/No published SOPs match/i);

    await user.click(screen.getByRole('button', { name: 'New draft' }));
    await user.click(screen.getByRole('button', { name: /Insert sample template/i }));
    const textarea = (await screen.findByLabelText(/Draft source/)) as HTMLTextAreaElement;
    expect(textarea.value).toContain('Refund for Duplicate Charge');
    expect((screen.getByLabelText(/SOP ID/i) as HTMLInputElement).value).toBe('BILL-REFUND-001');
    expect(screen.getByText(/Unsaved changes/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Validate & preview/i }));
    expect((await screen.findAllByText(/Valid\./, { exact: false })).length).toBeGreaterThanOrEqual(1);

    await user.click(screen.getByRole('button', { name: 'Save draft' }));
    expect(await screen.findByText(/Draft saved \(revision 1\)/)).toBeInTheDocument();
    const put = calls.find((c) => c.method === 'PUT' && c.url === '/api/v1/drafts/BILL-REFUND-001');
    expect(put).toBeTruthy();
    expect(put?.headers['X-Demo-User']).toBe('demo-author');
    expect(JSON.parse(put?.body ?? '{}').source).toContain('sop_id: BILL-REFUND-001');

    await user.click(screen.getByRole('button', { name: 'Publish' }));
    // Publish jumps to the published view with the stored snapshot.
    expect(await screen.findByText('Current version 1 of BILL-REFUND-001')).toBeInTheDocument();
    expect((await screen.findAllByText('Duplicate Charge Refund')).length).toBeGreaterThanOrEqual(1);
  });

  it('FR-034: validation issues show stage, code, readable message, and path; no preview', async () => {
    const user = userEvent.setup();
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([]) },
      {
        method: 'POST',
        url: '/api/v1/validate',
        body: {
          valid: false,
          issues: [
            { code: 'UNKNOWN_FIELD', stage: 'structural', message: 'Unknown field in a required section.', path: 'policy.bogus' },
            { code: 'REFUND_LIMIT_MISSING', stage: 'semantic', message: 'The refund action must declare a positive numeric max_amount.', path: 'actions/0' },
            { code: 'ESCALATION_MISSING', stage: 'semantic', message: 'Declare an escalation boundary targeting an existing escalate action.', path: 'boundaries.escalation' }
          ]
        }
      }
    ]);
    render(<App />);
    await screen.findByText(/No published SOPs match/i);
    await user.click(screen.getByRole('button', { name: 'New draft' }));
    await user.click(screen.getByRole('button', { name: /Insert sample template/i }));
    await user.click(await screen.findByRole('button', { name: /Validate & preview/i }));

    expect(await screen.findByText('Structural problems')).toBeInTheDocument();
    expect(screen.getByText('Semantic problems')).toBeInTheDocument();
    expect(screen.getByText('UNKNOWN_FIELD')).toBeInTheDocument();
    expect(screen.getByText('REFUND_LIMIT_MISSING')).toBeInTheDocument();
    expect(screen.getByText('ESCALATION_MISSING')).toBeInTheDocument();
    expect(screen.getByText('boundaries.escalation')).toBeInTheDocument();
    expect(
      screen.getByText('The refund action must declare a positive numeric max_amount.')
    ).toBeInTheDocument();
    // content is null when invalid -> no canonical preview
    expect(screen.queryByText(/Canonical content preview/i)).not.toBeInTheDocument();
  });

  it('FR-010: a failed save keeps the editor content and reports the error', async () => {
    const user = userEvent.setup();
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([]) },
      {
        method: 'PUT',
        url: '/api/v1/drafts/BILL-REFUND-001',
        status: 413,
        body: { code: 'source-too-large', message: 'Source is 70 KiB; the limit is 64 KiB.', issues: [] }
      }
    ]);
    render(<App />);
    await screen.findByText(/No published SOPs match/i);
    await user.click(screen.getByRole('button', { name: 'New draft' }));
    await user.click(screen.getByRole('button', { name: /Insert sample template/i }));
    const textarea = (await screen.findByLabelText(/Draft source/)) as HTMLTextAreaElement;
    const before = textarea.value;

    await user.click(screen.getByRole('button', { name: 'Save draft' }));
    expect(await screen.findByText(/source-too-large: Source is 70 KiB/i)).toBeInTheDocument();
    expect(((await screen.findByLabelText(/Draft source/)) as HTMLTextAreaElement).value).toBe(before);
  });

  it('AC-E2E-002: rejected publish surfaces separate safety issues, publication not performed', async () => {
    const user = userEvent.setup();
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([]) },
      {
        method: 'PUT',
        url: '/api/v1/drafts/BILL-REFUND-001',
        body: { sop_id: 'BILL-REFUND-001', revision: 2, publish_failed: true }
      },
      {
        method: 'POST',
        url: '/api/v1/sops/BILL-REFUND-001/publish',
        status: 422,
        body: {
          code: 'publication-rejected',
          message: 'The draft is not valid; the previously published version remains current.',
          issues: [
            { code: 'REFUND_LIMIT_MISSING', stage: 'semantic', message: 'The refund action must declare a positive numeric max_amount.', path: 'actions/0' },
            { code: 'ESCALATION_MISSING', stage: 'semantic', message: 'Declare an escalation boundary targeting an existing escalate action.', path: 'boundaries.escalation' }
          ]
        }
      }
    ]);
    render(<App />);
    await screen.findByText(/No published SOPs match/i);
    await user.click(screen.getByRole('button', { name: 'New draft' }));
    await user.click(screen.getByRole('button', { name: /Insert sample template/i }));
    await user.click(screen.getByRole('button', { name: 'Publish' }));

    expect(await screen.findByText(/Publication was rejected/)).toBeInTheDocument();
    expect(screen.getByText('REFUND_LIMIT_MISSING')).toBeInTheDocument();
    expect(screen.getByText('ESCALATION_MISSING')).toBeInTheDocument();
  });

  it('AC-E2E-004 (conflict path): stale revision publish reports 409 conflict', async () => {
    const user = userEvent.setup();
    setupRoutes([
      { method: 'GET', url: '/api/v1/sops', body: sopList([]) },
      {
        method: 'PUT',
        url: '/api/v1/drafts/BILL-REFUND-001',
        body: { sop_id: 'BILL-REFUND-001', revision: 3, publish_failed: false }
      },
      {
        method: 'POST',
        url: '/api/v1/sops/BILL-REFUND-001/publish',
        status: 409,
        body: {
          code: 'stale-revision',
          message: 'The saved draft is at revision 4; revision 3 cannot be published anymore.',
          issues: []
        }
      }
    ]);
    render(<App />);
    await screen.findByText(/No published SOPs match/i);
    await user.click(screen.getByRole('button', { name: 'New draft' }));
    await user.click(screen.getByRole('button', { name: /Insert sample template/i }));
    await user.click(screen.getByRole('button', { name: 'Publish' }));

    expect(await screen.findByText(/Conflict: The saved draft is at revision 4/i)).toBeInTheDocument();
  });
});

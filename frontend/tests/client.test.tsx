import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { IdentityProvider, useIdentity } from '../src/state/IdentityContext';
import { api, ApiError, client } from '../src/api/client';
import type { Identity } from '../src/api/types';

const realFetch = globalThis.fetch;

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api client (TASK-015)', () => {
  beforeEach(() => {
    globalThis.fetch = realFetch; // restore
  });
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it('sends the X-Demo-User header and Content-Type for POSTs', async () => {
    const seen: { url: string; init: RequestInit }[] = [];
    globalThis.fetch = (async (url: string, init: RequestInit) => {
      seen.push({ url, init });
      return jsonResponse(200, { OK: true });
    }) as typeof fetch;

    await api('/validate', { identity: 'demo-author', method: 'POST', body: { source: 'x' } });

    expect(seen[0].url).toBe('/api/v1/validate');
    const h = (seen[0].init.headers ?? {}) as Record<string, string>;
    expect(h['X-Demo-User']).toBe('demo-author');
    expect(h['Content-Type']).toBe('application/json');
    expect(JSON.parse(seen[0].init.body as string)).toEqual({ source: 'x' });
  });

  it('adds provided query params, omitting undefined', async () => {
    const seen: string[] = [];
    globalThis.fetch = (async (url: string) => {
      seen.push(url as string);
      return jsonResponse(200, []);
    }) as typeof fetch;

    await client.listSops('demo-author', { domain: 'Billing', risk: '' });
    expect(seen[0]).toBe('/api/v1/sops?domain=Billing');
    await client.listSops('demo-author');
    expect(seen[1]).toBe('/api/v1/sops');
  });

  it('throws a typed ApiError with code/message/issues on 4xx', async () => {
    globalThis.fetch = (async () =>
      jsonResponse(403, {
        code: 'FORBIDDEN',
        message: 'this operation requires the author identity',
        issues: [],
      })) as typeof fetch;

    let caught: unknown;
    try {
      await client.listDrafts('demo-consumer');
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(ApiError);
    const e = caught as ApiError;
    expect(e.status).toBe(403);
    expect(e.code).toBe('FORBIDDEN');
    expect(e.message).toBe('this operation requires the author identity');
  });

  it('surfaces a 401 missing-identity body as a typed error', async () => {
    globalThis.fetch = (async () =>
      jsonResponse(401, {
        code: 'MISSING_IDENTITY',
        message: 'missing identity',
        issues: [],
      })) as typeof fetch;
    const e = await client.listDrafts('nobody' as unknown as Identity).catch((x) => x);
    expect(e).toBeInstanceOf(ApiError);
    expect((e as ApiError).code).toBe('MISSING_IDENTITY');
  });

  it('422 carries the issue list', async () => {
    globalThis.fetch = (async () =>
      jsonResponse(422, {
        code: 'VALIDATION_FAILED',
        message: 'saved draft fails validation',
        issues: [
          { code: 'FIN_REFUND_MAX_AMOUNT', stage: 'semantic', message: 'missing', path: 'actions[0].max_amount' },
        ],
      })) as typeof fetch;
    const e = await client.publish('S', 1, 'demo-author').catch((x) => x);
    expect((e as ApiError).issues).toHaveLength(1);
    expect((e as ApiError).issues[0]).toMatchObject({ code: 'FIN_REFUND_MAX_AMOUNT' });
  });
});

function IdentityProbe({ onIdentity }: { onIdentity: (id: Identity) => void }) {
  const { identity, setIdentity } = useIdentity();
  return (
    <div>
      <span data-testid="current">{identity}</span>
      <button
        data-testid="flip"
        onClick={() => {
          const next: Identity = identity === 'demo-author' ? 'demo-consumer' : 'demo-author';
          onIdentity(next);
          setIdentity(next);
        }}
      >
        flip
      </button>
    </div>
  );
}

describe('identity context (TASK-015)', () => {
  it('persists the selected identity to the provider value', async () => {
    const got: Identity[] = [];
    render(
      <IdentityProvider>
        <IdentityProbe onIdentity={(id) => got.push(id)} />
      </IdentityProvider>,
    );
    expect(screen.getByTestId('current').textContent).toBe('demo-author');
    await userEvent.click(screen.getByTestId('flip'));
    await waitFor(() => expect(screen.getByTestId('current').textContent).toBe('demo-consumer'));
    expect(got).toEqual(['demo-consumer']);
  });
});

import type { DraftDto, DraftSummary, Envelope, Identity, Issue, SopSummary, ValidateResponse } from './types';

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly issues: Issue[] = [],
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface ApiOptions {
  identity: Identity;
  method?: string;
  body?: unknown;
  query?: Record<string, string | undefined>;
}

function withQuery(path: string, query?: Record<string, string | undefined>): string {
  const entries = Object.entries(query ?? {}).filter(([, v]) => v !== undefined && v !== '');
  if (entries.length === 0) return path;
  const qs = new URLSearchParams(entries.map(([k, v]) => [k, v as string])).toString();
  return `${path}?${qs}`;
}

export async function api<T>(path: string, opts: ApiOptions): Promise<T> {
  const url = withQuery(`/api/v1/${path.replace(/^\//, '')}`, opts.query);
  const init: RequestInit = {
    method: opts.method ?? 'GET',
    headers: {
      Accept: 'application/json',
      'X-Demo-User': opts.identity,
    },
  };
  if (opts.body !== undefined) {
    (init.headers as Record<string, string>)['Content-Type'] = 'application/json';
    init.body = JSON.stringify(opts.body);
  }
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    throw new ApiError(0, 'NETWORK', `network error: ${(e as Error).message}`);
  }
  const text = await res.text();
  let json: unknown = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
  }
  if (!res.ok) {
    const err = (json ?? {}) as { code?: string; message?: string; issues?: Issue[] };
    throw new ApiError(
      res.status,
      err.code ?? 'UNKNOWN',
      err.message ?? `HTTP ${res.status}`,
      Array.isArray(err.issues) ? err.issues : [],
    );
  }
  return (json ?? ({} as T)) as T;
}

export const client = {
  validate(source: string, identity: Identity) {
    return api<ValidateResponse>('/validate', {
      identity,
      method: 'POST',
      body: { source },
    });
  },
  saveDraft(sopId: string, source: string, identity: Identity) {
    return api<{ sop_id: string; revision: number }>('/drafts/' + encodeURIComponent(sopId), {
      identity,
      method: 'PUT',
      body: { source },
    });
  },
  getDraft(sopId: string, identity: Identity) {
    return api<DraftDto>(
      '/drafts/' + encodeURIComponent(sopId),
      { identity },
    );
  },
  listDrafts(identity: Identity) {
    return api<DraftSummary[]>('/drafts', { identity });
  },
  publish(sopId: string, revision: number, identity: Identity) {
    return api<Envelope>('/sops/' + encodeURIComponent(sopId) + '/publish', {
      identity,
      method: 'POST',
      body: { revision },
    });
  },
  listSops(
    identity: Identity,
    filter?: { domain?: string; risk?: string },
  ) {
    return api<SopSummary[]>('/sops', { identity, query: { domain: filter?.domain, risk: filter?.risk } });
  },
  getSop(sopId: string, identity: Identity) {
    return api<Envelope>('/sops/' + encodeURIComponent(sopId), { identity });
  },
  getSopVersion(sopId: string, version: number, identity: Identity) {
    return api<Envelope>(`/sops/${encodeURIComponent(sopId)}/versions/${version}`, { identity });
  },
};

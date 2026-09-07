import type {
  DraftDetail,
  DraftSummary,
  Identity,
  Issue,
  PublishResult,
  SopDetail,
  SopListResult,
  ValidateResult,
  VersionSnapshot
} from './types';

/** Error surfaced by the API: {code, message, issues} (IR-001). */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly issues: Issue[] = []
  ) {
    super(message);
  }
}

interface Init {
  method?: string;
  body?: unknown;
}

async function request<T>(path: string, identity: Identity, init: Init = {}): Promise<T> {
  const res = await fetch(path, {
    method: init.method ?? 'GET',
    headers: {
      'X-Demo-User': identity, // FR-001: identity travels with every API request
      ...(init.body !== undefined ? { 'Content-Type': 'application/json' } : {})
    },
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined
  });
  let data: unknown = null;
  const text = await res.text();
  if (text.trim()) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }
  if (!res.ok) {
    const d = data as { code?: string; message?: string; issues?: Issue[] } | null;
    throw new ApiError(
      res.status,
      d?.code ?? 'error',
      d?.message ?? `Request failed (HTTP ${res.status})`,
      Array.isArray(d?.issues) ? d?.issues : []
    );
  }
  return (data ?? null) as T;
}

export const api = {
  validate: (identity: Identity, source: string) =>
    request<ValidateResult>('/api/v1/validate', identity, { method: 'POST', body: { source } }),

  putDraft: (identity: Identity, sopId: string, source: string) =>
    request<DraftSummary>(`/api/v1/drafts/${encodeURIComponent(sopId)}`, identity, {
      method: 'PUT',
      body: { source }
    }),

  getDrafts: (identity: Identity) => request<{ drafts: DraftSummary[] }>('/api/v1/drafts', identity),

  getDraft: (identity: Identity, sopId: string) =>
    request<DraftDetail>(`/api/v1/drafts/${encodeURIComponent(sopId)}`, identity),

  publish: (identity: Identity, sopId: string, revision: number) =>
    request<PublishResult>(`/api/v1/sops/${encodeURIComponent(sopId)}/publish`, identity, {
      method: 'POST',
      body: { revision }
    }),

  listSops: (identity: Identity, domain?: string, risk?: string) => {
    const params = new URLSearchParams();
    if (domain) params.set('domain', domain);
    if (risk) params.set('risk', risk);
    const qs = params.toString();
    return request<SopListResult>('/api/v1/sops' + (qs ? `?${qs}` : ''), identity);
  },

  getSop: (identity: Identity, sopId: string) =>
    request<SopDetail>(`/api/v1/sops/${encodeURIComponent(sopId)}`, identity),

  getVersion: (identity: Identity, sopId: string, version: number) =>
    request<VersionSnapshot>(
      `/api/v1/sops/${encodeURIComponent(sopId)}/versions/${version}`,
      identity
    )
};

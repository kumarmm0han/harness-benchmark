// Thin API client for /api/v1 (DES-013). One place for requests: sends the demo identity
// header on every call and normalizes error bodies to {code, message, issues} (IR-001).
import type {
  ApiError,
  DraftDetail,
  DraftSummary,
  Identity,
  Published,
  SopSummary,
  ValidateResult,
  ValidationIssue,
} from "./types";

/** A non-2xx response, normalized to the documented error envelope. */
export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;
  readonly issues: ValidationIssue[];

  constructor(status: number, code: string, message: string, issues: ValidationIssue[]) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.code = code;
    this.issues = issues;
  }
}

export interface ApiClientOptions {
  base?: string;
  identity?: Identity;
  /** Injectable fetch-like function (defaults to the global `fetch`); makes the client testable. */
  fetcher?: typeof fetch;
}

export class ApiClient {
  private base: string;
  private identity: Identity;
  private fetcher: typeof fetch;

  constructor(opts: ApiClientOptions = {}) {
    this.base = (opts.base ?? "/api/v1").replace(/\/$/, "");
    this.identity = opts.identity ?? "demo-author";
    this.fetcher = opts.fetcher ?? fetch;
  }

  setIdentity(identity: Identity): void {
    this.identity = identity;
  }

  get currentIdentity(): Identity {
    return this.identity;
  }

  private query(params: Record<string, string | undefined>): string {
    const qs = new URLSearchParams();
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== "") qs.set(k, v);
    }
    const s = qs.toString();
    return s ? `?${s}` : "";
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    let res: Response;
    try {
      res = await this.fetcher(this.base + path, {
        method,
        headers: {
          "Content-Type": "application/json",
          "X-Demo-User": this.identity,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (e) {
      throw new ApiClientError(0, "NETWORK", e instanceof Error ? e.message : "network error", []);
    }

    const text = await res.text();
    let data: unknown = null;
    try {
      data = text ? (JSON.parse(text) as unknown) : null;
    } catch {
      data = null;
    }

    if (!res.ok) {
      const raw = (data ?? {}) as Partial<ApiError>;
      throw new ApiClientError(
        res.status,
        raw.code ?? "ERROR",
        raw.message ?? `request failed (${res.status})`,
        Array.isArray(raw.issues) ? raw.issues : [],
      );
    }
    return data as T;
  }

  // ---- catalog ----
  listSops(filter: { domain?: string; risk?: string } = {}): Promise<SopSummary[]> {
    return this.request<SopSummary[]>("GET", "/sops" + this.query({ domain: filter.domain, risk: filter.risk }));
  }
  getSop(sopId: string): Promise<Published> {
    return this.request<Published>("GET", `/sops/${encodeURIComponent(sopId)}`);
  }
  getSopVersion(sopId: string, version: number): Promise<Published> {
    return this.request<Published>("GET", `/sops/${encodeURIComponent(sopId)}/versions/${version}`);
  }

  // ---- validate + publish (author) ----
  validate(source: string): Promise<ValidateResult> {
    return this.request<ValidateResult>("POST", "/validate", { source });
  }
  publish(sopId: string, revision: number): Promise<Published> {
    return this.request<Published>("POST", `/sops/${encodeURIComponent(sopId)}/publish`, { revision });
  }

  // ---- drafts (author) ----
  listDrafts(): Promise<DraftSummary[]> {
    return this.request<DraftSummary[]>("GET", "/drafts");
  }
  getDraft(sopId: string): Promise<DraftDetail> {
    return this.request<DraftDetail>("GET", `/drafts/${encodeURIComponent(sopId)}`);
  }
  saveDraft(sopId: string, source: string): Promise<{ sop_id: string; revision: number; source: string }> {
    return this.request("PUT", `/drafts/${encodeURIComponent(sopId)}`, { source });
  }
}

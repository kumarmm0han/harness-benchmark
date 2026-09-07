export type Identity = 'demo-author' | 'demo-consumer';

export interface Issue {
  code: string;
  stage: 'structural' | 'semantic';
  message: string;
  path: string;
}

export interface ValidateResult {
  valid: boolean;
  issues: Issue[];
  content: Record<string, unknown> | null;
}

export interface DraftSummary {
  sop_id: string;
  revision: number;
  publish_failed: boolean;
}

export interface DraftDetail extends DraftSummary {
  source: string;
}

export interface SopSummary {
  sop_id: string;
  title: string | null;
  version: number;
  domain: string | null;
  risk_level: string | null;
}

export interface SopListResult {
  sops: SopSummary[];
}

/** Current published snapshot as returned by GET /api/v1/sops/{sop_id}. */
export interface SopDetail {
  sop_id: string;
  version: number;
  content: Record<string, unknown>;
}

/** Immutable historical snapshot (GET /api/v1/sops/{sop_id}/versions/{version}). */
export interface VersionSnapshot {
  sop_id: string;
  version: number;
  published_revision: number;
  published_at: string | null;
  content: Record<string, unknown>;
}

/** 200 response of POST /api/v1/sops/{sop_id}/publish (spec.md §4 envelope). */
export interface PublishResult {
  sop_id: string;
  version: number;
  published_at: string;
  content: Record<string, unknown>;
}

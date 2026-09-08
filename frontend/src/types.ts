// API types (IR-001, spec.md §4). Snake_case keys on the wire; the client returns them as-is.

export type Identity = "demo-author" | "demo-consumer";

export interface SopSummary {
  sop_id: string;
  title: string;
  version: number;
  domain: string;
  risk: string;
}

export interface DraftSummary {
  sop_id: string;
  revision: number;
  publication_failed: boolean;
  updated_at: string;
}

export interface DraftDetail extends DraftSummary {
  source: string;
}

export interface ValidationIssue {
  code: string;
  stage: string; // "structural" | "semantic"
  message: string;
  path?: string;
}

/** The fixed API error envelope (IR-001). */
export interface ApiError {
  code: string;
  message: string;
  issues: ValidationIssue[];
}

/** Canonical content object (a subset of spec.md §4, as the UI surfaces it). */
export interface Content {
  sop_id: string;
  title: string;
  owner_team: string;
  domain: string;
  intent: string;
  risk_level: string;
  max_autonomy: string;
  policy: { use_when: string[]; do_not_use_when: string[] };
  inputs: { name: string; type: string }[];
  rules: { id: string; conditions: { input: string; op: string; value: unknown }[]; action_ids: string[] }[];
  actions: { id: string; kind: string; description: string; max_amount?: number }[];
  boundaries: { escalation: { action_id: string; input: string; op: string; amount: number; target_action_id: string }[] };
  customer_messages: { primary: string; escalation: string };
}

/** The published snapshot envelope (spec.md §4). */
export interface Published {
  sop_id: string;
  version: number;
  published_at: string;
  content: Content;
}

export interface ValidateResult {
  valid: boolean;
  issues: ValidationIssue[];
  content: Content | null;
}

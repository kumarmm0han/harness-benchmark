export type Identity = 'demo-author' | 'demo-consumer';
export interface Issue {code: string; stage: 'structural' | 'semantic'; message: string; path: string}
export interface Content {
  sop_id: string; title: string; owner_team: string; domain: string; intent: string; risk_level: string; max_autonomy: string;
  policy: {use_when: string[]; do_not_use_when: string[]};
  inputs: {name: string; type: string}[];
  rules: {id: string; conditions: {input: string; op: string; value: number | boolean}[]; action_ids: string[]}[];
  actions: {id: string; kind: string; description: string; max_amount?: number}[];
  boundaries: {escalation: {action_id: string; input: string; op: string; amount: number; target_action_id: string}[]};
  customer_messages: {primary: string; escalation: string};
}
export interface Snapshot {sop_id: string; version: number; published_at: string; content: Content}
export interface Summary {sop_id: string; title: string; version: number; domain: string; risk: string}
export interface DraftSummary {sop_id: string; revision: number; failed_revision: number | null; current_version: number | null}
export interface Draft extends DraftSummary {source: string}
export interface Validation {valid: boolean; issues: Issue[]; content: Content | null}
export class ApiError extends Error {
  constructor(message: string, public issues: Issue[] = []) {super(message)}
}
export async function request<T>(identity: Identity, path: string, method = 'GET', body?: unknown): Promise<T> {
  let response: Response;
  try { response = await fetch(`/api/v1${path}`, {method, headers: {'X-Demo-User': identity, 'Content-Type': 'application/json'}, ...(body === undefined ? {} : {body: JSON.stringify(body)})}); }
  catch { throw new ApiError('Cannot reach the server. Your editor content is retained.'); }
  if (!response.ok) {
    let error: {message?: string; issues?: Issue[]} = {};
    try {error = await response.json()} catch { /* A proxy may return a non-JSON error. */ }
    throw new ApiError(error.message ?? 'The request failed. Your editor content is retained.', error.issues ?? []);
  }
  return response.json() as Promise<T>;
}

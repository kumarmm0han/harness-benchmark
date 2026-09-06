export type Identity = 'demo-author' | 'demo-consumer';

export interface Issue {
  code: string;
  stage: 'structural' | 'semantic';
  message: string;
  path: string | null;
}

export interface Policy {
  use_when: string[];
  do_not_use_when: string[];
}

export interface InputDef {
  name: string;
  type: string;
}

export interface Condition {
  input: string;
  op: string;
  value: unknown;
}

export interface RuleDef {
  id: string;
  conditions: Condition[];
  action_ids: string[];
}

export interface ActionDef {
  id: string;
  kind: string;
  description: string;
  max_amount?: number;
}

export interface EscalationDef {
  action_id: string;
  input: string;
  op: string;
  amount: unknown;
  target_action_id: string;
}

export interface Content {
  sop_id: string;
  title: string;
  owner_team: string;
  domain: string;
  intent: string;
  risk_level: string;
  max_autonomy: string;
  policy: Policy;
  inputs: InputDef[];
  rules: RuleDef[];
  actions: ActionDef[];
  boundaries: { escalation: EscalationDef[] };
  customer_messages: { primary: string; escalation: string };
}

export interface Envelope {
  sop_id: string;
  version: number;
  published_at: string;
  content: Content;
}

export interface DraftDto {
  sop_id: string;
  revision: number;
  source: string;
  publish_failed: boolean;
}

export interface DraftSummary {
  sop_id: string;
  revision: number;
  publish_failed: boolean;
}

export interface SopSummary {
  sop_id: string;
  title: string;
  version: number;
  domain: string;
  risk: string;
}

export interface ValidateResponse {
  valid: boolean;
  issues: Issue[];
  content: Content | null;
}

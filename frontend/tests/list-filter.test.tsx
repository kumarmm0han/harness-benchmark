import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SopList } from '../src/components/SopList';
import { DraftList } from '../src/components/DraftList';
import { FilterBar } from '../src/components/FilterBar';
import type { DraftSummary, SopSummary } from '../src/api/types';

const SOPS: SopSummary[] = [
  { sop_id: 'BILL-REFUND-001', title: 'Refund for Duplicate Charge', version: 1, domain: 'Billing', risk: 'medium' },
  { sop_id: 'SUP-FAQ-001', title: 'Support FAQ', version: 2, domain: 'Support', risk: 'low' },
];
const DRAFTS: DraftSummary[] = [
  { sop_id: 'BILL-REFUND-001', revision: 3, publish_failed: true },
  { sop_id: 'NEW-001', revision: 1, publish_failed: false },
];

describe('SopList (TASK-016)', () => {
  it('renders published rows with identity+version', () => {
    render(<SopList sops={SOPS} onOpen={vi.fn()} />);
    expect(screen.getAllByRole('link').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('BILL-REFUND-001')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('shows the empty state text when []', () => {
    render(<SopList sops={[]} onOpen={vi.fn()} />);
    expect(screen.getByText(/No published SOPs match/i)).toBeInTheDocument();
  });

  it('rows are keyboard-focusable <a> elements (tab reachable)', () => {
    render(<SopList sops={[SOPS[0]]} onOpen={vi.fn()} />);
    const link = screen.getByRole('link', { name: 'BILL-REFUND-001' });
    expect(link.tagName).toBe('A');
    // Anchors with an href are natively in the tab order (tabIndex >= 0).
    expect(link.tabIndex).toBeGreaterThanOrEqual(0);
  });
});

describe('FilterBar (TASK-016)', () => {
  it('domain/risk dropdowns appear and call handlers', async () => {
    const onDomain = vi.fn();
    const onRisk = vi.fn();
    render(<FilterBar domain="" risk="" onDomain={onDomain} onRisk={onRisk} />);
    await userEvent.selectOptions(screen.getByLabelText('Domain'), 'Billing');
    expect(onDomain).toHaveBeenCalledWith('Billing');
    await userEvent.selectOptions(screen.getByLabelText('Risk'), 'medium');
    expect(onRisk).toHaveBeenCalledWith('medium');
  });
});

describe('DraftList (TASK-016 — author-only; hidden by App for consumers)', () => {
  it('shows drafts with revision and publish-failure indicator', () => {
    render(<DraftList drafts={DRAFTS} onOpen={vi.fn()} />);
    expect(screen.getByText('BILL-REFUND-001')).toBeInTheDocument();
    expect(screen.getByText('NEW-001')).toBeInTheDocument();
    expect(screen.getByTestId('draft-failed')).toHaveTextContent(/Publish failed/);
  });

  it('empty state text when []', () => {
    render(<DraftList drafts={[]} onOpen={vi.fn()} />);
    expect(screen.getByText(/No saved drafts yet/i)).toBeInTheDocument();
  });
});

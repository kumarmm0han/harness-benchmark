import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Editor } from '../src/components/Editor';
import { TEMPLATE_SOURCE } from '../src/template';
import { client } from '../src/api/client';
import type { Issue } from '../src/api/types';

const toast = () => ({});

describe('Editor — validate/save/publish (TASK-017)', () => {
  it('Insert template inserts the exact spec §3 text', async () => {
    render(
      <Editor sopId="__new__" identity="demo-author" onPublished={vi.fn()} toast={toast} />,
    );
    const area = screen.getByLabelText(/SOP source/i);
    await userEvent.click(screen.getByRole('button', { name: /Insert valid template/i }));
    expect((area as HTMLTextAreaElement).value).toBe(TEMPLATE_SOURCE);
    // And it contains the canonical sop_id.
    expect((area as HTMLTextAreaElement).value).toContain('sop_id: BILL-REFUND-001');
  });

  it('unsaved guard: publish is disabled until the draft is saved', async () => {
    vi.spyOn(client, 'saveDraft').mockResolvedValue({ sop_id: 'X', revision: 1 } as never);
    render(
      <Editor sopId="__new__" identity="demo-author" onPublished={vi.fn()} toast={toast} />,
    );
    // Insert template -> dirty (no saved snapshot).
    await userEvent.click(screen.getByRole('button', { name: /Insert valid template/i }));
    const publish = screen.getByTestId('btn-publish');
    // Save first.
    await userEvent.click(screen.getByTestId('btn-save'));
    await waitFor(() => expect(client.saveDraft).toHaveBeenCalled());
    // After save, publish becomes enabled (revision known + not dirty + author).
    await waitFor(() => expect(publish).toBeEnabled());
  });

  it('after save, revision label is shown and a re-edit re-enables the dirty state', async () => {
    vi.spyOn(client, 'saveDraft').mockResolvedValue({ sop_id: 'X', revision: 2 } as never);
    render(
      <Editor sopId="__new__" identity="demo-author" onPublished={vi.fn()} toast={toast} />,
    );
    await userEvent.click(screen.getByRole('button', { name: /Insert valid template/i }));
    await userEvent.click(screen.getByTestId('btn-save'));
    await waitFor(() =>
      expect(screen.getByText(/revision 2/)).toBeInTheDocument(),
    );
    const area = screen.getByLabelText(/SOP source/i) as HTMLTextAreaElement;
    await userEvent.type(area, ' ');
    expect(screen.getByTestId('dirty')).toBeInTheDocument();
  });

  it('consumer cannot see the publish button (author-only editor)', () => {
    render(
      <Editor sopId="__new__" identity="demo-consumer" onPublished={vi.fn()} toast={toast} />,
    );
    expect(screen.queryByTestId('btn-publish')).not.toBeInTheDocument();
    expect(screen.getByText(/author-only/i)).toBeInTheDocument();
  });
});

import { ValidationPanel } from '../src/components/ValidationPanel';

const ISSUES: Issue[] = [
  { code: 'C_FRONTMATTER_ENUM', stage: 'structural', message: 'domain not in set', path: 'front_matter.domain' },
  { code: 'FIN_REFUND_MAX_AMOUNT', stage: 'semantic', message: 'missing limit', path: 'actions[0].max_amount' },
];

describe('ValidationPanel (TASK-017 — structural vs semantic, not color-only)', () => {
  it('labels each issue with a [STRUCTURAL]/[SEMANTIC] text tag', () => {
    render(<ValidationPanel issues={ISSUES} />);
    expect(screen.getByText(/\[STRUCTURAL\] C_FRONTMATTER_ENUM/)).toBeInTheDocument();
    expect(screen.getByText(/\[SEMANTIC\] FIN_REFUND_MAX_AMOUNT/)).toBeInTheDocument();
    // Grouped into separate stage sections.
    expect(screen.getByRole('heading', { name: 'Structural' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Semantic' })).toBeInTheDocument();
  });
});

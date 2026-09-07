import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import App from '../App';

describe('App shell (TASK-002 placeholder)', () => {
  it('renders the demo title', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'SOP Demo' })).toBeInTheDocument();
  });
});

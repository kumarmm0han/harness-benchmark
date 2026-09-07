import { useState } from 'react';
import type { Identity } from './types';
import Browse from './views/Browse';
import SopDetail from './views/SopDetail';
import Drafts from './views/Drafts';
import Editor from './views/Editor';

let editorKey = 0;

type View =
  | { name: 'browse' }
  | { name: 'sop'; sopId: string; version?: number }
  | { name: 'drafts' }
  | { name: 'editor'; sopId?: string; key: number };

/**
 * Demo shell (FR-001, NFR-050): a labeled local-only identity selector,
 * identity-aware navigation (authors get drafts; consumers never see draft
 * data), and the five views. Status is always conveyed as text.
 */
export default function App() {
  const [identity, setIdentity] = useState<Identity>('demo-author');
  const [view, setView] = useState<View>({ name: 'browse' });
  const isAuthor = identity === 'demo-author';

  const switchIdentity = (id: Identity) => {
    setIdentity(id);
    // Leave author-only views when switching to a consumer (FR-001).
    if (id === 'demo-consumer' && (view.name === 'drafts' || view.name === 'editor')) {
      setView({ name: 'browse' });
    }
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>SOP Demo</h1>
        <p className="tagline">
          Author a structured SOP → validate → publish an immutable version → read the same published
          version in human and JSON views. Local demo only — no real authentication, no real business
          actions.
        </p>
        <fieldset className="identity" aria-label="Demo identity (local only)">
          <legend>Demo identity (local only — not real authentication)</legend>
          <label>
            <input
              type="radio"
              name="identity"
              value="demo-author"
              checked={identity === 'demo-author'}
              onChange={() => switchIdentity('demo-author')}
            />{' '}
            demo-author
          </label>{' '}
          <label>
            <input
              type="radio"
              name="identity"
              value="demo-consumer"
              checked={identity === 'demo-consumer'}
              onChange={() => switchIdentity('demo-consumer')}
            />{' '}
            demo-consumer
          </label>
        </fieldset>
        <p className="whoami" aria-live="polite">
          Acting as <strong>{identity}</strong>
          {isAuthor ? ' (can author, save, validate, publish, and read history)' : ' (reader of published content only)'}
        </p>

        <nav aria-label="Main">
          <button type="button" onClick={() => setView({ name: 'browse' })}>
            Published SOPs
          </button>{' '}
          {isAuthor && (
            <>
              <button type="button" onClick={() => setView({ name: 'drafts' })}>
                My drafts
              </button>{' '}
              <button
                type="button"
                onClick={() => setView({ name: 'editor', key: ++editorKey })}
              >
                New draft
              </button>
            </>
          )}
        </nav>
      </header>

      <main>
        {view.name === 'browse' && (
          <Browse identity={identity} onOpen={(sopId) => setView({ name: 'sop', sopId })} />
        )}
        {view.name === 'sop' && (
          <SopDetail
            identity={identity}
            sopId={view.sopId}
            version={view.version}
            onBack={() => setView({ name: 'browse' })}
            onVersion={isAuthor ? (v) => setView({ name: 'sop', sopId: view.sopId, version: v }) : undefined}
          />
        )}
        {view.name === 'drafts' && isAuthor && (
          <Drafts
            identity={identity}
            onOpen={(sopId) => setView({ name: 'editor', sopId, key: Date.now() })}
            onNew={() => setView({ name: 'editor', key: Date.now() })}
          />
        )}
        {view.name === 'editor' && isAuthor && (
          <Editor
            key={view.key}
            identity={identity}
            sopId={view.sopId}
            onPublished={(sopId) => setView({ name: 'sop', sopId })}
          />
        )}
      </main>

      <footer className="app-footer">
        <p>
          Demo identities are fixed configuration (DR-003). Identities, drafts, and published data are
          local-only. Removing data is described in the README.
        </p>
      </footer>
    </div>
  );
}

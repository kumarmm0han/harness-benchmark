import { useCallback, useEffect, useState } from 'react';
import { IdentityProvider, useIdentity } from './state/IdentityContext';
import { IdentitySelector } from './components/IdentitySelector';
import { FilterBar } from './components/FilterBar';
import { SopList } from './components/SopList';
import { DraftList } from './components/DraftList';
import { Editor } from './components/Editor';
import { HumanView } from './components/HumanView';
import { JsonView } from './components/JsonView';
import type { DraftSummary, Envelope, Issue, SopSummary } from './api/types';
import { client, ApiError } from './api/client';
import './styles.css';

type ToastState = { kind: 'ok' | 'error'; text: string };

function Shell() {
  const { identity } = useIdentity();
  const [tab, setTab] = useState<'list' | 'drafts' | 'editor' | 'detail'>('list');
  const [currentSopId, setCurrentSopId] = useState<string | null>(null);
  const [editorSopId, setEditorSopId] = useState<string>('__new__');

  const [sops, setSops] = useState<SopSummary[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [listIssues, setListIssues] = useState<Issue[]>([]);
  const [domainFilter, setDomainFilter] = useState('');
  const [riskFilter, setRiskFilter] = useState('');

  const [drafts, setDrafts] = useState<DraftSummary[]>([]);
  const [draftError, setDraftError] = useState<string | null>(null);

  const [detail, setDetail] = useState<Envelope | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [toast, setToastState] = useState<ToastState | null>(null);
  const toastFn = useCallback((kind: 'ok' | 'error', text: string) => {
    setToastState({ kind, text });
    window.setTimeout(() => setToastState((t) => (t && t.text === text ? null : t)), 6000);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setListLoading(true);
    setListError(null);
    client
      .listSops(identity, {
        domain: domainFilter || undefined,
        risk: riskFilter || undefined,
      })
      .then((rows) => {
        if (!cancelled) {
          setSops(rows);
          setListIssues([]);
        }
      })
      .catch((e) => {
        if (cancelled) return;
        if (e instanceof ApiError && e.status === 400) {
          setSops([]);
          setListIssues((e.issues as Issue[]) ?? []);
          setListError(`${e.code}: ${e.message}`);
        } else {
          setSops([]);
          setListError(e instanceof Error ? e.message : 'failed to load');
        }
      })
      .finally(() => !cancelled && setListLoading(false));
    return () => {
      cancelled = true;
    };
  }, [identity, domainFilter, riskFilter]);

  useEffect(() => {
    if (identity !== 'demo-author') {
      setDrafts([]);
      setDraftError(null);
      return;
    }
    let cancelled = false;
    setDraftError(null);
    client
      .listDrafts(identity)
      .then((rows) => !cancelled && setDrafts(rows))
      .catch((e) => !cancelled && setDraftError(e instanceof Error ? e.message : 'drafts failed'));
    return () => {
      cancelled = true;
    };
  }, [identity]);

  useEffect(() => {
    if (!currentSopId) {
      setDetail(null);
      setDetailError(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);
    client
      .getSop(currentSopId, identity)
      .then((env) => {
        if (!cancelled) {
          setDetail(env);
        }
      })
      .catch((e) => {
        if (cancelled) return;
        setDetail(null);
        if (e instanceof ApiError && e.status === 404) setDetailError('No current publication for this SOP yet.');
        else if (e instanceof ApiError && e.status === 403) setDetailError(`Forbidden for identity ${e.message}.`);
        else setDetailError(e instanceof Error ? e.message : 'failed to load detail');
      })
      .finally(() => !cancelled && setDetailLoading(false));
    return () => {
      cancelled = true;
    };
  }, [currentSopId, identity]);

  const isAuthor = identity === 'demo-author';

  return (
    <div className="container">
      <header>
        <h1>SOP Demo — local-only publishing</h1>
        <IdentitySelector />
      </header>

      {toast ? (
        <div className={`toast ${toast.kind}`} role="status" aria-live="polite" data-testid="toast">
          {toast.text}
        </div>
      ) : null}

      <div className="tabs" role="tablist">
        <button className={tab === 'list' ? 'active' : ''} onClick={() => setTab('list')} data-testid="tab-list">
          Published SOPs
        </button>
        {isAuthor ? (
          <button className={tab === 'drafts' ? 'active' : ''} onClick={() => setTab('drafts')} data-testid="tab-drafts">
            Saved drafts
          </button>
        ) : null}
        <button className={tab === 'editor' ? 'active' : ''} onClick={() => setTab('editor')} data-testid="tab-editor">
          Editor
        </button>
        {currentSopId ? (
          <button
            className={tab === 'detail' ? 'active' : ''}
            onClick={() => setTab('detail')}
            data-testid="tab-detail"
          >
            Detail — {currentSopId}
          </button>
        ) : null}
      </div>

      {tab === 'list' ? (
        <section>
          <FilterBar
            domain={domainFilter}
            risk={riskFilter}
            onDomain={setDomainFilter}
            onRisk={setRiskFilter}
          />
          {listError ? (
            <div>
              <div className="toast error" role="alert">
                {listError}
              </div>
              {listIssues.length > 0 ? (
                <ValidationNotice issues={listIssues} />
              ) : null}
            </div>
          ) : null}
          {listLoading ? (
            <div className="muted" role="status">
              Loading published SOPs…
            </div>
          ) : (
            <SopList
              sops={sops}
              onOpen={(id) => {
                setCurrentSopId(id);
                setTab('detail');
              }}
            />
          )}
          <p className="muted">
            Identity: <strong>{identity}</strong>. {isAuthor ? 'Authors can edit and publish.' : 'Consumers read published content only.'}
          </p>
        </section>
      ) : null}

      {tab === 'drafts' && isAuthor ? (
        <section>
          <h2>Saved drafts (demo-author only)</h2>
          {draftError ? <div className="toast error" role="alert">{draftError}</div> : null}
          <DraftList
            drafts={drafts}
            onOpen={(id) => {
              setEditorSopId(id);
              setTab('editor');
            }}
          />
        </section>
      ) : null}

      {tab === 'editor' ? (
        <section>
          <Editor
            sopId={editorSopId}
            identity={identity}
            onPublished={(id) => {
              setCurrentSopId(id);
              setTab('detail');
            }}
            toast={toastFn}
          />
        </section>
      ) : null}

      {tab === 'detail' && currentSopId ? (
        <DetailPane
          sopId={currentSopId}
          isAuthor={isAuthor}
          detail={detail}
          detailError={detailError}
          detailLoading={detailLoading}
          onEdit={() => {
            setEditorSopId(currentSopId);
            setTab('editor');
          }}
        />
      ) : null}

      <footer>
        <p className="muted">
          Local-only demo. Identities are fixed local accounts, not production authentication. No external
          services, no real customer data, no execution of rules or actions.
        </p>
      </footer>
    </div>
  );
}

function DetailPane({
  sopId,
  isAuthor,
  detail,
  detailError,
  detailLoading,
  onEdit,
}: {
  sopId: string;
  isAuthor: boolean;
  detail: Envelope | null;
  detailError: string | null;
  detailLoading: boolean;
  onEdit: () => void;
}) {
  const [mode, setMode] = useState<'human' | 'json'>('human');
  return (
    <section>
      <div className="actions-bar" role="group" aria-label="Detail view mode">
        <button
          onClick={() => setMode('human')}
          data-testid="mode-human"
          className={mode === 'human' ? 'active' : ''}
        >
          Human view
        </button>
        <button
          onClick={() => setMode('json')}
          data-testid="mode-json"
          className={mode === 'json' ? 'active' : ''}
        >
          JSON view
        </button>
        {isAuthor ? <button onClick={onEdit}>Open editor</button> : null}
      </div>
      {detailError ? (
        <div className="toast error" role="alert">
          {detailError}
        </div>
      ) : null}
      {detailLoading && !detail ? (
        <div className="muted" role="status">
          Loading…
        </div>
      ) : null}
      {detail ? (
        // Human and JSON views render the SAME fetched snapshot (FR-053).
        <div data-testid={`detail-pane-${sopId}`}>
          {mode === 'human' ? <HumanView envelope={detail} /> : <JsonView envelope={detail} />}
        </div>
      ) : null}
    </section>
  );
}

function ValidationNotice({ issues }: { issues: Issue[] }) {
  return (
    <div className="panel">
      <h3>{issues.length} filter issue(s)</h3>
      <ul>
        {issues.map((i, n) => (
          <li key={n} className="issue">
            <strong>{i.code}</strong> — {i.message}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default function App() {
  return (
    <IdentityProvider>
      <Shell />
    </IdentityProvider>
  );
}

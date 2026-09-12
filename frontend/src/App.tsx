import {useEffect, useState} from 'react';
import {ApiError, request} from './api';
import type {Draft, DraftSummary, Identity, Issue, Snapshot, Summary, Validation} from './api';
import {Detail, HumanContent} from './Detail';
import template from './template.md?raw';

function Issues({issues}: {issues: Issue[]}) {
  return <>{(['structural', 'semantic'] as const).map(stage => {
    const group = issues.filter(i => i.stage === stage);
    return group.length > 0 && <section key={stage} aria-label={`${stage} errors`}><h3>{stage === 'structural' ? 'Structural' : 'Semantic'} errors</h3><ul>{group.map((issue, index) => <li key={index}><strong>{issue.path}</strong>: {issue.message} <small>({issue.code})</small></li>)}</ul></section>;
  })}</>;
}
function Workspace({identity}: {identity: Identity}) {
  const author = identity === 'demo-author';
  const [list, setList] = useState<Summary[]>([]);
  const [drafts, setDrafts] = useState<DraftSummary[]>([]);
  const [domain, setDomain] = useState(''); const [risk, setRisk] = useState('');
  const [source, setSource] = useState(''); const [id, setId] = useState('BILL-REFUND-001');
  const [saved, setSaved] = useState<Draft | null>(null);
  const [validation, setValidation] = useState<Validation | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [status, setStatus] = useState('Loading SOPs…'); const [error, setError] = useState('');
  const [issues, setIssues] = useState<Issue[]>([]); const [busy, setBusy] = useState(false);
  const dirty = saved ? source !== saved.source || id !== saved.sop_id : source.length > 0;
  const query = new URLSearchParams({...domain && {domain}, ...risk && {risk}}).toString();
  useEffect(() => {
    let current = true;
    setStatus('Loading SOPs…');
    Promise.all([request<Summary[]>(identity, `/sops?${query}`), author ? request<DraftSummary[]>(identity, '/drafts') : Promise.resolve([])]).then(([sops, authorDrafts]) => {
      if (current) {setList(sops); setDrafts(authorDrafts); setStatus('SOPs loaded.'); setError('');}
    }).catch(e => {if (current) {setError(e.message); setStatus('Loading failed.');}});
    return () => {current = false};
  }, [identity, author, query]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => {if (dirty) event.preventDefault()};
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  async function refresh() {
    const [sops, authorDrafts] = await Promise.all([request<Summary[]>(identity, `/sops?${query}`), author ? request<DraftSummary[]>(identity, '/drafts') : Promise.resolve([])]);
    setList(sops); setDrafts(authorDrafts);
  }
  async function run(work: () => Promise<void>) {
    setBusy(true); setError(''); setIssues([]);
    try {await work()} catch (e) {setError(e instanceof Error ? e.message : 'Request failed.'); if (e instanceof ApiError) setIssues(e.issues); setStatus('Request failed.');} finally {setBusy(false)}
  }
  function edit(value: string) {setSource(value); setValidation(null); setIssues([])}
  async function openDraft(draftId: string) {
    await run(async () => {const d = await request<Draft>(identity, `/drafts/${encodeURIComponent(draftId)}`); setSaved(d); setId(d.sop_id); setSource(d.source); setValidation(null); setStatus(`Opened revision ${d.revision}.`)});
  }
  async function save() {
    await run(async () => {
      const d = await request<Draft>(identity, `/drafts/${encodeURIComponent(id)}`, 'PUT', {source});
      setSaved({...d, failed_revision: null, current_version: saved?.sop_id === id ? saved.current_version : null});
      setStatus(`Saved revision ${d.revision}. Published content is unchanged.`); await refresh();
    });
  }
  async function publish() {
    if (!saved || dirty) return;
    await run(async () => {
      try {
        const published = await request<Snapshot>(identity, `/sops/${encodeURIComponent(saved.sop_id)}/publish`, 'POST', {revision: saved.revision});
        setSnapshot(published); setSaved({...saved, failed_revision: null, current_version: published.version}); setStatus(`Published version ${published.version}.`); await refresh();
      } catch (e) {
        if (e instanceof ApiError && e.issues.length > 0) {
          setSaved({...saved, failed_revision: saved.revision});
          setDrafts(current => current.map(d => d.sop_id === saved.sop_id ? {...d, failed_revision: saved.revision} : d));
        }
        throw e;
      }
    });
  }
  return <main>
    <div role="status" className="status">{busy ? 'Working…' : status}</div>
    {error && <div role="alert" className="error">{error}</div>}
    <Issues issues={issues}/>
    <section className="panel" aria-label="Published SOPs"><h2>Published SOPs</h2>
      <div className="filters"><label>Domain<select value={domain} onChange={e => setDomain(e.target.value)} disabled={busy}><option value="">All domains</option><option>Billing</option><option>Support</option></select></label>
      <label>Risk<select value={risk} onChange={e => setRisk(e.target.value)} disabled={busy}><option value="">All risks</option><option>low</option><option>medium</option></select></label><button disabled={busy} onClick={() => run(async () => {await refresh(); setStatus('Lists refreshed.')})}>Refresh lists</button></div>
      {list.length === 0 ? <p>No published SOPs match these filters.</p> : <ul className="sop-list">{list.map(s => <li key={s.sop_id}><button disabled={busy} onClick={() => run(async () => {setSnapshot(await request<Snapshot>(identity, `/sops/${encodeURIComponent(s.sop_id)}`)); setStatus('Published snapshot loaded.')})}>{s.sop_id} — {s.title}</button><span>Version {s.version} · {s.domain} · {s.risk}</span></li>)}</ul>}
    </section>
    {author && <section className="panel" aria-label="Author workspace"><h2>Author workspace</h2>
      <h3>Saved drafts</h3>{drafts.length === 0 ? <p>No drafts saved yet. Insert the template to begin.</p> : <ul>{drafts.map(d => <li key={d.sop_id}><button disabled={busy || dirty} onClick={() => openDraft(d.sop_id)}>Open draft {d.sop_id}</button> · Revision {d.revision}{d.failed_revision !== null && ` · Publication failed for revision ${d.failed_revision}`}</li>)}</ul>}
      {dirty && <p>Save your unsaved changes before opening another draft.</p>}
      <div className="actions"><button disabled={busy} onClick={() => {edit(template); setId('BILL-REFUND-001'); setSaved(null); setStatus('Template inserted. Change both the draft ID and source sop_id to create another SOP.')}}>Insert valid template</button></div>
      <label>Draft SOP ID<input value={id} onChange={e => setId(e.target.value)} disabled={busy}/></label>
      <p>The draft ID must match sop_id in the Markdown before publication.</p>
      <label>Markdown source<textarea rows={22} value={source} onChange={e => edit(e.target.value)} disabled={busy} spellCheck={false}/></label>
      <p>{dirty ? 'Unsaved changes' : saved ? `Saved revision ${saved.revision}` : 'No saved revision'}</p>
      {saved?.failed_revision != null && <p role="alert">Publication failed for revision {saved.failed_revision}. {saved.current_version ? `Previous version ${saved.current_version} remains available.` : 'No published version is available.'}</p>}
      <div className="actions"><button disabled={busy} onClick={save}>Save draft</button><button disabled={busy} onClick={() => run(async () => {const result = await request<Validation>(identity, '/validate', 'POST', {source}); setValidation(result); setIssues(result.issues); setStatus(result.valid ? 'Validation passed. Preview is ready.' : 'Validation failed. Correct the listed fields.')})}>Validate / preview</button><button disabled={busy || !saved || dirty} onClick={publish}>Publish saved revision</button></div>
      {validation?.valid && validation.content && <details open><summary>Canonical content preview (unpublished)</summary><HumanContent content={validation.content}/><pre>{JSON.stringify(validation.content, null, 2)}</pre></details>}
    </section>}
    {snapshot && <Detail snapshot={snapshot}/>}
  </main>;
}
export default function App() {
  const [identity, setIdentity] = useState<Identity>('demo-author');
  return <><header><p className="eyebrow">LOCAL SOP DEMO</p><h1>Author once. Read one policy.</h1><p>Structured Markdown → validated canonical JSON → human and AI views.</p><label>Demo-only identity<select value={identity} onChange={e => setIdentity(e.target.value as Identity)}><option>demo-author</option><option>demo-consumer</option></select></label><p>Local demo identity selection only. No real business actions are performed.</p></header><Workspace key={identity} identity={identity}/></>;
}

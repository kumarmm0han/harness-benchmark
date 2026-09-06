import { useCallback, useEffect, useState } from 'react';
import { ValidationPanel } from './ValidationPanel';
import { TEMPLATE_SOURCE } from '../template';
import type { Identity, Issue } from '../api/types';
import { client, ApiError } from '../api/client';

interface EditorProps {
  sopId: string;
  identity: Identity;
  onPublished: (sopId: string) => void;
  toast: (kind: 'ok' | 'error', text: string) => void;
}

/** Extract the front-matter sop_id (first `sop_id:` line) so the save target
 * follows the document while the author edits it (IR-001). */
export function extractSopId(source: string): string | null {
  const m = source.match(/^sop_id:\s*([^\s]+)/m);
  return m ? m[1] : null;
}

export function Editor({ sopId, identity, onPublished, toast }: EditorProps) {
  const [source, setSource] = useState('');
  const [savedSnapshot, setSavedSnapshot] = useState<string | null>(null);
  const [revision, setRevision] = useState<number | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [validation, setValidation] = useState<{ issues: Issue[]; valid: boolean } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (sopId === '__new__') {
      setSavedSnapshot(null);
      setRevision(null);
      setLoadFailed(false);
      return;
    }
    client
      .getDraft(sopId, identity)
      .then((d) => {
        setSource(d.source);
        setSavedSnapshot(d.source);
        setRevision(d.revision);
      })
      .catch(() => setLoadFailed(true));
  }, [sopId, identity]);

  const dirty = savedSnapshot != null && source !== savedSnapshot;
  const targetSopId = extractSopId(source) ?? sopId;
  const isAuthor = identity === 'demo-author';

  const saveDraft = useCallback(async () => {
    const id = extractSopId(source);
    if (!id) {
      toast('error', 'The front matter must include a sop_id before saving.');
      return;
    }
    setBusy(true);
    try {
      const d = await client.saveDraft(id, source, identity);
      setSavedSnapshot(source);
      setRevision(d.revision);
      toast('ok', `Saved ${id} @ revision ${d.revision}.`);
    } catch (e) {
      toast('error', e instanceof ApiError ? `${e.code}: ${e.message}` : 'Save failed.');
    } finally {
      setBusy(false);
    }
  }, [source, identity, toast]);

  const validate = useCallback(async () => {
    setBusy(true);
    try {
      const r = await client.validate(source, identity);
      setValidation({ issues: r.issues as Issue[], valid: r.valid });
      toast('ok', r.valid ? 'Preview valid — no issues.' : `Preview has ${r.issues.length} issue(s).`);
    } catch (e) {
      toast('error', e instanceof ApiError ? `${e.code}: ${e.message}` : 'Validation request failed.');
    } finally {
      setBusy(false);
    }
  }, [source, identity, toast]);

  const publish = useCallback(async () => {
    if (revision == null) {
      toast('error', 'Save the draft first before publishing.');
      return;
    }
    const id = targetSopId;
    setBusy(true);
    try {
      const env = await client.publish(id, revision, identity);
      toast('ok', `Published ${id} as version ${env.version}.`);
      onPublished(id);
    } catch (e) {
      if (e instanceof ApiError && e.issues.length > 0) {
        setValidation({ issues: e.issues as Issue[], valid: false });
      }
      toast(
        'error',
        `Publish failed — previous version retained. ${e instanceof ApiError ? `${e.code}: ${e.message}` : ''}`.trim(),
      );
    } finally {
      setBusy(false);
    }
  }, [revision, targetSopId, identity, onPublished, toast]);

  if (!isAuthor) {
    return (
      <div className="toast error" role="alert">
        Draft editing is author-only. Switch identity to demo-author, or open a published SOP.
      </div>
    );
  }

  return (
    <div className="panel">
      <h2>
        Editor — {sopId === '__new__' ? 'new draft' : sopId}{' '}
        {revision != null ? <span className="chip">revision {revision}</span> : null}
        {dirty ? (
          <span className="chip" data-testid="dirty">
            unsaved changes
          </span>
        ) : null}
      </h2>
      {loadFailed ? (
        <p className="muted">No saved draft for {sopId} — paste source or insert the template below.</p>
      ) : null}
      <label htmlFor="editor-source" className="muted">
        SOP source (YAML front matter + sections)
      </label>
      <textarea
        id="editor-source"
        className="editor-text"
        value={source}
        onChange={(e) => setSource(e.target.value)}
        spellCheck={false}
      />
      <div className="actions-bar">
        <button onClick={() => setSource(TEMPLATE_SOURCE)} disabled={busy}>
          Insert valid template
        </button>
        <button onClick={saveDraft} disabled={busy} data-testid="btn-save">
          Save draft
        </button>
        <button onClick={validate} disabled={busy} data-testid="btn-validate">
          Validate / preview
        </button>
        <button
          onClick={publish}
          disabled={busy || dirty || revision == null}
          data-testid="btn-publish"
          title={dirty ? 'Save first — publication uses the saved revision' : undefined}
        >
          Publish
        </button>
        {dirty ? (
          <span className="muted" data-testid="publish-blocked">
            Publish is disabled until the draft is saved.
          </span>
        ) : null}
        {revision == null && !dirty ? <span className="muted">Save a draft to enable publishing.</span> : null}
      </div>
      {validation ? (
        validation.valid && validation.issues.length === 0 ? (
          <div className="toast ok" role="status" data-testid="validation-result">
            Preview is valid — ready to publish.
          </div>
        ) : (
          <div data-testid="validation-result">
            <ValidationPanel issues={validation.issues} />
          </div>
        )
      ) : null}
    </div>
  );
}

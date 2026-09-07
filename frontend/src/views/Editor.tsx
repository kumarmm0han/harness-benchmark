import { useEffect, useState } from 'react';
import { api, ApiError } from '../api';
import type { Identity, Issue, PublishResult, ValidateResult } from '../types';
import IssuePanel from '../components/IssuePanel';
import { TEMPLATE, TEMPLATE_SOP_ID } from '../template';

interface Props {
  identity: Identity;
  /** Existing draft to reopen; omitted for a new draft. */
  sopId?: string;
  onPublished: (sopId: string, version: number) => void;
}

type Notice =
  | { kind: 'info' | 'ok' | 'error'; text: string; issues?: Issue[] }
  | null;

/**
 * Draft editor (FR-010, FR-034, FR-045): plain textarea + save + validate
 * (structural vs. semantic issues, canonical preview) + publish.
 *
 * - Editor content is local state: failed saves/validation never discard it.
 * - "Unsaved changes" is always visible (FR-010).
 * - Publishing requires a saved revision: if the editor is dirty, the app
 *   saves first and publishes the returned revision; a failed save aborts
 *   publication (spec.md §5).
 */
export default function Editor({ identity, sopId, onPublished }: Props) {
  const [path, setPath] = useState(sopId ?? '');
  const [source, setSource] = useState('');
  const [dirty, setDirty] = useState(false);
  const [loaded, setLoaded] = useState(sopId === undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busy, setBusy] = useState<null | 'save' | 'validate' | 'publish'>(null);
  const [notice, setNotice] = useState<Notice>(null);
  const [validation, setValidation] = useState<ValidateResult | null>(null);
  const [revision, setRevision] = useState<number | null>(null);

  useEffect(() => {
    if (sopId === undefined) return;
    let cancelled = false;
    api
      .getDraft(identity, sopId)
      .then((d) => {
        if (cancelled) return;
        setPath(d.sop_id);
        setSource(d.source);
        setRevision(d.revision);
        setDirty(false);
        setLoaded(true);
        if (d.publish_failed) {
          setNotice({
            kind: 'error',
            text: `Draft revision ${d.revision} is marked as a failed publication attempt. The previously published version (if any) remains current for consumers. Saving or publishing again clears this flag.`
          });
        }
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setLoadError(e instanceof ApiError ? `${e.code}: ${e.message}` : 'Failed to load the draft.');
        setLoaded(true);
      });
    return () => {
      cancelled = true;
    };
    // Load once per sopId.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sopId]);

  const insertTemplate = () => {
    if (source && source.trim().length > 0) {
      if (!window.confirm('Replace the current draft content with the sample template?')) return;
    }
    setPath(TEMPLATE_SOP_ID);
    setSource(TEMPLATE);
    setDirty(true);
    setValidation(null);
    setNotice({ kind: 'info', text: 'Template inserted. Adjust sop_id and content, then save.' });
  };

  const doSave = async (): Promise<number | null> => {
    if (!path.trim()) {
      setNotice({ kind: 'error', text: 'Provide an SOP ID (e.g. from the front matter) before saving.' });
      return null;
    }
    setBusy('save');
    try {
      const res = await api.putDraft(identity, path.trim(), source);
      setRevision(res.revision);
      setPath(res.sop_id);
      setDirty(false);
      setNotice({ kind: 'ok', text: `Draft saved (revision ${res.revision}). Saving never changes published content.` });
      return res.revision;
    } catch (e) {
      const err = e as ApiError;
      setNotice({
        kind: 'error',
        text: `Save failed — your editor content is kept. ${err.code}: ${err.message}`
      });
      return null;
    } finally {
      setBusy(null);
    }
  };

  const doValidate = async () => {
    if (!path.trim()) {
      setNotice({ kind: 'error', text: 'Provide an SOP ID before validating.' });
      return;
    }
    setBusy('validate');
    try {
      // Validate the editor content (works on unsaved edits; never persists).
      const res = await api.validate(identity, source);
      setValidation(res);
      setNotice(
        res.valid
          ? { kind: 'ok', text: 'Valid. The canonical preview below is only a preview — nothing was saved or published.' }
          : { kind: 'error', text: 'Validation found issues (see below). Invalid previews never change stored state.' }
      );
    } catch (e) {
      const err = e as ApiError;
      setValidation(null);
      setNotice({ kind: 'error', text: `Validation request failed — editor content is kept. ${err.code}: ${err.message}` });
    } finally {
      setBusy(null);
    }
  };

  const doPublish = async () => {
    if (!path.trim()) {
      setNotice({ kind: 'error', text: 'Provide an SOP ID before publishing.' });
      return;
    }
    setBusy('publish');
    try {
      // Publication uses the saved revision: save first if dirty (spec.md §5).
      let rev = revision;
      if (dirty) {
        const saved = await doSave();
        if (saved === null) return; // save failed → abort
        rev = saved;
      } else if (rev === null) {
        const saved = await doSave();
        if (saved === null) return;
        rev = saved;
      }
      const res: PublishResult = await api.publish(identity, path.trim(), rev);
      setRevision(rev ?? null); // keep the saved-draft revision, not the SOP version
      setDirty(false);
      setNotice({
        kind: 'ok',
        text: `Published version ${res.version} of ${res.sop_id}. Consumers will see this version immediately.`
      });
      onPublished(res.sop_id, res.version);
    } catch (e) {
      const err = e as ApiError;
      if (err.status === 422) {
        setNotice({
          kind: 'error',
          issues: err.issues,
          text: 'Publication was rejected. The previously published version (if any) remains current. Fix the issues, save, and publish again.'
        });
      } else if (err.status === 409) {
        setNotice({
          kind: 'error',
          text: `Conflict: ${err.message} Save the draft to get the latest revision, then publish.`
        });
      } else {
        setNotice({ kind: 'error', text: `Publication failed: ${err.code}: ${err.message}` });
      }
    } finally {
      setBusy(null);
    }
  };

  if (!loaded) {
    return (
      <section aria-labelledby="editor-heading">
        <h2 id="editor-heading">Editor</h2>
        <p role="status">Loading draft {sopId}…</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="editor-heading">
      <h2 id="editor-heading">Editor</h2>
      {loadError && (
        <p role="alert" className="bad">
          {loadError}
        </p>
      )}
      <label className="sopid-field">
        SOP ID (used as the draft path)
        <input value={path} onChange={(e) => { setPath(e.target.value); setDirty(true); }} placeholder="e.g. BILL-REFUND-001" />
      </label>

      <label className="source-field">
        Draft source (Markdown + YAML)
        <textarea
          rows={18}
          value={source}
          onChange={(e) => {
            setSource(e.target.value);
            setDirty(true);
          }}
          aria-describedby="editor-help"
        />
      </label>
      <p id="editor-help" className="hint">
        Uses the documented template in spec.md. Machine-critical data comes from the YAML blocks and
        front matter, not from prose.
      </p>

      <p className="dirty-indicator" aria-live="polite">
        {dirty ? '● Unsaved changes' : 'No unsaved changes'}
      </p>

      <div className="editor-actions">
        <button type="button" onClick={insertTemplate} disabled={busy !== null}>
          Insert sample template
        </button>
        <button type="button" onClick={() => void doSave()} disabled={busy !== null}>
          Save draft
        </button>
        <button type="button" onClick={() => void doValidate()} disabled={busy !== null}>
          Validate &amp; preview
        </button>
        <button type="button" onClick={() => void doPublish()} disabled={busy !== null}>
          Publish
        </button>
      </div>

      {notice && (
        <p role="status" className={`notice ${notice.kind}`} aria-live="assertive">
          {notice.text}
        </p>
      )}

      {notice?.kind === 'error' && notice.issues && notice.issues.length > 0 && (
        <IssuePanel issues={notice.issues} />
      )}

      {validation && (
        <div className="validation">
          {validation.valid ? (
            <>
              <p role="status" className="ok">
                Valid. Canonical content preview (deterministic for this source):
              </p>
              <pre className="json-view">{JSON.stringify(validation.content, null, 2)}</pre>
            </>
          ) : (
            <>
              <p role="status" className="bad">
                Invalid — {validation.issues.length} issue(s). Content is omitted because the source is
                not publishable.
              </p>
              <IssuePanel issues={validation.issues} />
            </>
          )}
        </div>
      )}
    </section>
  );
}

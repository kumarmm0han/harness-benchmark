// Editor (TASK-008, DES-013/014, FR-010, FR-034, FR-042, NFR-050).
//
// One author surface: insert the spec.md template or paste structured Markdown, save the
// draft, validate (structural + semantic issues with paths), and publish by the saved
// revision. Guards (FR-010): publish is disabled until the buffer is saved, unsaved
// changes are indicated as text, and a failed save never discards the editor content.
// Authored text is always data (NFR-020) — the app never executes rules or actions.
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ApiClientError } from "../api";
import type { ApiClient } from "../api";
import { TEMPLATE } from "../template";
import type { Identity, ValidationIssue } from "../types";

interface Props {
  sopId: string;
  client: ApiClient;
  identity: Identity;
  onBack: () => void;
}

type StatusKind = "info" | "success" | "error" | "warn";
type Status = { kind: StatusKind; text: string };

function messageFor(e: unknown): string {
  if (e instanceof ApiClientError) {
    const base = `${e.code}: ${e.message}`;
    if (e.issues.length > 0) {
      return `${base} — ${e.issues.map((i) => (i.path ? `${i.path}: ${i.message}` : i.message)).join("; ")}`;
    }
    return base;
  }
  return e instanceof Error ? e.message : "Something went wrong.";
}

export default function Editor({ sopId, client, identity, onBack }: Props) {
  const isAuthor = identity === "demo-author";

  const [source, setSource] = useState<string>("");
  const [initialLoaded, setInitialLoaded] = useState<boolean>(false);
  const [revision, setRevision] = useState<number | null>(null);
  const [publicationFailed, setPublicationFailed] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [validating, setValidating] = useState<boolean>(false);
  const [publishing, setPublishing] = useState<boolean>(false);
  const [valid, setValid] = useState<boolean | null>(null);
  const [issues, setIssues] = useState<ValidationIssue[]>([]);
  const [status, setStatus] = useState<Status>({ kind: "info", text: "Loading draft…" });

  // The editor content as last saved/loaded — the baseline for dirty tracking.
  const cleanRef = useRef<string>("");

  useEffect(() => {
    let cancelled = false;
    setStatus({ kind: "info", text: "Loading draft…" });
    client
      .getDraft(sopId)
      .then((d) => {
        if (cancelled) return;
        setSource(d.source);
        cleanRef.current = d.source;
        setRevision(d.revision);
        setPublicationFailed(d.publication_failed);
        setInitialLoaded(true);
        setStatus({ kind: "info", text: "Loaded draft. Review it, then Save and Validate." });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        const notFound = e instanceof ApiClientError && e.status === 404;
        if (notFound) {
          cleanRef.current = "";
          setSource("");
          setInitialLoaded(true);
          setRevision(null);
          setStatus({ kind: "info", text: "No draft yet. Insert the template, then Save." });
        } else {
          setStatus({ kind: "error", text: `Could not load the draft — ${messageFor(e)}` });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [client, sopId]);

  const dirty = initialLoaded && source !== cleanRef.current;
  const busy = saving || validating || publishing;
  // Publish on a saved, clean draft with a server-assigned revision only (FR-010).
  const canPublish = isAuthor && initialLoaded && !dirty && revision !== null && !busy;

  const insertTemplate = useCallback(() => {
    setSource(TEMPLATE);
    setValid(null);
    setIssues([]);
    setStatus({ kind: "info", text: "Template inserted. Save it to persist; then Validate." });
  }, []);

  const save = useCallback(async () => {
    setSaving(true);
    setStatus({ kind: "info", text: "Saving…" });
    try {
      const res = await client.saveDraft(sopId, source);
      setSource(res.source);
      cleanRef.current = res.source;
      setRevision(res.revision);
      setPublicationFailed(false);
      setStatus({ kind: "success", text: `Saved. Revision ${res.revision}.` });
    } catch (e) {
      // Retain the editor content on a save failure (FR-010) — only the status updates.
      setStatus({ kind: "error", text: `Save failed — ${messageFor(e)}. Your text is preserved.` });
    } finally {
      setSaving(false);
    }
  }, [client, sopId, source]);

  const validate = useCallback(async () => {
    setValidating(true);
    setStatus({ kind: "info", text: "Validating…" });
    try {
      const res = await client.validate(source);
      setValid(res.valid);
      setIssues(res.issues);
      setStatus(
        res.valid
          ? { kind: "success", text: "Valid content. You can publish this draft." }
          : { kind: "error", text: `Invalid: ${res.issues.length} issue(s). Fix them before publishing.` },
      );
    } catch (e) {
      setValid(null);
      setIssues([]);
      setStatus({ kind: "error", text: `Validate failed — ${messageFor(e)}.` });
    } finally {
      setValidating(false);
    }
  }, [client, source]);

  const publish = useCallback(async () => {
    if (dirty || revision === null) return;
    setPublishing(true);
    setStatus({ kind: "info", text: "Publishing…" });
    try {
      const pub = await client.publish(sopId, revision);
      setPublicationFailed(false);
      setStatus({ kind: "success", text: `Published as version ${pub.version}.` });
    } catch (e) {
      setPublicationFailed(true);
      setStatus({
        kind: "error",
        text: `Publish failed — ${messageFor(e)}. The previously published version is unchanged.`,
      });
    } finally {
      setPublishing(false);
    }
  }, [client, sopId, dirty, revision]);

  const structural = useMemo(() => issues.filter((i) => i.stage === "structural"), [issues]);
  const semantic = useMemo(() => issues.filter((i) => i.stage === "semantic"), [issues]);

  const publishHint = !initialLoaded
    ? "loading…"
    : !isAuthor
      ? "consumers cannot author"
      : dirty
        ? "save your changes to publish"
        : revision === null
          ? "save a draft first"
          : "";

  const issueKey = (i: ValidationIssue) => `${i.code}|${i.path ?? ""}|${i.message}`;

  return (
    <section className="editor" aria-label={`Editor for ${sopId}`}>
      <button type="button" className="ghost" onClick={onBack}>
        &larr; Back
      </button>
      <h2>
        Edit SOP: <span className="sop-id">{sopId}</span>
      </h2>
      {!isAuthor && (
        <p role="status" className="error">
          You are acting as a reader (consumer), so authoring is disabled here.
        </p>
      )}

      <label className="field" htmlFor="sop-source">
        Source (front matter + the seven sections) <span className="muted">— saved as text; never executed</span>
        <textarea
          id="sop-source"
          value={source}
          spellCheck={false}
          placeholder="Insert the template or paste structured Markdown…"
          onChange={(e) => setSource(e.target.value)}
        />
      </label>

      <div className="editor-actions">
        <button type="button" className="ghost" onClick={insertTemplate} disabled={!isAuthor || busy || !initialLoaded}>
          Insert template
        </button>
        <button type="button" onClick={() => void save()} disabled={!isAuthor || busy || !initialLoaded}>
          {saving ? "Saving…" : "Save draft"}
        </button>
        <button type="button" onClick={() => void validate()} disabled={!isAuthor || busy || !initialLoaded}>
          {validating ? "Validating…" : "Validate"}
        </button>
        <button
          type="button"
          className="primary"
          onClick={() => void publish()}
          disabled={!canPublish}
          title={publishHint || undefined}
        >
          {publishing ? "Publishing…" : "Publish"}
        </button>
      </div>

      {dirty && (
        <p role="status" data-testid="unsaved" className="dirty">
          You have unsaved changes — save before publishing.
        </p>
      )}

      {initialLoaded && publicationFailed && (
        <p role="status" data-testid="pub-failed" className="error">
          A previous publish of this draft failed — the last published version remains current.
        </p>
      )}

      <p role="status" data-testid="editor-status" className={`status-${status.kind}`}>
        {status.text}
      </p>

      {valid === false && (
        <div className="issues" role="alert" aria-label="Validation issues">
          {structural.length > 0 && (
            <div>
              <h3>
                <span className="stage">structural</span> {structural.length}
              </h3>
              <ul>
                {structural.map((i) => (
                  <li key={issueKey(i)} className="issue">
                    <span className="code">{i.code}</span> {i.message}
                    {i.path && <span className="path"> — {i.path}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {semantic.length > 0 && (
            <div>
              <h3>
                <span className="stage">semantic</span> {semantic.length}
              </h3>
              <ul>
                {semantic.map((i) => (
                  <li key={issueKey(i)} className="issue">
                    <span className="code">{i.code}</span> {i.message}
                    {i.path && <span className="path"> — {i.path}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}

import type { Issue } from '../types';

/**
 * Renders validation issues grouped by stage (structural first, then
 * semantic) with readable messages and source paths (FR-034, NFR-050).
 * Text only — no HTML injection from issue fields.
 */
export default function IssuePanel({ issues }: { issues: Issue[] }) {
  const structural = issues.filter((i) => i.stage === 'structural');
  const semantic = issues.filter((i) => i.stage === 'semantic');

  if (issues.length === 0) {
    return <p role="status" className="ok">No issues — content is valid.</p>;
  }

  const group = (stage: Issue['stage'], list: Issue[]) =>
    list.length === 0 ? null : (
      <section key={stage}>
        <h4>{stage === 'structural' ? 'Structural problems' : 'Semantic problems'}</h4>
        <ul className="issues">
          {list.map((issue, idx) => (
            <li key={`${issue.code}-${idx}`} className="issue">
              <span className="issue-code">{issue.code}</span>
              <span className="issue-message">{issue.message}</span>
              <code className="issue-path">{issue.path}</code>
            </li>
          ))}
        </ul>
      </section>
    );

  return (
    <div role="alert" aria-label="Validation issues">
      <p className="bad">
        {issues.length} issue{issues.length === 1 ? '' : 's'} block publication. Fix them and validate again:
      </p>
      {group('structural', structural)}
      {group('semantic', semantic)}
    </div>
  );
}

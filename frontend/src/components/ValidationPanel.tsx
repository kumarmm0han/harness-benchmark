import type { Issue } from '../api/types';

export function ValidationPanel({ issues }: { issues: Issue[] }) {
  if (issues.length === 0) {
    return (
      <div className="toast ok" role="status" data-testid="validation-ok">
        No issues — content is valid.
      </div>
    );
  }
  const structural = issues.filter((i) => i.stage === 'structural');
  const semantic = issues.filter((i) => i.stage === 'semantic');
  return (
    <div role="alert" className="panel">
      <h3>{issues.length} issue(s) found</h3>
      {structural.length > 0 && (
        <section aria-label="Structural issues">
          <h4>Structural</h4>
          <ul>
            {structural.map((i, n) => (
              <li className="issue" key={`s-${n}`}>
                <label>[STRUCTURAL] {i.code}</label> — {i.message}
                {i.path ? <span className="muted"> (path: {i.path})</span> : null}
              </li>
            ))}
          </ul>
        </section>
      )}
      {semantic.length > 0 && (
        <section aria-label="Semantic issues">
          <h4>Semantic</h4>
          <ul>
            {semantic.map((i, n) => (
              <li className="issue" key={`m-${n}`}>
                <label>[SEMANTIC] {i.code}</label> — {i.message}
                {i.path ? <span className="muted"> (path: {i.path})</span> : null}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

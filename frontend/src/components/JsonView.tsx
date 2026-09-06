import type { Envelope } from '../api/types';

interface JsonViewProps {
  envelope: Envelope;
}

export function JsonView({ envelope }: JsonViewProps) {
  const text = (() => {
    try {
      return JSON.stringify(envelope, null, 2);
    } catch {
      return String(envelope);
    }
  })();
  return (
    <div className="panel" data-testid="json-view">
      <h2>Canonical JSON snapshot</h2>
      <pre className="json" data-testid="json-pre">{text}</pre>
    </div>
  );
}

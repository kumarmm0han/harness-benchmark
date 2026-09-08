// TASK-007 placeholder — the real human + JSON views (same snapshot, safe HTML-as-text,
// author version picker) are built in TASK-009. Kept as a real component so the app is
// navigable and testable in the meantime.
interface Props {
  sopId: string;
  onBack: () => void;
}

export default function SopDetail({ sopId, onBack }: Props) {
  return (
    <section aria-label="SOP detail">
      <button type="button" className="ghost" onClick={onBack}>
        ← Back to list
      </button>
      <h2>{sopId}</h2>
      <p className="empty">The human and JSON views are provided in the next step.</p>
    </section>
  );
}

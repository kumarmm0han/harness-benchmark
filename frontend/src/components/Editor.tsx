// TASK-007 placeholder — the full editor (insert template, save, validate, publish,
// unsaved-changes guard) is built in TASK-008. Kept as a real component so the app is
// navigable and testable in the meantime.
export default function Editor() {
  return (
    <section aria-label="Editor">
      <h2>Edit an SOP</h2>
      <p className="empty">The author editor is provided in the next step (save / validate / publish).</p>
    </section>
  );
}

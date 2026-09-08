import type { Identity } from "../types";

const IDENTITY_LABELS: Record<Identity, string> = {
  "demo-author": "demo-author (author)",
  "demo-consumer": "demo-consumer (reader)",
};

interface Props {
  value: Identity;
  onChange: (identity: Identity) => void;
}

/**
 * Demo identity selector (FR-001, NFR-050). Explicitly labeled as a demo-only, non-production
 * mechanism. Keyboard usable (native radio group; a labelled <select> alternative too).
 */
export default function IdentitySelector({ value, onChange }: Props) {
  return (
    <fieldset className="identity">
      <legend className="identity-legend">
        Who are you? <span className="muted">(demo only — local identities, not real accounts)</span>
      </legend>
      <label className="identity-option">
        <input
          type="radio"
          name="identity"
          value="demo-author"
          checked={value === "demo-author"}
          onChange={() => onChange("demo-author")}
        />
        {IDENTITY_LABELS["demo-author"]}
      </label>
      <label className="identity-option">
        <input
          type="radio"
          name="identity"
          value="demo-consumer"
          checked={value === "demo-consumer"}
          onChange={() => onChange("demo-consumer")}
        />
        {IDENTITY_LABELS["demo-consumer"]}
      </label>
    </fieldset>
  );
}

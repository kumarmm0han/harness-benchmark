import type { Identity } from '../api/types';
import { useIdentity } from '../state/IdentityContext';

export function IdentitySelector() {
  const { identity, setIdentity } = useIdentity();
  return (
    <div className="identity-box">
      <label htmlFor="identity-select">Identity (demo-only)</label>
      <select
        id="identity-select"
        value={identity}
        onChange={(e) => setIdentity(e.target.value as Identity)}
      >
        <option value="demo-author">demo-author</option>
        <option value="demo-consumer">demo-consumer</option>
      </select>
      <strong>Current: {identity}</strong>
    </div>
  );
}

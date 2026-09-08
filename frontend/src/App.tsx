// App shell (DES-013). State-based navigation, no router dependency: three views
// (list / editor / detail), the current demo identity, and a single shared API client.
import { useEffect, useMemo, useState } from "react";
import { ApiClient } from "./api";
import type { Identity } from "./types";
import IdentitySelector from "./components/IdentitySelector";
import SopList from "./components/SopList";
import Editor from "./components/Editor";
import SopDetail from "./components/SopDetail";

type Route =
  | { kind: "list" }
  | { kind: "editor"; sopId: string }
  | { kind: "detail"; sopId: string };

export default function App() {
  // One client for the whole app; the identity is swapped in place when the user
  // changes it (DES-013), so every subsequent request carries the new X-Demo-User.
  const client = useMemo(() => new ApiClient(), []);
  const [identity, setIdentity] = useState<Identity>("demo-author");
  const [route, setRoute] = useState<Route>({ kind: "list" });

  useEffect(() => {
    client.setIdentity(identity);
  }, [client, identity]);

  const selectIdentity = (next: Identity) => {
    setIdentity(next);
    // Role controls differ by identity; always land on the safe list view.
    setRoute({ kind: "list" });
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>SOP Demo</h1>
        <p className="muted">
          Author structured Markdown, validate it, publish an immutable version, then read the
          published result in human and JSON views. Identities are local demo values, and
          authored rules are data &mdash; they are validated and displayed, never executed.
        </p>
        <IdentitySelector value={identity} onChange={selectIdentity} />
      </header>

      <main>
        {route.kind === "list" && (
          <SopList
            identity={identity}
            client={client}
            onOpenSop={(sopId) => setRoute({ kind: "detail", sopId })}
            onOpenDraft={(sopId) => setRoute({ kind: "editor", sopId })}
          />
        )}
        {route.kind === "editor" && <Editor />}
        {route.kind === "detail" && (
          <SopDetail sopId={route.sopId} onBack={() => setRoute({ kind: "list" })} />
        )}
      </main>
    </div>
  );
}

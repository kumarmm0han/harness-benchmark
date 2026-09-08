import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../api";
import type { ApiClient } from "../api";
import SopDetail from "./SopDetail";

const SOP_ID = "BILL-REFUND-001";

// A canonical published snapshot (spec.md §7 shape) with a hostile <script> string in an
// action description, to prove the UI renders authored markup as text, not as HTML.
function publishedFixture(): unknown {
  return {
    sop_id: SOP_ID,
    version: 1,
    published_at: "2026-01-01T12:00:00Z",
    content: {
      sop_id: SOP_ID,
      title: "Refund for Duplicate Charge",
      owner_team: "Billing Operations",
      domain: "Billing",
      intent: "refund_duplicate_charge",
      risk_level: "medium",
      max_autonomy: "assist",
      policy: {
        use_when: ["Customer reports a duplicate charge."],
        do_not_use_when: ["Fraud is suspected."],
      },
      inputs: [
        { name: "refund_amount", type: "number" },
        { name: "duplicate_confirmed", type: "boolean" },
      ],
      rules: [{ id: "R1", conditions: [{ input: "duplicate_confirmed", op: "eq", value: true }], action_ids: ["A1"] }],
      actions: [
        {
          id: "A1",
          kind: "refund",
          description: "You can <script>alert(1)</script> be refunded within the limit.",
          max_amount: 200,
        },
      ],
      boundaries: { escalation: [{ action_id: "A1", input: "refund_amount", op: "gt", amount: 200, target_action_id: "A2" }] },
      customer_messages: {
        primary: "A representative can review the confirmed duplicate charge for a refund.",
        escalation: "This request needs additional review because it exceeds the refund limit.",
      },
    },
  };
}

function makeClient(opts: { published?: unknown; notFound?: boolean } = {}): ApiClient {
  const getSop = opts.notFound
    ? vi.fn().mockRejectedValue(new ApiClientError(404, "NOT_FOUND", "no published SOP for this sop_id", []))
    : vi.fn().mockResolvedValue(opts.published ?? publishedFixture());
  return { getSop } as unknown as ApiClient;
}

function renderDetail(client: ApiClient) {
  return render(<SopDetail sopId={SOP_ID} client={client} onBack={() => {}} />);
}

async function loaded(container: HTMLElement) {
  await waitFor(() => expect(container.textContent).toContain("Version: 1"));
}

describe("SopDetail", () => {
  it("shows SOP identity and version in the human view", async () => {
    const { container } = renderDetail(makeClient());
    await loaded(container);
    expect(container.textContent).toContain(SOP_ID);
    expect(container.textContent).toContain("Version: 1");
    expect(container.textContent).toContain("Refund for Duplicate Charge");
    expect(container.textContent).toContain("Billing");
  });

  it("renders authored <script> strings as literal text, not executed", async () => {
    const { container } = renderDetail(makeClient());
    await loaded(container);
    // Present as literal text...
    expect(container.textContent).toContain("<script>alert(1)</script>");
    // ...and no actual <script> element was injected (i.e. it was not executed).
    expect(container.querySelectorAll("script")).toHaveLength(0);
  });

  it("exposes identical identity/version/policy in the human and JSON views", async () => {
    const { container } = renderDetail(makeClient());
    await loaded(container);
    // human
    expect(container.textContent).toContain(SOP_ID);
    expect(container.textContent).toContain("Refund for Duplicate Charge");

    // switch to JSON (same fetched snapshot)
    fireEvent.click(screen.getByRole("button", { name: /^json$/i }));
    await waitFor(() => {
      expect(container.textContent).toContain(`"sop_id": "${SOP_ID}"`);
      expect(container.textContent).toContain('"version": 1');
      expect(container.textContent).toContain("Refund for Duplicate Charge");
    });
    // identical identity + version across both views (FR-053)
    expect(container.textContent).toContain('"sop_id": "BILL-REFUND-001"');
    expect(container.textContent).toContain('"version": 1');
  });

  it("shows a clear message when nothing is published (404)", async () => {
    const { container } = renderDetail(makeClient({ notFound: true }));
    await waitFor(() => expect(container.textContent).toMatch(/No published version/i));
  });
});

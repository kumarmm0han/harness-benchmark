import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiClientError } from "../api";
import type { ApiClient } from "../api";
import type { DraftDetail, Identity, ValidationIssue } from "../types";
import { TEMPLATE } from "../template";
import Editor from "./Editor";

const SOP_ID = "BILL-REFUND-001";

type MockFn = ReturnType<typeof vi.fn>;
type Mocks = { getDraft: MockFn; saveDraft: MockFn; validate: MockFn; publish: MockFn };

function makeClient(opts: {
  draft?: DraftDetail | null;
  saveError?: unknown;
  validate?: { valid: boolean; issues: ValidationIssue[]; content: null };
  publish?: unknown;
} = {}): { client: ApiClient; mocks: Mocks } {
  const draft = opts.draft ?? {
    sop_id: SOP_ID,
    revision: 1,
    publication_failed: false,
    updated_at: "2026-01-01T00:00:00Z",
    source: "existing draft text",
  };
  const getDraft: MockFn =
    draft === null
      ? vi.fn().mockRejectedValue(new ApiClientError(404, "NOT_FOUND", "no draft for this sop_id", []))
      : vi.fn().mockResolvedValue(draft);

  const saveDraft: MockFn =
    opts.saveError != null
      ? vi.fn().mockRejectedValue(opts.saveError)
      : vi.fn().mockImplementation(async (_id: string, source: string) => ({ sop_id: SOP_ID, revision: 99, source }));

  const validate: MockFn = vi
    .fn()
    .mockResolvedValue(opts.validate ?? { valid: true, issues: [], content: null });

  const publish: MockFn = vi
    .fn()
    .mockResolvedValue(opts.publish ?? { sop_id: SOP_ID, version: 2, published_at: "2026-01-01T00:00:00Z", content: {} });

  // Only the four editor methods are exercised; cast the fake to the full ApiClient type.
  const client = { getDraft, saveDraft, validate, publish } as unknown as ApiClient;
  return { client, mocks: { getDraft, saveDraft, validate, publish } };
}

function renderEditor(client: ApiClient, identity: Identity = "demo-author") {
  render(<Editor sopId={SOP_ID} client={client} identity={identity} onBack={() => {}} />);
}

async function loadDone() {
  await screen.findByText(/Loaded draft\.|No draft yet\./);
}

describe("Editor", () => {
  it("inserts the exact spec.md §3 template", async () => {
    const { client } = makeClient({ draft: { sop_id: SOP_ID, revision: 1, publication_failed: false, updated_at: "u", source: "old" } });
    renderEditor(client);
    await loadDone();
    fireEvent.click(screen.getByRole("button", { name: /insert template/i }));
    await waitFor(() => expect(screen.getByRole("textbox")).toHaveValue(TEMPLATE));
  });

  it("blocks publishing while there are unsaved changes, and re-enables after save", async () => {
    const { client, mocks } = makeClient({ draft: { sop_id: SOP_ID, revision: 1, publication_failed: false, updated_at: "u", source: "clean source" } });
    renderEditor(client);
    await loadDone();
    const publish = () => screen.getByRole("button", { name: /^publish$/i });
    expect(publish()).toBeEnabled();

    fireEvent.change(screen.getByRole("textbox"), { target: { value: "clean source CHANGED" } });
    await screen.findByTestId("unsaved");
    expect(publish()).toBeDisabled();

    fireEvent.click(screen.getByRole("button", { name: /save draft/i }));
    await screen.findByText(/Saved\. Revision 99/);
    expect(mocks.saveDraft).toHaveBeenCalledWith(SOP_ID, "clean source CHANGED");
    expect(publish()).toBeEnabled();
  });

  it("retains editor content when a save fails", async () => {
    const { client } = makeClient({
      draft: { sop_id: SOP_ID, revision: 1, publication_failed: false, updated_at: "u", source: "original" },
      saveError: new ApiClientError(400, "MALFORMED", "missing source", []),
    });
    renderEditor(client);
    await loadDone();
    fireEvent.click(screen.getByRole("button", { name: /insert template/i }));
    expect(screen.getByRole("textbox")).toHaveValue(TEMPLATE);
    fireEvent.click(screen.getByRole("button", { name: /save draft/i }));
    await screen.findByText(/save failed/i);
    // Content is preserved, not discarded on a save error (FR-010).
    expect(screen.getByRole("textbox")).toHaveValue(TEMPLATE);
  });

  it("shows structural and semantic validation issues with their paths", async () => {
    const { client } = makeClient({
      draft: { sop_id: SOP_ID, revision: 1, publication_failed: false, updated_at: "u", source: "s" },
      validate: {
        valid: false,
        issues: [
          { code: "MISSING_FIELD", stage: "structural", message: "title is required", path: "frontmatter.title" },
          { code: "FINANCIAL_MISSING_LIMIT", stage: "semantic", message: "refund limit is required", path: "actions[0]" },
        ],
        content: null,
      },
    });
    renderEditor(client);
    await loadDone();
    fireEvent.click(screen.getByRole("button", { name: /validate/i }));
    await screen.findByRole("alert", { name: /validation issues/i });
    expect(screen.getByText("structural")).toBeInTheDocument();
    expect(screen.getByText("semantic")).toBeInTheDocument();
    expect(screen.getByText("MISSING_FIELD")).toBeInTheDocument();
    expect(screen.getByText(/frontmatter\.title/)).toBeInTheDocument();
    expect(screen.getByText("FINANCIAL_MISSING_LIMIT")).toBeInTheDocument();
    expect(screen.getByText(/actions\[0\]/)).toBeInTheDocument();
  });

  it("publishes by the saved draft revision", async () => {
    const { client, mocks } = makeClient({
      draft: { sop_id: SOP_ID, revision: 7, publication_failed: false, updated_at: "u", source: "clean" },
      publish: { sop_id: SOP_ID, version: 3, published_at: "2026-01-01T00:00:00Z", content: {} },
    });
    renderEditor(client);
    await loadDone();
    fireEvent.click(screen.getByRole("button", { name: /^publish$/i }));
    await screen.findByText(/published as version 3/i);
    expect(mocks.publish).toHaveBeenCalledWith(SOP_ID, 7);
  });

  it("disables author actions when acting as the consumer", async () => {
    const { client, mocks } = makeClient();
    renderEditor(client, "demo-consumer");
    await loadDone();
    expect(screen.getByRole("button", { name: /save draft/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /validate/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /^publish$/i })).toBeDisabled();
    // No author actions are triggered for a consumer.
    expect(mocks.saveDraft).not.toHaveBeenCalled();
    expect(mocks.publish).not.toHaveBeenCalled();
  });

  it("surfaces a failed publish as text without discarding the draft", async () => {
    const { client, mocks } = makeClient({
      draft: { sop_id: SOP_ID, revision: 2, publication_failed: false, updated_at: "u", source: "clean" },
    });
    (mocks.publish as MockFn).mockRejectedValueOnce(
      new ApiClientError(409, "STALE_REVISION", "revision does not match the saved draft", []),
    );
    renderEditor(client);
    await loadDone();
    fireEvent.click(screen.getByRole("button", { name: /^publish$/i }));
    await screen.findByText(/publish failed/i);
    // Draft text is preserved; the failure is reported to the author.
    expect(screen.getByRole("textbox")).toHaveValue("clean");
    expect(mocks.publish).toHaveBeenCalledTimes(1);
  });
});

import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiClient, ApiClientError } from "../api";
import type { DraftSummary, SopSummary } from "../types";
import SopList from "./SopList";

type Mocks = { listSops: ReturnType<typeof vi.fn>; listDrafts: ReturnType<typeof vi.fn> };

function makeClient(opts: {
  listSops?: { value?: SopSummary[]; error?: ApiClientError };
  listDrafts?: DraftSummary[];
} = {}): { client: ApiClient; mocks: Mocks } {
  const listSops = opts.listSops?.error
    ? vi.fn().mockRejectedValue(opts.listSops.error)
    : vi.fn().mockResolvedValue(opts.listSops?.value ?? []);
  const listDrafts = vi.fn().mockResolvedValue(opts.listDrafts ?? []);
  const client = { listSops, listDrafts } as unknown as ApiClient;
  return { client, mocks: { listSops, listDrafts } };
}

function renderList(client: ApiClient, identity: "demo-author" | "demo-consumer") {
  const onOpenSop = vi.fn();
  const onOpenDraft = vi.fn();
  render(<SopList identity={identity} client={client} onOpenSop={onOpenSop} onOpenDraft={onOpenDraft} />);
  return { onOpenSop, onOpenDraft };
}

const oneSop: SopSummary = {
  sop_id: "BILL-REFUND-001",
  title: "Refund for Duplicate Charge",
  version: 1,
  domain: "Billing",
  risk: "medium",
};
const oneDraft: DraftSummary = {
  sop_id: "BILL-REFUND-001",
  revision: 2,
  publication_failed: false,
  updated_at: "2026-01-01T00:00:00Z",
};

describe("SopList", () => {
  it("shows a useful empty state when nothing matches", async () => {
    const { client } = makeClient();
    renderList(client, "demo-author");
    expect(await screen.findByTestId("empty-state")).toHaveTextContent(/no published sops match/i);
  });

  it("wires domain filters into the catalog request", async () => {
    const { client, mocks } = makeClient({ listSops: { value: [oneSop] } });
    renderList(client, "demo-author");
    await screen.findByText("BILL-REFUND-001");

    const selects = screen.getAllByRole("combobox");
    await act(async () => {
      fireEvent.change(selects[0], { target: { value: "Support" } });
    });
    await waitFor(() => expect(mocks.listSops).toHaveBeenLastCalledWith({ domain: "Support", risk: undefined }));
  });

  it("combines domain and risk with AND semantics", async () => {
    const { client, mocks } = makeClient({ listSops: { value: [oneSop] } });
    renderList(client, "demo-author");
    await screen.findByText("BILL-REFUND-001");

    const selects = screen.getAllByRole("combobox");
    await act(async () => {
      fireEvent.change(selects[0], { target: { value: "Billing" } });
    });
    await act(async () => {
      fireEvent.change(selects[1], { target: { value: "medium" } });
    });
    await waitFor(() => expect(mocks.listSops).toHaveBeenLastCalledWith({ domain: "Billing", risk: "medium" }));
  });

  it("surfaces API errors as text, not color alone", async () => {
    const { client } = makeClient({
      listSops: { error: new ApiClientError(400, "INVALID_FILTER", "invalid domain filter: Hacking", []) },
    });
    renderList(client, "demo-author");
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/invalid domain filter: Hacking/i);
  });

  it("shows the author-only draft list to the author (with the failure flag)", async () => {
    const { client, mocks } = makeClient({
      listSops: { value: [oneSop] },
      listDrafts: [{ ...oneDraft, publication_failed: true }],
    });
    renderList(client, "demo-author");
    await screen.findByText(/rev 2/);
    expect(mocks.listDrafts).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("heading", { name: /your drafts/i })).toBeInTheDocument();
    expect(screen.getByText(/publishing failed/i)).toBeInTheDocument();
  });

  it("lets the consumer see the catalog but no draft controls at all", async () => {
    const { client, mocks } = makeClient({ listSops: { value: [oneSop] }, listDrafts: [oneDraft] });
    const { onOpenDraft } = renderList(client, "demo-consumer");
    expect(await screen.findByText("BILL-REFUND-001")).toBeInTheDocument();
    expect(mocks.listDrafts).not.toHaveBeenCalled();
    expect(screen.queryByRole("heading", { name: /your drafts/i })).not.toBeInTheDocument();
    expect(onOpenDraft).not.toHaveBeenCalled();
  });

  it("uses native, keyboard-operable controls to open a SOP or a draft", async () => {
    const { client } = makeClient({ listSops: { value: [oneSop] }, listDrafts: [oneDraft] });
    const { onOpenSop, onOpenDraft } = renderList(client, "demo-author");
    await screen.findByText(/rev 2/);
    const container = screen.getByRole("region", { name: /sop catalog and drafts/i });

    const rows = within(container).getAllByRole("listitem");
    const sopRow = rows.find((li) => li.textContent?.includes("Refund for Duplicate Charge"));
    expect(sopRow).toBeDefined();
    const openSop = within(sopRow as HTMLElement).getByRole("button", { name: /open/i });
    expect(openSop.tagName).toBe("BUTTON");
    openSop.focus();
    expect(openSop).toHaveFocus();
    fireEvent.click(openSop);
    expect(onOpenSop).toHaveBeenCalledWith("BILL-REFUND-001");

    const draftRow = rows.find((li) => li.textContent?.includes("rev 2"));
    expect(draftRow).toBeDefined();
    fireEvent.click(within(draftRow as HTMLElement).getByRole("button", { name: /open/i }));
    expect(onOpenDraft).toHaveBeenCalledWith("BILL-REFUND-001");
  });
});

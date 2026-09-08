import { describe, expect, it, vi } from "vitest";
import { ApiClient, ApiClientError } from "./api";

type Init = { method?: string; headers?: Record<string, string>; body?: string };

function respond(status: number, body: unknown): Response {
  return new Response(body === null ? "" : JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

// The ApiClient passes (url, init) to the fetcher; return the init object of a recorded call.
function callInit(call: unknown): Init {
  return (call as [string, Init])[1];
}

async function catchApiError(p: Promise<unknown>): Promise<ApiClientError> {
  try {
    await p;
  } catch (err) {
    expect(err).toBeInstanceOf(ApiClientError);
    return err as ApiClientError;
  }
  throw new Error("expected the request to reject");
}

describe("ApiClient", () => {
  it("sends the current identity as X-Demo-User on every request", async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(respond(200, { items: [] })));
    const client = new ApiClient({ identity: "demo-author", fetcher });
    await client.listDrafts();
    expect(callInit(fetcher.mock.calls[0]).headers?.["X-Demo-User"]).toBe("demo-author");

    client.setIdentity("demo-consumer");
    await client.listDrafts();
    expect(callInit(fetcher.mock.calls[1]).headers?.["X-Demo-User"]).toBe("demo-consumer");
  });

  it("normalizes a non-2xx body into the documented {code,message,issues} envelope", async () => {
    const fetcher = vi
      .fn()
      .mockResolvedValue(
        respond(400, { code: "INVALID_FILTER", message: "invalid domain filter: Hacking", issues: [] }),
      );
    const client = new ApiClient({ identity: "demo-consumer", fetcher });

    const e = await catchApiError(client.listSops({ domain: "Hacking" }));
    expect(e.status).toBe(400);
    expect(e.code).toBe("INVALID_FILTER");
    expect(e.message).toBe("invalid domain filter: Hacking");
    expect(e.issues).toEqual([]);
  });

  it("falls back to a status-based message when the body has no message", async () => {
    const fetcher = vi.fn().mockResolvedValue(respond(413, { code: "SOURCE_TOO_LARGE", issues: [] }));
    const client = new ApiClient({ fetcher });
    const e = await catchApiError(client.saveDraft("BILL-REFUND-001", "x"));
    expect(e.status).toBe(413);
    expect(e.code).toBe("SOURCE_TOO_LARGE");
    expect(e.message).toContain("413");
    expect(e.issues).toEqual([]);
  });

  it("resolves 200 response bodies and omits the body on GET", async () => {
    const content = { sop_id: "BILL-REFUND-001", version: 1, content: {} };
    const fetcher = vi.fn().mockResolvedValue(respond(200, content));
    const client = new ApiClient({ fetcher });
    await expect(client.getSop("BILL-REFUND-001")).resolves.toEqual(content);

    const init = callInit(fetcher.mock.calls[0]);
    expect(init.method).toBe("GET");
    expect(init.body).toBeUndefined();
    expect(init.headers?.["Content-Type"]).toBe("application/json");
  });
});

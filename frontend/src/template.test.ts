import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import path from "node:path";
import { TEMPLATE } from "./template";

// Extract the exact template block from spec.md §3 so the editor's inserted text stays
// byte-identical to the frozen spec (FR-010, PRN-001).
function specTemplate(): string {
  const spec = readFileSync(path.resolve(process.cwd(), "..", "spec.md"), "utf8");
  const marker = "````markdown\n";
  const start = spec.indexOf(marker);
  expect(start, "spec.md §3 template fence not found").toBeGreaterThanOrEqual(0);
  const bodyStart = start + marker.length;
  const end = spec.indexOf("\n````", bodyStart);
  expect(end, "spec.md §3 template closing fence not found").toBeGreaterThanOrEqual(0);
  return spec.slice(bodyStart, end);
}

describe("TEMPLATE (spec.md §3 guard)", () => {
  it("is byte-identical to the spec.md §3 valid template", () => {
    expect(TEMPLATE.trim()).toBe(specTemplate().trim());
  });

  it("carries the canonical front matter for the duplicate-charge example", () => {
    expect(TEMPLATE).toContain("sop_id: BILL-REFUND-001");
    expect(TEMPLATE).toContain("domain: Billing");
    expect(TEMPLATE).toContain("intent: refund_duplicate_charge");
    expect(TEMPLATE).toContain("risk_level: medium");
    expect(TEMPLATE).toContain("max_autonomy: assist");
  });
});

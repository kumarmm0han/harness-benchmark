import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Vitest runs with globals disabled, so @testing-library/react's automatic cleanup
// (which depends on a global afterEach) does not register on its own — reset the DOM
// between tests explicitly so rendered components never leak into the next test.
afterEach(() => {
  cleanup();
});

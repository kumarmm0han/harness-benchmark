import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import IdentitySelector from "./IdentitySelector";

describe("IdentitySelector", () => {
  it("is explicitly labeled as a demo-only, non-production identity", () => {
    render(<IdentitySelector value="demo-author" onChange={() => {}} />);
    expect(screen.getByText(/demo only/i)).toBeInTheDocument();
    expect(screen.getByText(/not real accounts/i)).toBeInTheDocument();
  });

  it("shows both demo identities as native (keyboard-usable) radios", () => {
    render(<IdentitySelector value="demo-author" onChange={() => {}} />);
    const radios = screen.getAllByRole("radio");
    expect(radios).toHaveLength(2);
    for (const r of radios) {
      expect(r).toHaveAttribute("name", "identity");
    }
    // The selected identity reflects in the checked state.
    expect(screen.getByRole("radio", { name: /demo-author/ })).toBeChecked();
    expect(screen.getByRole("radio", { name: /demo-consumer/ })).not.toBeChecked();
  });

  it("notifies onChange with the selected identity", () => {
    const onChange = vi.fn();
    render(<IdentitySelector value="demo-consumer" onChange={onChange} />);
    fireEvent.click(screen.getByRole("radio", { name: /demo-author/ }));
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith("demo-author");
  });
});

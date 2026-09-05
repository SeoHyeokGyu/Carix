import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SessionPending } from "./SessionPending";

describe("SessionPending", () => {
  it("renders pending message with status role", () => {
    render(<SessionPending />);

    const statusEl = screen.getByRole("status");
    expect(statusEl).toBeInTheDocument();
    expect(statusEl).toHaveTextContent("불러오는 중…");
  });
});

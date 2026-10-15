import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SessionPending } from "./SessionPending";

describe("SessionPending", () => {
  it("status 역할과 함께 로딩 중 안내 문구를 렌더링한다", () => {
    render(<SessionPending />);

    const statusEl = screen.getByRole("status");
    expect(statusEl).toBeInTheDocument();
    expect(statusEl).toHaveTextContent("불러오는 중…");
  });
});

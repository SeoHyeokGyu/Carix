import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { describe, expect, it } from "vitest";

import RecordsLayout from "./layout";

describe("이력 관리 탭 레이아웃 (S-12)", () => {
  it("탭 3개를 그린다", async () => {
    const Stub = createRoutesStub([
      {
        path: "/mypage/records",
        Component: RecordsLayout,
        children: [{ index: true, Component: () => <p>소득 본문</p> }],
      },
    ]);
    render(<Stub initialEntries={["/mypage/records"]} />);

    expect(
      await screen.findByRole("link", { name: "소득" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "소비" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "스킬" })).toBeInTheDocument();
  });
});

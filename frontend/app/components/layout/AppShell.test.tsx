import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  const items = [
    { to: "/dashboard", label: "지표", end: true },
    { to: "/dashboard/history", label: "이력" },
  ];

  it("내비게이션 항목들을 렌더링하고 활성 탭 스타일을 올바르게 적용한다", () => {
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route element={<AppShell navLabel="대시보드" items={items} />}>
            <Route path="/dashboard" element={<div>지표 페이지</div>} />
            <Route path="/dashboard/history" element={<div>이력 페이지</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const nav = screen.getByRole("navigation", { name: "대시보드" });
    expect(nav).toBeInTheDocument();

    const activeLink = screen.getByRole("link", { name: "지표" });
    const inactiveLink = screen.getByRole("link", { name: "이력" });

    expect(activeLink).toHaveClass("bg-carix-info/10");
    expect(inactiveLink).toHaveClass("text-carix-text/70");
    expect(screen.getByText("지표 페이지")).toBeInTheDocument();
  });
});

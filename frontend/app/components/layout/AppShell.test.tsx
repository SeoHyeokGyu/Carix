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

  it("브랜드 로고 링크와 우측 유틸리티 아이템(설정 등)을 정상 렌더링한다", () => {
    const utilityItems = [{ to: "/mypage/profile", label: "설정", icon: "⚙️" }];

    render(
      <MemoryRouter initialEntries={["/mypage/profile"]}>
        <Routes>
          <Route
            element={
              <AppShell
                navLabel="대시보드"
                items={items}
                utilityItems={utilityItems}
              />
            }
          >
            <Route path="/dashboard" element={<div>지표 페이지</div>} />
            <Route path="/mypage/profile" element={<div>설정 페이지</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    // 브랜드 로고 링크가 /dashboard 로 연결되는지 확인
    const logoLink = screen.getByRole("link", { name: "Carix" });
    expect(logoLink).toBeInTheDocument();
    expect(logoLink).toHaveAttribute("href", "/dashboard");

    // 주 내비게이션 항목들이 설정 페이지에서도 그대로 노출되는지 확인
    expect(screen.getByRole("link", { name: "지표" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "이력" })).toBeInTheDocument();

    // 우측 유틸리티 설정 링크가 활성화(Active) 상태인지 확인
    const settingsLink = screen.getByRole("link", { name: /설정/ });
    expect(settingsLink).toBeInTheDocument();
    expect(settingsLink).toHaveClass("bg-carix-info/10");
  });
});

import { render, screen, within } from "@testing-library/react";
import { CircleHelp } from "lucide-react";
import { createRoutesStub, MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";

import { MAIN_NAV_ITEMS } from "../../routes/dashboard/layout";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  const items = [
    { to: "/dashboard", label: "판정", end: true },
    { to: "/dashboard/history", label: "지수 기록" },
  ];

  it("내비게이션 항목들을 렌더링하고 활성 탭 스타일을 올바르게 적용한다", () => {
    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route element={<AppShell navLabel="대시보드" items={items} />}>
            <Route path="/dashboard" element={<div>판정 페이지</div>} />
            <Route
              path="/dashboard/history"
              element={<div>지수 기록 페이지</div>}
            />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    const nav = screen.getByRole("navigation", { name: "대시보드" });
    expect(nav).toBeInTheDocument();

    const activeLink = screen.getByRole("link", { name: "판정" });
    const inactiveLink = screen.getByRole("link", { name: "지수 기록" });

    expect(activeLink).toHaveClass("bg-carix-info/10");
    expect(inactiveLink).toHaveClass("text-carix-text/70");
    expect(screen.getByText("판정 페이지")).toBeInTheDocument();
  });

  it("브랜드 로고 링크와 우측 유틸리티 아이템(도움말)을 정상 렌더링한다", () => {
    const utilityItems = [
      { to: "/dashboard/guide", label: "도움말", icon: CircleHelp },
    ];

    render(
      <MemoryRouter initialEntries={["/dashboard/guide"]}>
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
            <Route path="/dashboard" element={<div>판정 페이지</div>} />
            <Route path="/dashboard/guide" element={<div>도움말 페이지</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    // 브랜드 로고 링크가 /dashboard 로 연결되는지 확인
    const logoLink = screen.getByRole("link", { name: "Carix" });
    expect(logoLink).toBeInTheDocument();
    expect(logoLink).toHaveAttribute("href", "/dashboard");

    // 주 내비게이션 항목들이 도움말 페이지에서도 그대로 노출되는지 확인
    expect(screen.getByRole("link", { name: "판정" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "지수 기록" })).toBeInTheDocument();

    // 우측 유틸리티 도움말 링크가 활성화(Active) 상태인지 확인
    const helpLink = screen.getByRole("link", { name: "도움말" });
    expect(helpLink).toHaveClass("bg-carix-info/10");
  });

  it("accountMenu 를 켜야 계정 메뉴 트리거가 나온다", async () => {
    // 계정 메뉴는 useSubmit 을 써서 데이터 라우터 안에서만 그려진다(앱과 같은 조건).
    const Stub = createRoutesStub([
      {
        path: "/onboarding",
        Component: () => <AppShell navLabel="온보딩" items={[]} />,
      },
      {
        path: "/dashboard",
        Component: () => (
          <AppShell navLabel="대시보드" items={items} accountMenu />
        ),
      },
    ]);

    const { unmount } = render(<Stub initialEntries={["/onboarding"]} />);
    await screen.findByRole("link", { name: "Carix" });
    expect(
      screen.queryByRole("button", { name: "내 정보" }),
    ).not.toBeInTheDocument();
    unmount();

    render(<Stub initialEntries={["/dashboard"]} />);
    expect(
      await screen.findByRole("button", { name: "내 정보" }),
    ).toBeInTheDocument();
  });
  describe("하위 화면 선택(match)", () => {
    // 실제 주 메뉴 정의를 쓴다 — 「판정」의 match 가 빠지면 지난 판정 화면에서 선택이 사라진다.
    const sectionItems = MAIN_NAV_ITEMS;

    function renderAt(path: string) {
      render(
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              element={<AppShell navLabel="대시보드" items={sectionItems} />}
            >
              <Route path="/dashboard" element={<div>판정</div>} />
              <Route path="/dashboard/history" element={<div>지난 판정</div>} />
              <Route
                path="/dashboard/historyx"
                element={<div>다른 화면</div>}
              />
              <Route path="/dashboard/trends" element={<div>추이</div>} />
            </Route>
          </Routes>
        </MemoryRouter>,
      );
      const nav = screen.getByRole("navigation", { name: "대시보드" });
      return {
        verdict: within(nav).getByRole("link", { name: "판정" }),
        trends: within(nav).getByRole("link", { name: "추이 분석" }),
      };
    }

    it("하위 화면에서 상위 항목을 선택으로 보이고 aria-current=true 를 단다", () => {
      const { verdict, trends } = renderAt("/dashboard/history");

      expect(verdict).toHaveAttribute("aria-current", "true");
      expect(verdict).toHaveClass("bg-carix-info/10");
      expect(verdict).toHaveAttribute("href", "/dashboard");
      expect(trends).not.toHaveAttribute("aria-current");
      expect(trends).toHaveClass("text-carix-text/70");
    });

    it("상위 화면 자체에서는 aria-current=page 다", () => {
      const { verdict } = renderAt("/dashboard");

      expect(verdict).toHaveAttribute("aria-current", "page");
      expect(verdict).toHaveClass("bg-carix-info/10");
    });

    it("경로 접두만 같은 화면은 하위 화면이 아니다", () => {
      const { verdict } = renderAt("/dashboard/historyx");

      expect(verdict).not.toHaveAttribute("aria-current");
      expect(verdict).toHaveClass("text-carix-text/70");
    });
  });
});

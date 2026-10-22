import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { describe, expect, it } from "vitest";

import { MAIN_NAV_ITEMS } from "../../routes/dashboard/layout";
import { AppShell } from "./AppShell";

describe("AppShell", () => {
  describe("하위 화면 선택(match)", () => {
    // 실제 주 메뉴 정의를 쓴다 — 「대시보드」의 match 가 빠지면 지난 판정 화면에서 선택이 사라진다.
    const sectionItems = MAIN_NAV_ITEMS;

    function renderAt(path: string) {
      render(
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              element={<AppShell navLabel="주 메뉴" items={sectionItems} />}
            >
              <Route path="/dashboard" element={<div>대시보드 화면</div>} />
              <Route path="/dashboard/history" element={<div>지난 판정</div>} />
              <Route
                path="/dashboard/historyx"
                element={<div>다른 화면</div>}
              />
              <Route path="/dashboard/trends" element={<div>추이</div>} />
              <Route path="/dashboard/skills" element={<div>스킬 분석</div>} />
            </Route>
          </Routes>
        </MemoryRouter>,
      );
      const nav = screen.getByRole("navigation", { name: "주 메뉴" });
      return {
        home: within(nav).getByRole("link", { name: "대시보드" }),
        trends: within(nav).getByRole("link", { name: "추이 분석" }),
        skills: within(nav).getByRole("link", { name: "스킬 분석" }),
      };
    }

    it("하위 화면에서 상위 항목을 선택으로 보이고 aria-current=true 를 단다", () => {
      const { home, trends, skills } = renderAt("/dashboard/history");

      expect(home).toHaveAttribute("aria-current", "true");
      expect(home).toHaveClass("bg-carix-info/10");
      expect(home).toHaveAttribute("href", "/dashboard");
      expect(trends).not.toHaveAttribute("aria-current");
      expect(trends).toHaveClass("text-carix-text/70");
      expect(skills).not.toHaveAttribute("aria-current");
      expect(skills).toHaveClass("text-carix-text/70");
    });

    it("경로 접두만 같은 화면은 하위 화면이 아니다", () => {
      const { home } = renderAt("/dashboard/historyx");

      expect(home).not.toHaveAttribute("aria-current");
      expect(home).toHaveClass("text-carix-text/70");
    });
  });
});

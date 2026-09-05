import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as SessionProviderModule from "../../lib/SessionProvider";
import type { Session } from "../../lib/session";
import DashboardHistory from "./history";
import DashboardHome from "./index";
import DashboardLayout from "./layout";
import DashboardSimulator from "./simulator";
import DashboardTrends from "./trends";

vi.mock("../../lib/SessionProvider", () => ({
  useSession: vi.fn(),
}));

describe("대시보드 라우트 및 레이아웃 (Dashboard)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("개별 화면 (Pages)", () => {
    it("DashboardHome(지표 메인) 화면을 정상 렌더링한다", () => {
      render(<DashboardHome />);
      expect(screen.getByText("S-10")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "대시보드" }),
      ).toBeInTheDocument();
    });

    it("DashboardHistory(월별 이력) 화면을 정상 렌더링한다", () => {
      render(<DashboardHistory />);
      expect(screen.getByText("S-11")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "월별 이력" }),
      ).toBeInTheDocument();
    });

    it("DashboardSimulator(12개월 시뮬레이터) 화면을 정상 렌더링한다", () => {
      render(<DashboardSimulator />);
      expect(screen.getByText("S-15")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "12개월 시뮬레이터" }),
      ).toBeInTheDocument();
    });

    it("DashboardTrends(추이 분석) 화면을 정상 렌더링한다", () => {
      render(<DashboardTrends />);
      expect(screen.getByText("S-14")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "추이 분석" }),
      ).toBeInTheDocument();
    });
  });

  describe("DashboardLayout (대시보드 공통 레이아웃)", () => {
    it("세션 조회 중일 때는 SessionPending을 렌더링한다", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "loading",
      });

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <DashboardLayout />
        </MemoryRouter>,
      );

      expect(screen.getByRole("status")).toHaveTextContent("불러오는 중…");
    });

    it("온보딩 미완료(비로그인 또는 단계 미완료) 시 리디렉트한다", () => {
      vi.mocked(SessionProviderModule.useSession).mockReturnValue({
        status: "anonymous",
      });

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route path="/dashboard" element={<DashboardLayout />} />
            <Route path="/" element={<div>랜딩 페이지</div>} />
          </Routes>
        </MemoryRouter>,
      );

      expect(screen.getByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("온보딩 완료 사용자인 경우 대시보드 탭 네비게이션이 포함된 AppShell을 렌더링한다", () => {
      const onboardedSession: Session = {
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      };
      vi.mocked(SessionProviderModule.useSession).mockReturnValue(
        onboardedSession,
      );

      render(
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route element={<DashboardLayout />}>
              <Route path="/dashboard" element={<div>대시보드 콘텐츠</div>} />
            </Route>
          </Routes>
        </MemoryRouter>,
      );

      expect(
        screen.getByRole("navigation", { name: "대시보드" }),
      ).toBeInTheDocument();
      expect(screen.getByText("대시보드 콘텐츠")).toBeInTheDocument();
    });
  });
});

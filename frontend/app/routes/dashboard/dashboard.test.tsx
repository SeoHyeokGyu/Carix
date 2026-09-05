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

describe("Dashboard routes and layout", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("pages", () => {
    it("renders DashboardHome (index)", () => {
      render(<DashboardHome />);
      expect(screen.getByText("S-10")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "대시보드" }),
      ).toBeInTheDocument();
    });

    it("renders DashboardHistory", () => {
      render(<DashboardHistory />);
      expect(screen.getByText("S-11")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "월별 이력" }),
      ).toBeInTheDocument();
    });

    it("renders DashboardSimulator", () => {
      render(<DashboardSimulator />);
      expect(screen.getByText("S-15")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "12개월 시뮬레이터" }),
      ).toBeInTheDocument();
    });

    it("renders DashboardTrends", () => {
      render(<DashboardTrends />);
      expect(screen.getByText("S-14")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "추이 분석" }),
      ).toBeInTheDocument();
    });
  });

  describe("DashboardLayout", () => {
    it("renders SessionPending when session is loading", () => {
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

    it("redirects when user is not onboarded (e.g. anonymous or incomplete step)", () => {
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

    it("renders AppShell with dashboard tabs when user is onboarded", () => {
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

import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetSession, setSession } from "../../lib/session";
import DashboardHistory from "./history";
import DashboardHome from "./index";
import DashboardLayout, { clientLoader as dashboardLayoutLoader } from "./layout";
import DashboardSimulator from "./simulator";
import DashboardTrends from "./trends";

describe("대시보드 라우트 및 레이아웃 (Dashboard)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 각 테스트가
    // setSession 으로 필요한 값을 명시적으로 채운다.
    resetSession();
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
    it("비로그인 상태면 랜딩 페이지로 보낸다", async () => {
      setSession({ status: "anonymous" });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>대시보드</div>,
        },
        { path: "/", Component: () => <div>랜딩 페이지</div> },
      ]);

      render(<Stub initialEntries={["/dashboard"]} />);

      expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("온보딩 미완료 사용자면 남은 단계 화면으로 보낸다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>대시보드</div>,
        },
        {
          path: "/onboarding/profile",
          Component: () => <div>프로필 온보딩으로 리디렉트됨</div>,
        },
      ]);

      render(<Stub initialEntries={["/dashboard"]} />);

      expect(
        await screen.findByText("프로필 온보딩으로 리디렉트됨"),
      ).toBeInTheDocument();
    });

    it("온보딩 완료 사용자인 경우 대시보드 탭 네비게이션이 포함된 AppShell을 렌더링한다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: DashboardLayout,
          children: [
            { index: true, Component: () => <div>대시보드 콘텐츠</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/dashboard"]} />);

      expect(
        await screen.findByRole("navigation", { name: "대시보드" }),
      ).toBeInTheDocument();
      expect(screen.getByText("대시보드 콘텐츠")).toBeInTheDocument();
    });
  });
});

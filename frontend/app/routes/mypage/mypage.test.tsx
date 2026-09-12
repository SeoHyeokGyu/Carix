import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as onboardingApi from "../../lib/onboardingApi";
import { resetSession, setSession } from "../../lib/session";
import MypageLayout, { clientLoader as mypageLayoutLoader } from "./layout";
import MypageProfile, { clientAction as mypageProfileAction } from "./profile";
import MypageRecords from "./records";

describe("마이페이지 라우트 및 레이아웃 (Mypage)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 각 테스트가
    // setSession 으로 필요한 값을 명시적으로 채운다.
    resetSession();
  });

  describe("개별 화면 (Pages)", () => {
    it("MypageProfile(설정) 화면을 정상 렌더링한다", async () => {
      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          Component: MypageProfile,
        },
      ]);
      render(<Stub initialEntries={["/mypage/profile"]} />);
      expect(await screen.findByText("S-13")).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "설정" })).toBeInTheDocument();
      expect(screen.getByText("온보딩 재설정")).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "온보딩 처음부터 다시 시작" }),
      ).toBeInTheDocument();
    });

    it("온보딩 다시 시작을 누르면 resetOnboarding 을 부르고 프로필로 이동한다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });

      const resetSpy = vi
        .spyOn(onboardingApi, "resetOnboarding")
        .mockResolvedValue({
          currentStep: "PROFILE",
          completed: false,
        });

      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          Component: MypageProfile,
          action: mypageProfileAction,
        },
        {
          path: "/onboarding/profile",
          Component: () => <div>프로필 화면</div>,
        },
      ]);

      render(<Stub initialEntries={["/mypage/profile"]} />);
      const user = userEvent.setup();

      await user.click(
        await screen.findByRole("button", {
          name: "온보딩 처음부터 다시 시작",
        }),
      );

      expect(resetSpy).toHaveBeenCalledTimes(1);
      expect(await screen.findByText("프로필 화면")).toBeInTheDocument();
    });

    it("MypageRecords(이력 관리) 화면을 정상 렌더링한다", () => {
      render(<MypageRecords />);
      expect(screen.getByText("S-12")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "이력 관리" }),
      ).toBeInTheDocument();
    });
  });

  describe("MypageLayout (마이페이지 공통 레이아웃)", () => {
    it("비로그인(anonymous) 상태 시 랜딩 페이지로 리디렉트한다", async () => {
      setSession({ status: "anonymous" });

      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          loader: mypageLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>마이페이지</div>,
        },
        { path: "/", Component: () => <div>랜딩 페이지</div> },
      ]);

      render(<Stub initialEntries={["/mypage/profile"]} />);

      expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("로그인 상태면 온보딩 완료 여부와 관계없이 AppShell 탭을 렌더링한다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE", // Even incomplete onboarding is allowed in mypage
      });

      const Stub = createRoutesStub([
        {
          path: "/mypage/profile",
          loader: mypageLayoutLoader,
          HydrateFallback: () => null,
          Component: MypageLayout,
          children: [
            { index: true, Component: () => <div>설정 페이지 내용</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/mypage/profile"]} />);

      expect(
        await screen.findByRole("navigation", { name: "마이페이지" }),
      ).toBeInTheDocument();
      expect(screen.getByText("설정 페이지 내용")).toBeInTheDocument();

      // 상단 메인 메뉴바의 항목들이 설정 화면에서도 변경되지 않고 그대로 유지되는지 검증
      expect(screen.getByRole("link", { name: "지표" })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "이력" })).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "추이 분석" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "시뮬레이터" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "이력 관리" }),
      ).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /설정/ })).toBeInTheDocument();
    });
  });
});

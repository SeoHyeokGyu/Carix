import { render, screen } from "@testing-library/react";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetSession, setSession } from "../../lib/session";
import OnboardingLayout, {
  clientLoader as onboardingLayoutLoader,
} from "./layout";
import Skills from "./skills";

describe("온보딩 라우트 및 레이아웃 (Onboarding)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 각 테스트가
    // setSession 으로 필요한 값을 명시적으로 채운다.
    resetSession();
  });

  describe("개별 화면 (Pages)", () => {
    // Profile·Income·Expense·Review 는 각각 S-05·S-06·S-07·S-09 실화면
    // (clientLoader/clientAction)을 구현한 뒤로 여기서 다루지 않는다 —
    // profile.test.tsx·income.test.tsx·expense.test.tsx·review.test.tsx 가 전담한다.

    it("Skills(보유 스킬) 화면을 정상 렌더링한다", () => {
      render(<Skills />);
      expect(screen.getByText("S-08")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "보유 스킬" }),
      ).toBeInTheDocument();
    });
  });

  describe("OnboardingLayout (온보딩 공통 레이아웃)", () => {
    it("비로그인 상태면 랜딩 페이지로 보낸다", async () => {
      setSession({ status: "anonymous" });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/profile",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>온보딩</div>,
        },
        { path: "/", Component: () => <div>랜딩 페이지</div> },
      ]);

      render(<Stub initialEntries={["/onboarding/profile"]} />);

      expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("사용자의 현재 단계와 다른 온보딩 경로 접근 시 현재 단계로 리디렉트한다", async () => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/skills",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>스킬</div>,
        },
        {
          path: "/onboarding/profile",
          Component: () => <div>프로필로 리디렉트됨</div>,
        },
      ]);

      render(<Stub initialEntries={["/onboarding/skills"]} />);

      expect(
        await screen.findByText("프로필로 리디렉트됨"),
      ).toBeInTheDocument();
    });

    it("온보딩 완료 사용자가 소득 화면을 열면 대시보드로 보낸다", async () => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "a@b.kr",
        onboardingStep: "COMPLETED",
      });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/income",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>온보딩</div>,
        },
        { path: "/dashboard", Component: () => <div>대시보드</div> },
      ]);

      render(<Stub initialEntries={["/onboarding/income"]} />);

      expect(await screen.findByText("대시보드")).toBeInTheDocument();
    });

    it("현재 진행 단계와 일치하면 AppShell 탭 및 온보딩 화면을 렌더링한다", async () => {
      setSession({
        status: "authenticated",
        userId: "u-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const Stub = createRoutesStub([
        {
          path: "/onboarding/profile",
          loader: onboardingLayoutLoader,
          HydrateFallback: () => null,
          Component: OnboardingLayout,
          children: [
            { index: true, Component: () => <div>프로필 온보딩 본문</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/onboarding/profile"]} />);

      expect(
        await screen.findByRole("navigation", { name: "온보딩 단계" }),
      ).toBeInTheDocument();
      expect(screen.getByText("프로필 온보딩 본문")).toBeInTheDocument();
    });
  });
});

import { describe, expect, test } from "vitest";
import type { OnboardingStep, Session } from "./session";
import {
  DASHBOARD_PATH,
  LOGIN_PATH,
  REVIEW_PATH,
  guardOnboarded,
  guardOnboarding,
  guardSession,
} from "./routeGuards";

const anonymous: Session = { status: "anonymous" };

const signedIn = (onboardingStep: OnboardingStep): Session => ({
  status: "authenticated",
  email: "beta@example.com",
  onboardingStep,
});

describe("guardSession — 마이페이지", () => {
  test("세션이 없으면 로그인으로 보낸다", () => {
    expect(guardSession(anonymous)).toEqual({
      allow: false,
      redirectTo: LOGIN_PATH,
    });
  });

  test("온보딩을 마치지 않았어도 통과시킨다", () => {
    expect(guardSession(signedIn("INCOME"))).toEqual({ allow: true });
  });
});

describe("guardOnboarded — 대시보드", () => {
  test("세션이 없으면 로그인으로 보낸다", () => {
    expect(guardOnboarded(anonymous)).toEqual({
      allow: false,
      redirectTo: LOGIN_PATH,
    });
  });

  test("온보딩이 남아 있으면 마지막 미완료 단계로 보낸다", () => {
    expect(guardOnboarded(signedIn("EXPENSE"))).toEqual({
      allow: false,
      redirectTo: "/onboarding/expense",
    });
  });

  test("온보딩을 마쳤으면 통과시킨다", () => {
    expect(guardOnboarded(signedIn("COMPLETED"))).toEqual({ allow: true });
  });
});

describe("guardOnboarding — 온보딩", () => {
  test("세션이 없으면 로그인으로 보낸다", () => {
    expect(guardOnboarding(anonymous, "/onboarding/profile")).toEqual({
      allow: false,
      redirectTo: LOGIN_PATH,
    });
  });

  test("현재 단계 화면은 통과시킨다", () => {
    expect(guardOnboarding(signedIn("SKILLS"), "/onboarding/skills")).toEqual({
      allow: true,
    });
  });

  test("앞선 단계를 건너뛰면 현재 단계로 되돌린다", () => {
    expect(guardOnboarding(signedIn("PROFILE"), "/onboarding/skills")).toEqual({
      allow: false,
      redirectTo: "/onboarding/profile",
    });
  });

  test("이미 지난 단계로 돌아가도 현재 단계로 되돌린다", () => {
    expect(guardOnboarding(signedIn("REVIEW"), "/onboarding/income")).toEqual({
      allow: false,
      redirectTo: REVIEW_PATH,
    });
  });

  test("완료 후 온보딩 화면은 대시보드로 보낸다", () => {
    expect(
      guardOnboarding(signedIn("COMPLETED"), "/onboarding/income"),
    ).toEqual({ allow: false, redirectTo: DASHBOARD_PATH });
  });

  test("완료 후에도 검토 화면은 재진입을 허용한다", () => {
    expect(guardOnboarding(signedIn("COMPLETED"), REVIEW_PATH)).toEqual({
      allow: true,
    });
  });
});

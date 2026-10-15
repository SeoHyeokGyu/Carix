import { describe, expect, test } from "vitest";

import {
  DASHBOARD_PATH,
  guardOnboarded,
  guardOnboarding,
  guardSession,
  isForwardStep,
  LANDING_PATH,
  REVIEW_PATH,
  stepPath,
} from "./routeGuards";
import type { OnboardingStep, Session } from "./session";

const anonymous: Session = { status: "anonymous" };

const loading: Session = { status: "loading" };

const signedIn = (onboardingStep: OnboardingStep): Session => ({
  status: "authenticated",
  userId: "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  email: "beta@example.com",
  onboardingStep,
});

describe("guardSession — 마이페이지", () => {
  test("세션 조회 중에는 판정하지 않는다", () => {
    expect(guardSession(loading)).toEqual({ allow: "pending" });
  });

  test("세션이 없으면 랜딩으로 보낸다", () => {
    expect(guardSession(anonymous)).toEqual({
      allow: false,
      redirectTo: LANDING_PATH,
    });
  });

  test("온보딩을 마치지 않았어도 통과시킨다", () => {
    expect(guardSession(signedIn("INCOME"))).toEqual({ allow: true });
  });
});

describe("guardOnboarded — 대시보드", () => {
  test("세션 조회 중에는 판정하지 않는다", () => {
    expect(guardOnboarded(loading)).toEqual({ allow: "pending" });
  });

  test("세션이 없으면 랜딩으로 보낸다", () => {
    expect(guardOnboarded(anonymous)).toEqual({
      allow: false,
      redirectTo: LANDING_PATH,
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
  test("세션 조회 중에는 판정하지 않는다", () => {
    expect(guardOnboarding(loading, "/onboarding/profile")).toEqual({
      allow: "pending",
    });
  });

  test("세션이 없으면 랜딩으로 보낸다", () => {
    expect(guardOnboarding(anonymous, "/onboarding/profile")).toEqual({
      allow: false,
      redirectTo: LANDING_PATH,
    });
  });

  test("현재 단계 화면은 통과시킨다", () => {
    expect(guardOnboarding(signedIn("SKILLS"), "/onboarding/skills")).toEqual({
      allow: true,
    });
  });

  test("앞선(아직 도달하지 않은) 단계를 건너뛰면 현재 단계로 되돌린다", () => {
    expect(guardOnboarding(signedIn("PROFILE"), "/onboarding/skills")).toEqual({
      allow: false,
      redirectTo: "/onboarding/profile",
    });
  });

  // 정책 변경 — 예전에는 지난 단계로 돌아가는 것도 현재 단계로 되돌렸다. 검토(S-09)
  // 화면의 "수정" 링크가 앞선 단계로 돌아갈 수 있어야 해서(그리고 이 소득 화면의
  // "이전" 링크도 같은 이유로) 한 단계 전이든 여러 단계 전이든 뒤로 가는 것은
  // 이제 허용한다. 아직 도달하지 않은 단계로 앞서가는 것만 계속 막는다.
  test("한 단계 이전으로 돌아가면 허용한다", () => {
    expect(guardOnboarding(signedIn("EXPENSE"), "/onboarding/income")).toEqual({
      allow: true,
    });
  });

  test("여러 단계 이전으로 돌아가면 허용한다", () => {
    expect(guardOnboarding(signedIn("REVIEW"), "/onboarding/income")).toEqual({
      allow: true,
    });
    expect(guardOnboarding(signedIn("SKILLS"), "/onboarding/profile")).toEqual({
      allow: true,
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

  // deny-by-default — 알 수 없는 경로는 어떤 단계에도 대응되지 않아 인덱스
  // 비교를 할 수 없다. 이 경우 fail-closed 로 현재 단계로 되돌린다(허용 쪽으로
  // 새지 않는다).
  test("알려지지 않은 경로는 현재 단계로 되돌린다", () => {
    expect(guardOnboarding(signedIn("INCOME"), "/onboarding/unknown")).toEqual({
      allow: false,
      redirectTo: "/onboarding/income",
    });
  });
});

describe("stepPath — 온보딩 단계별 경로 매핑", () => {
  test("온보딩 완료(COMPLETED) 상태면 대시보드 경로를 반환한다", () => {
    expect(stepPath("COMPLETED")).toBe(DASHBOARD_PATH);
  });

  test("미완료 단계별 해당 온보딩 경로를 반환한다", () => {
    expect(stepPath("PROFILE")).toBe("/onboarding/profile");
    expect(stepPath("INCOME")).toBe("/onboarding/income");
    expect(stepPath("EXPENSE")).toBe("/onboarding/expense");
    expect(stepPath("SKILLS")).toBe("/onboarding/skills");
    expect(stepPath("REVIEW")).toBe(REVIEW_PATH);
  });

  // FIX 4b — session.ts 는 서버 응답의 onboardingStep 을 검증 없이 그대로 편다.
  // 알려지지 않은 값이 들어오면 이전에는 STEP_PATH 조회가 undefined 를 돌려줘
  // redirect(undefined) → "/undefined" 로 튕기는 루프가 됐다. fail-closed 로
  // 랜딩을 돌려줘야 한다.
  test("알려지지 않은 단계값은 undefined 가 아니라 랜딩 경로로 fail-closed 한다", () => {
    expect(stepPath("BOGUS" as OnboardingStep)).toBe(LANDING_PATH);
  });
});

describe("guardOnboarded — 알려지지 않은 단계값 (FIX 4b)", () => {
  test("알려지지 않은 단계값이면 /undefined 가 아니라 랜딩으로 보낸다", () => {
    expect(guardOnboarded(signedIn("BOGUS" as OnboardingStep))).toEqual({
      allow: false,
      redirectTo: LANDING_PATH,
    });
  });
});

describe("isForwardStep — 서버 단계 갱신을 앞으로 갈 때만 허용", () => {
  test("아직 도달하지 않은 단계로 가면 true다", () => {
    expect(isForwardStep("PROFILE", "INCOME")).toBe(true);
    expect(isForwardStep("PROFILE", "REVIEW")).toBe(true);
  });

  test("이미 지난 단계를 다시 저장해도 false다(뒤로 가지 않는다)", () => {
    expect(isForwardStep("REVIEW", "INCOME")).toBe(false);
    expect(isForwardStep("EXPENSE", "PROFILE")).toBe(false);
  });

  test("같은 단계를 다시 제출해도 false다", () => {
    expect(isForwardStep("INCOME", "INCOME")).toBe(false);
  });

  test("COMPLETED 는 그 앞의 어떤 단계로도 갈 수 없다", () => {
    expect(isForwardStep("COMPLETED", "REVIEW")).toBe(false);
    expect(isForwardStep("COMPLETED", "PROFILE")).toBe(false);
  });
});

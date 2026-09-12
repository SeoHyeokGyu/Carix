import { Link, useLocation } from "react-router";
import { getSession } from "../../lib/session";

export const ONBOARDING_STEPS = [
  { step: 1, to: "/onboarding/profile", label: "프로필" },
  { step: 2, to: "/onboarding/income", label: "소득" },
  { step: 3, to: "/onboarding/expense", label: "소비" },
  { step: 4, to: "/onboarding/skills", label: "스킬" },
  { step: 5, to: "/onboarding/review", label: "검토" },
] as const;

export type StepNumber = 1 | 2 | 3 | 4 | 5;

const STEP_ORDER_MAP: Record<string, StepNumber> = {
  PROFILE: 1,
  INCOME: 2,
  EXPENSE: 3,
  SKILLS: 4,
  REVIEW: 5,
  COMPLETED: 5,
};

interface StepIndicatorProps {
  current?: StepNumber;
}

/**
 * 온보딩 화면 본문 상단에 표시되는 세련된 단계 표시(Stepper) 컴포넌트.
 * 이미 도달했거나 작성한 단계는 클릭하여 자유롭게 이동 및 재수정할 수 있습니다.
 */
export function StepIndicator({ current }: StepIndicatorProps) {
  const location = useLocation();

  const resolvedStep: StepNumber = (() => {
    if (current) return current;
    const path = location.pathname;
    if (path.includes("/profile")) return 1;
    if (path.includes("/income")) return 2;
    if (path.includes("/expense")) return 3;
    if (path.includes("/skills")) return 4;
    if (path.includes("/review")) return 5;
    return 1;
  })();

  // 세션의 최대 도달 단계 확인 (이전 단계로 돌아왔더라도 앞선 단계로 다시 이동 가능)
  const session = getSession();
  const sessionMaxStep: StepNumber =
    session && session.status === "authenticated"
      ? (STEP_ORDER_MAP[session.onboardingStep] ?? 1)
      : 1;

  const maxReachableStep = Math.max(resolvedStep, sessionMaxStep);

  const progressPercent = Math.max(
    0,
    Math.min(100, ((resolvedStep - 1) / (ONBOARDING_STEPS.length - 1)) * 100),
  );

  return (
    <nav aria-label="온보딩 단계" className="w-full select-none py-2">
      <div className="relative flex items-center justify-between">
        {/* 전체 연결선 (배경) */}
        <div
          aria-hidden="true"
          className="absolute left-4 right-4 top-4 sm:top-[18px] h-0.5 -translate-y-1/2 bg-carix-border/80"
        />

        {/* 활성 연결선 (진행률) */}
        <div
          aria-hidden="true"
          className="absolute left-4 top-4 sm:top-[18px] h-0.5 -translate-y-1/2 bg-carix-info transition-all duration-300 ease-out"
          style={{ width: `calc(${progressPercent}% * (100% - 32px) / 100)` }}
        />

        {/* 5개 스텝 노드 */}
        <ol
          aria-label="온보딩 진행 단계"
          className="relative z-10 flex w-full items-start justify-between"
        >
          {ONBOARDING_STEPS.map(({ step, to, label }) => {
            const isCompleted = step < resolvedStep;
            const isCurrent = step === resolvedStep;
            const isClickable = step <= maxReachableStep && !isCurrent;

            const content = (
              <div className="flex flex-col items-center">
                {/* 원형 배지 */}
                <div
                  className={[
                    "flex h-8 w-8 sm:h-9 sm:w-9 items-center justify-center rounded-full text-xs sm:text-sm font-semibold transition-all duration-200",
                    isCompleted
                      ? "bg-carix-info text-white shadow-sm ring-2 ring-carix-info/20 group-hover:ring-carix-info/40 group-hover:scale-105"
                      : isCurrent
                        ? "border-2 border-carix-info bg-carix-surface text-carix-info font-bold shadow-md ring-4 ring-carix-info/15 scale-105"
                        : "border-2 border-carix-border bg-carix-surface text-carix-text/40",
                  ].join(" ")}
                >
                  {isCompleted ? (
                    <svg
                      className="h-4 w-4 sm:h-4.5 sm:w-4.5 stroke-[2.5]"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                      aria-hidden="true"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        d="M5 13l4 4L19 7"
                      />
                    </svg>
                  ) : (
                    <span>{step}</span>
                  )}
                </div>

                {/* 단계명 라벨 */}
                <span
                  className={[
                    "mt-2 text-xs sm:text-sm tracking-tight transition-colors whitespace-nowrap",
                    isCurrent
                      ? "font-bold text-carix-info"
                      : isCompleted
                        ? "font-medium text-carix-text/80 group-hover:text-carix-info"
                        : "font-medium text-carix-text/40",
                  ].join(" ")}
                >
                  {label}
                </span>
              </div>
            );

            return (
              <li
                key={step}
                aria-current={isCurrent ? "step" : undefined}
                className="flex flex-col items-center"
              >
                {isClickable ? (
                  <Link
                    to={to}
                    className="group flex flex-col items-center focus:outline-none focus-visible:ring-2 focus-visible:ring-carix-info rounded-lg p-1 transition-transform"
                    title={`${label} 단계로 이동`}
                  >
                    {content}
                  </Link>
                ) : (
                  <div
                    className={[
                      "flex flex-col items-center p-1",
                      isCurrent ? "" : "cursor-not-allowed opacity-80",
                    ].join(" ")}
                  >
                    {content}
                  </div>
                )}
              </li>
            );
          })}
        </ol>
      </div>
    </nav>
  );
}

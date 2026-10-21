import type { ReactNode } from "react";
import {
  Form,
  Link,
  redirect,
  useActionData,
  useLoaderData,
  useNavigation,
  useSubmit,
} from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";

import { onboardingStepLabel } from "../../components/onboarding/StepIndicator";
import {
  type AppliedExpenseCategory,
  type AppliedExpenseResponse,
  getAppliedExpenses,
  listExpenses,
} from "../../lib/api/expense";
import { type IncomeResponse, listIncome } from "../../lib/api/income";
import { getProfile, type ProfileResponse } from "../../lib/api/profile";
import { toUserMessage } from "../../lib/apiError";
import { UnauthorizedError } from "../../lib/coreApi";
import type { FieldErrors } from "../../lib/fieldErrors";
import { formatKoreanSalary } from "../../lib/koreanSalary";
import { advanceOnboarding, resetOnboarding } from "../../lib/onboardingApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { EXPENSE_CATEGORIES } from "../../lib/schemas/expense";
import {
  COMPANY_SIZES,
  EMPLOYMENT_TYPES,
  JOB_FAMILIES,
  REGIONS,
} from "../../lib/schemas/profile";
import { requireSession, setSession } from "../../lib/session";
import { fetchUserSkills } from "../../lib/skillApi";
import { currentYearMonth } from "../../lib/yearMonth";

type ActionData = { errors: FieldErrors } | null;

// 월(baseYearMonth)은 이 화면에도 선택 UI 가 없다 — 언제나 "당월" 지출 행을
// 읽는다(expense.tsx 와 같은 이유·같은 계산 — currentYearMonth, ../../lib/yearMonth).

function labelFor(
  options: readonly { value: string; label: string }[],
  value: string,
): string {
  return options.find((option) => option.value === value)?.label ?? value;
}

/** `PATCH /profile` 이 부분 저장을 허용한 대가(API 규격 §3.2)로, 필수 필드 완비 검증을 여기서 한다. */
function isProfileComplete(
  profile: ProfileResponse,
): profile is ProfileResponse & {
  jobFamily: string;
  yearsOfExperience: number;
  region: string;
  employmentType: string;
} {
  return (
    profile.jobFamily !== null &&
    profile.yearsOfExperience !== null &&
    profile.region !== null &&
    profile.employmentType !== null
  );
}

interface GateData {
  profile: ProfileResponse;
  income: IncomeResponse[];
}

interface ReviewRequirement {
  label: string;
  path: string;
  isSatisfied: (data: GateData) => boolean;
}

/**
 * 완비 게이트가 요구하는 항목 하나로 된 목록. 렌더링(미완 안내 링크)과
 * `clientAction`(제출 거부)이 이 배열 하나를 그대로 공유한다 — 같은 조건을
 * 두 곳에 따로 적으면 한쪽만 고치는 실수로 조용히 어긋난다(버튼은 뜨는데
 * 액션은 거부하거나, 그 반대). 항목을 추가하려면 이 배열에 한 줄만 더하면
 * 된다.
 *
 * **스킬(US-O5, 최소 1개)은 아직 여기 없다.** S-08(스킬 입력) 화면이 아직
 * `PlaceholderScreen`이라 자신의 "다음" 제출에 최소 1개 검증이 없다 — 지금
 * 여기서 스킬을 요구하면 아무도 온보딩을 끝낼 수 없는 교착 상태가 된다.
 * S-08 이 자신의 최소 1개 요구를 실제로 갖추면 이 배열에 한 줄을 추가해
 * 이중 방어로 승격할 수 있다. 그 전까지 스킬 0개는 시장가치 지수에 "표본
 * 부족" 뱃지로만 나타난다 — 설계가 이미 다루는 열화 상태라 아무것도 깨지지
 * 않는다.
 */
const REVIEW_REQUIREMENTS: ReviewRequirement[] = [
  {
    label: "프로필",
    path: "/onboarding/profile",
    isSatisfied: ({ profile }) => isProfileComplete(profile),
  },
  {
    label: "소득",
    path: "/onboarding/income",
    isSatisfied: ({ income }) => income.length > 0,
  },
];

// 데이터를 반환하는 액션 뒤에는 React Router 가 loader 를 자동으로 다시
// 돌린다 — 화면을 수동으로 새로고침하라고 안내할 필요가 없다. 어떤 항목이
// 다시 비었는지는 이 오류 아래 곧바로 뜨는 단계별 안내 링크가 보여준다.
const GATE_REFUSAL_MESSAGE =
  "입력하신 정보가 변경되어 필수 항목이 비어 있습니다. 아래 안내에 따라 다시 완료한 뒤 제출해 주세요.";

/**
 * 적용 금액은 투명성용 부가 정보다. 이 화면은 온보딩의 유일한 완비 게이트라, 이 조회가
 * 실패했다고 ErrorBoundary 로 넘겨 제출까지 막으면 안 된다 — 401 만 올려 보낸다.
 */
async function loadAppliedExpenses(): Promise<AppliedExpenseResponse | null> {
  try {
    return await getAppliedExpenses(currentYearMonth());
  } catch (error) {
    if (error instanceof UnauthorizedError) throw error;
    return null;
  }
}

function describeAmounts(categories: AppliedExpenseCategory[]): string {
  return categories
    .map((item) => {
      const label = labelFor(EXPENSE_CATEGORIES, item.category);
      return item.amount === null
        ? label
        : `${label} ${item.amount.toLocaleString()}원`;
    })
    .join(", ");
}

/**
 * US-O6 — 기본값으로 대체된 항목에 실제로 적용된 수치를 붙인다. `amountBasis` 세 갈래가
 * 각자 다른 문장이다. `weight` 는 `NONE` 에서만 읽는다: 서버는 늘 기본 가중치를 싣지만,
 * 금액 기준이 있으면 실제로 적용되는 비중은 금액 ÷ 합계라 기본 가중치가 아니다. 정본이 소수
 * 넷째 자리라 백분율은 둘째 자리까지 그대로 보인다 — 한 자리로 줄이면 반올림이 어긋난다.
 */
function appliedExpenseNote(applied: AppliedExpenseResponse | null): ReactNode {
  if (applied === null) {
    return <p>적용 금액을 불러오지 못했습니다. 제출에는 영향이 없습니다.</p>;
  }

  if (applied.amountBasis === "NONE") {
    const weights = applied.categories
      .map(
        (item) =>
          `${labelFor(EXPENSE_CATEGORIES, item.category)} ${(item.weight * 100).toFixed(2)}%`,
      )
      .join(", ");
    return (
      <p>
        <strong>적용된 소비 비중:</strong> 소비 금액 기준이 없어 통계청 평균
        비중({applied.weightBasePeriod}년 기준)만으로 계산합니다 — {weights}
      </p>
    );
  }

  if (applied.amountBasis === "PROPAGATED") {
    return (
      <p>
        <strong>적용된 소비 금액:</strong> 이전에 입력하신 소비 금액이 이번 달에
        그대로 적용됩니다 — {describeAmounts(applied.categories)}
      </p>
    );
  }

  const substituted = applied.categories.filter(
    (item) => item.weightStatus === "DEFAULT_CPI",
  );
  if (substituted.length === 0) return null;
  return (
    <p>
      <strong>기본값으로 대체된 항목:</strong> {describeAmounts(substituted)}{" "}
      (입력하신 금액 합계에 맞춰 채운 값)
    </p>
  );
}

export async function clientLoader() {
  try {
    const [profile, income, expenses, skills, session, applied] =
      await Promise.all([
        getProfile(),
        listIncome(),
        listExpenses(),
        fetchUserSkills(),
        requireSession(),
        loadAppliedExpenses(),
      ]);
    const isCompleted =
      session.status === "authenticated" &&
      session.onboardingStep === "COMPLETED";
    return { profile, income, expenses, skills, applied, isCompleted };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 조회 실패는 이 화면에 그릴 자리가 없다 — ErrorBoundary 몫이다(다른 온보딩 화면과 동일 판단).
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");

  if (intent === "reset") {
    try {
      await resetOnboarding();
      const session = await requireSession();
      if (session.status === "authenticated") {
        setSession({ ...session, onboardingStep: "PROFILE" });
      }
      return redirect("/onboarding/profile");
    } catch (error) {
      if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
      return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
    }
  }

  try {
    // 렌더는 loader 가 진입 시점에 가져온 데이터로 버튼을 보여줬을 뿐이다 —
    // 그 사이(다른 탭에서 소득 이력을 지우는 등) 완비 조건이 깨질 수 있다.
    // `PATCH /v1/auth/onboarding` 은 COMPLETED 를 무조건 받아 주므로(서버에
    // 완비 개념이 없다), 이 화면이 유일한 완비 게이트다 — 제출 시점에
    // REVIEW_REQUIREMENTS 로 다시 검증한다(렌더와 완전히 같은 조건 — 배열을
    // 공유하므로 표류할 수 없다).
    const [profile, income] = await Promise.all([getProfile(), listIncome()]);
    const isIncomplete = REVIEW_REQUIREMENTS.some(
      (requirement) => !requirement.isSatisfied({ profile, income }),
    );
    if (isIncomplete) {
      return { errors: { _form: GATE_REFUSAL_MESSAGE } } satisfies ActionData;
    }

    await advanceOnboarding("COMPLETED");

    // guardOnboarding 은 세션 캐시(session.ts 의 모듈 전역)를 읽는다 — 갱신하지 않으면
    // 대시보드 진입 시 온보딩 미완료로 오판해 다시 이 화면으로 되튕긴다.
    const session = await requireSession();
    if (session.status === "authenticated") {
      setSession({ ...session, onboardingStep: "COMPLETED" });
    }

    return redirect("/dashboard");
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
  }
}

export default function Review() {
  const { profile, income, expenses, skills, applied, isCompleted } =
    useLoaderData<typeof clientLoader>();
  const actionData = useActionData<typeof clientAction>();
  const navigation = useNavigation();
  const submit = useSubmit();
  const errors = actionData?.errors;
  const isSubmitting =
    navigation.state === "submitting" || navigation.state === "loading";

  const profileComplete = isProfileComplete(profile);
  const profileSummary = profileComplete
    ? [
        labelFor(JOB_FAMILIES, profile.jobFamily),
        `경력 ${profile.yearsOfExperience}년`,
        labelFor(REGIONS, profile.region),
        labelFor(EMPLOYMENT_TYPES, profile.employmentType),
        profile.companySize
          ? labelFor(COMPANY_SIZES, profile.companySize)
          : null,
      ]
        .filter((part): part is string => Boolean(part))
        .join(" · ")
    : null;

  const latestIncome =
    income.length > 0
      ? [...income].sort((a, b) =>
          b.effectiveDate.localeCompare(a.effectiveDate),
        )[0]
      : null;

  const currentExpense = expenses.find(
    (row) => row.baseYearMonth === currentYearMonth(),
  );

  // 부분 저장을 허용한 대가(§3.2)로 이 화면이 완비를 검증한다. 미완인 항목은
  // 제출 버튼 대신 해당 단계로 가는 안내로 대체한다 — 아무 것도 누를 수 없는
  // 화면이 아니라, 무엇을 더 해야 하는지 알려주는 화면이어야 한다.
  // `clientAction`의 제출 거부와 정확히 같은 조건(REVIEW_REQUIREMENTS)을 쓴다.
  const gateData: GateData = { profile, income };
  const incompleteSteps = REVIEW_REQUIREMENTS.filter(
    (requirement) => !requirement.isSatisfied(gateData),
  );
  const canSubmit = incompleteSteps.length === 0;

  return (
    <PageFrame width="narrow">
      <PageHeader title={onboardingStepLabel(5)} />

      {isCompleted && (
        <div className="rounded-lg border border-carix-info/40 bg-carix-info/10 p-5 text-sm text-carix-text">
          <p className="font-semibold text-carix-info">온보딩 완료 상태</p>
          <p className="mt-1 text-carix-text/80">
            이미 온보딩을 완료하셨습니다. 화면 아래 「처음부터 다시 시작」으로
            다시 할 수 있습니다.
          </p>
        </div>
      )}

      <div className="mt-6 flex flex-col gap-8">
        <Card data-page-section="">
          <CardHeader>
            <CardTitle as="h2">프로필</CardTitle>
            <CardAction>
              <Link
                to="/onboarding/profile"
                aria-label="프로필 정보 수정"
                className={buttonVariants({ variant: "outline", size: "sm" })}
              >
                수정
              </Link>
            </CardAction>
          </CardHeader>
          <CardContent>
            <p>
              {profileComplete ? profileSummary : "필수 정보가 비어 있습니다."}
            </p>
          </CardContent>
        </Card>

        <Card data-page-section="">
          <CardHeader>
            <CardTitle as="h2">소득</CardTitle>
            <CardAction>
              <Link
                to="/onboarding/income"
                aria-label="소득 정보 수정"
                className={buttonVariants({ variant: "outline", size: "sm" })}
              >
                수정
              </Link>
            </CardAction>
          </CardHeader>
          <CardContent>
            <p>
              {latestIncome
                ? `세전 연봉 ${formatKoreanSalary(latestIncome.grossSalary)} · 월 실수령액 ${formatKoreanSalary(latestIncome.netSalary)}`
                : "입력 필요"}
            </p>
          </CardContent>
        </Card>

        <Card data-page-section="">
          <CardHeader>
            <CardTitle as="h2">소비</CardTitle>
            <CardAction>
              <Link
                to="/onboarding/expense"
                aria-label="소비 정보 수정"
                className={buttonVariants({ variant: "outline", size: "sm" })}
              >
                수정
              </Link>
            </CardAction>
          </CardHeader>
          <CardContent>
            <p>
              {currentExpense
                ? `${currentExpense.items.length}개 카테고리 입력 완료`
                : "입력된 소비 내역이 없습니다."}
            </p>
          </CardContent>
        </Card>

        <Card data-page-section="">
          <CardHeader>
            <CardTitle as="h2">스킬</CardTitle>
            <CardAction>
              <Link
                to="/onboarding/skills"
                aria-label="스킬 정보 수정"
                className={buttonVariants({ variant: "outline", size: "sm" })}
              >
                수정
              </Link>
            </CardAction>
          </CardHeader>
          <CardContent>
            <p>
              {skills.length > 0
                ? `${skills.length}개 등록 (${skills.map((skill) => skill.standardName).join(", ")})`
                : "등록된 스킬이 없습니다."}
            </p>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6 flex flex-col gap-2 rounded-lg border border-carix-border bg-carix-surface px-5 py-4 text-sm text-carix-text/80">
        {appliedExpenseNote(applied)}
        <p>
          <strong>계산 제한 사항:</strong> 세금, 성과급, 주식보상, 퇴직금은
          추정하지 않으며 결과에 포함되지 않습니다.
        </p>
      </div>

      <Form method="post" className="mt-8 flex flex-col gap-4">
        {errors?._form && (
          <p role="alert" className="text-sm text-carix-danger">
            {errors._form}
          </p>
        )}

        {!canSubmit && !isCompleted && (
          <div className="flex flex-col gap-2 text-sm text-carix-text/80">
            <p>다음 단계를 완료해야 제출할 수 있습니다.</p>
            <ul className="flex flex-col gap-1">
              {incompleteSteps.map((step) => (
                <li key={step.path}>
                  <Link to={step.path} className="underline">
                    {step.label} 완료하러 가기
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* 와이어프레임(05-review.html) 순서 그대로 "이전"이 먼저, 제출 버튼이 그
            다음이다. DOM 순서를 CSS 로 뒤집지 않는다. */}
        <div className="flex flex-wrap gap-3">
          {!isCompleted && (
            <Link
              to="/onboarding/skills"
              className={buttonVariants({ variant: "outline" })}
            >
              이전
            </Link>
          )}
          {isCompleted ? (
            <>
              <Link to="/dashboard" className={buttonVariants()}>
                대시보드로 돌아가기
              </Link>
              <ConfirmDialog
                trigger={
                  <Button
                    type="button"
                    variant="outline"
                    disabled={isSubmitting}
                  >
                    처음부터 다시 시작
                  </Button>
                }
                title="온보딩을 처음부터 다시 할까요?"
                description="입력한 프로필·소득·소비·스킬은 그대로 남습니다. 다만 온보딩을 다시 마칠 때까지 판정 화면에 들어갈 수 없습니다."
                confirmLabel="다시 시작"
                tone="default"
                onConfirm={() =>
                  submit({ intent: "reset" }, { method: "post" })
                }
              />
            </>
          ) : (
            canSubmit && (
              <Button type="submit" disabled={isSubmitting}>
                제출하고 대시보드로
              </Button>
            )
          )}
        </div>
      </Form>
    </PageFrame>
  );
}

import { Link, redirect, useActionData, useLoaderData, useNavigation, Form } from "react-router";
import { buttonVariants, Button } from "@/components/ui/button";
import {
  Card,
  CardAction,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { StepBadges } from "../../components/onboarding/StepBadges";
import { toUserMessage } from "../../lib/apiError";
import { getProfile, type ProfileResponse } from "../../lib/api/profile";
import { listIncome } from "../../lib/api/income";
import { listExpenses } from "../../lib/api/expense";
import { listUserSkills } from "../../lib/api/userSkills";
import { UnauthorizedError } from "../../lib/coreApi";
import type { FieldErrors } from "../../lib/fieldErrors";
import { advanceOnboarding } from "../../lib/onboardingApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import {
  COMPANY_SIZES,
  EMPLOYMENT_TYPES,
  JOB_FAMILIES,
  REGIONS,
} from "../../lib/schemas/profile";
import { EXPENSE_CATEGORIES } from "../../lib/schemas/expense";
import { requireSession, setSession } from "../../lib/session";

type ActionData = { errors: FieldErrors } | null;

/**
 * 월(baseYearMonth)은 이 화면에도 선택 UI 가 없다 — 언제나 "당월" 지출 행을
 * 읽는다(expense.tsx 와 같은 이유·같은 계산).
 */
function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function labelFor(options: readonly { value: string; label: string }[], value: string): string {
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

export async function clientLoader() {
  try {
    const [profile, income, expenses, skills] = await Promise.all([
      getProfile(),
      listIncome(),
      listExpenses(),
      listUserSkills(),
    ]);
    return { profile, income, expenses, skills };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 조회 실패는 이 화면에 그릴 자리가 없다 — ErrorBoundary 몫이다(다른 온보딩 화면과 동일 판단).
    throw error;
  }
}

export async function clientAction() {
  try {
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
  const { profile, income, expenses, skills } = useLoaderData<typeof clientLoader>();
  const actionData = useActionData<typeof clientAction>();
  const navigation = useNavigation();
  const errors = actionData?.errors;
  const isSubmitting = navigation.state === "submitting" || navigation.state === "loading";

  const profileComplete = isProfileComplete(profile);
  const profileSummary = profileComplete
    ? [
        labelFor(JOB_FAMILIES, profile.jobFamily),
        `경력 ${profile.yearsOfExperience}년`,
        labelFor(REGIONS, profile.region),
        labelFor(EMPLOYMENT_TYPES, profile.employmentType),
        profile.companySize ? labelFor(COMPANY_SIZES, profile.companySize) : null,
      ]
        .filter((part): part is string => Boolean(part))
        .join(" · ")
    : null;

  const latestIncome =
    income.length > 0
      ? [...income].sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate))[0]
      : null;

  const currentExpense = expenses.find((row) => row.baseYearMonth === currentYearMonth());
  const defaultCpiLabels = (currentExpense?.items ?? [])
    .filter((item) => item.status === "DEFAULT_CPI")
    .map((item) => labelFor(EXPENSE_CATEGORIES, item.category));

  const incomeComplete = income.length > 0;

  // 부분 저장을 허용한 대가(§3.2)로 이 화면이 완비를 검증한다. 미완인 항목은
  // 제출 버튼 대신 해당 단계로 가는 안내로 대체한다 — 아무 것도 누를 수 없는
  // 화면이 아니라, 무엇을 더 해야 하는지 알려주는 화면이어야 한다.
  const incompleteSteps: { label: string; path: string }[] = [];
  if (!profileComplete) incompleteSteps.push({ label: "프로필", path: "/onboarding/profile" });
  if (!incomeComplete) incompleteSteps.push({ label: "소득", path: "/onboarding/income" });
  const canSubmit = incompleteSteps.length === 0;

  return (
    <section className="mx-auto w-full max-w-3xl px-6 py-16">
      <StepBadges current={5} />

      <h1 className="mt-6 font-display text-3xl font-bold text-carix-text sm:text-4xl">
        검토
      </h1>

      <div className="mt-6 flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>프로필</CardTitle>
            <CardAction>
              <Link to="/onboarding/profile" className={buttonVariants({ variant: "outline", size: "sm" })}>
                수정
              </Link>
            </CardAction>
          </CardHeader>
          <CardContent>
            <p>{profileComplete ? profileSummary : "필수 정보가 비어 있습니다."}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>소득</CardTitle>
            <CardAction>
              <Link to="/onboarding/income" className={buttonVariants({ variant: "outline", size: "sm" })}>
                수정
              </Link>
            </CardAction>
          </CardHeader>
          <CardContent>
            <p>
              {latestIncome
                ? `세전 연봉 ${latestIncome.grossSalary.toLocaleString()}원 · 월 실수령액 ${latestIncome.netSalary.toLocaleString()}원`
                : "입력 필요"}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>소비</CardTitle>
            <CardAction>
              <Link to="/onboarding/expense" className={buttonVariants({ variant: "outline", size: "sm" })}>
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

        <Card>
          <CardHeader>
            <CardTitle>스킬</CardTitle>
            <CardAction>
              <Link to="/onboarding/skills" className={buttonVariants({ variant: "outline", size: "sm" })}>
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
        {defaultCpiLabels.length > 0 && (
          <p>
            <strong>기본값으로 대체된 항목:</strong> {defaultCpiLabels.join(", ")} (통계청 평균
            소비지출 비중 사용)
          </p>
        )}
        <p>
          <strong>계산 제한 사항:</strong> 세금, 성과급, 주식보상, 퇴직금은 추정하지 않으며
          결과에 포함되지 않습니다.
        </p>
      </div>

      <Form method="post" className="mt-8 flex flex-col gap-4">
        {errors?._form && (
          <p role="alert" className="text-sm text-destructive">
            {errors._form}
          </p>
        )}

        {!canSubmit && (
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
          <Link to="/onboarding/skills" className={buttonVariants({ variant: "outline" })}>
            이전
          </Link>
          {canSubmit && (
            <Button type="submit" disabled={isSubmitting}>
              제출하고 대시보드로
            </Button>
          )}
        </div>
      </Form>
    </section>
  );
}

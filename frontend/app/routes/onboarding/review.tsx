import {
  Link,
  redirect,
  useActionData,
  useLoaderData,
  useNavigation,
  Form,
} from "react-router";
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
import { listIncome, type IncomeResponse } from "../../lib/api/income";
import { listExpenses } from "../../lib/api/expense";
import { listUserSkills } from "../../lib/api/userSkills";
import { UnauthorizedError } from "../../lib/coreApi";
import type { FieldErrors } from "../../lib/fieldErrors";
import { advanceOnboarding, resetOnboarding } from "../../lib/onboardingApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import {
  COMPANY_SIZES,
  EMPLOYMENT_TYPES,
  JOB_FAMILIES,
  REGIONS,
} from "../../lib/schemas/profile";
import { EXPENSE_CATEGORIES } from "../../lib/schemas/expense";
import { requireSession, setSession } from "../../lib/session";
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

export async function clientLoader() {
  try {
    const [profile, income, expenses, skills, session] = await Promise.all([
      getProfile(),
      listIncome(),
      listExpenses(),
      listUserSkills(),
      requireSession(),
    ]);
    const isCompleted =
      session.status === "authenticated" &&
      session.onboardingStep === "COMPLETED";
    return { profile, income, expenses, skills, isCompleted };
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
  const { profile, income, expenses, skills, isCompleted } =
    useLoaderData<typeof clientLoader>();
  const actionData = useActionData<typeof clientAction>();
  const navigation = useNavigation();
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
  const defaultCpiLabels = (currentExpense?.items ?? [])
    .filter((item) => item.status === "DEFAULT_CPI")
    .map((item) => labelFor(EXPENSE_CATEGORIES, item.category));

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
    <section className="mx-auto w-full max-w-3xl px-6 py-16">
      <StepBadges current={5} />

      <h1 className="mt-6 font-display text-3xl font-bold text-carix-text sm:text-4xl">
        검토
      </h1>

      {isCompleted && (
        <div className="mt-6 rounded-lg border border-carix-info/40 bg-carix-info/10 p-5 text-sm text-carix-text">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="font-semibold text-carix-info">온보딩 완료 상태</p>
              <p className="mt-1 text-carix-text/80">
                이미 온보딩을 완료하셨습니다. 처음부터 다시 온보딩을
                진행하시려면 재시작을 눌러주세요.
              </p>
            </div>
            <Button
              type="submit"
              name="intent"
              value="reset"
              form="review-form"
              variant="default"
              disabled={isSubmitting}
              className="self-start sm:self-auto"
            >
              온보딩 처음부터 다시 시작
            </Button>
          </div>
        </div>
      )}

      <div className="mt-6 flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>프로필</CardTitle>
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

        <Card>
          <CardHeader>
            <CardTitle>소득</CardTitle>
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
                ? `세전 연봉 ${latestIncome.grossSalary.toLocaleString()}원 · 월 실수령액 ${latestIncome.netSalary.toLocaleString()}원`
                : "입력 필요"}
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>소비</CardTitle>
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

        <Card>
          <CardHeader>
            <CardTitle>스킬</CardTitle>
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
        {defaultCpiLabels.length > 0 && (
          <p>
            <strong>기본값으로 대체된 항목:</strong>{" "}
            {defaultCpiLabels.join(", ")} (통계청 평균 소비지출 비중 사용)
          </p>
        )}
        <p>
          <strong>계산 제한 사항:</strong> 세금, 성과급, 주식보상, 퇴직금은
          추정하지 않으며 결과에 포함되지 않습니다.
        </p>
      </div>

      <Form id="review-form" method="post" className="mt-8 flex flex-col gap-4">
        {errors?._form && (
          <p role="alert" className="text-sm text-destructive">
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
              <Button
                type="submit"
                name="intent"
                value="reset"
                disabled={isSubmitting}
              >
                온보딩 처음부터 다시 시작
              </Button>
              <Link
                to="/dashboard"
                className={buttonVariants({ variant: "outline" })}
              >
                대시보드로 돌아가기
              </Link>
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
    </section>
  );
}

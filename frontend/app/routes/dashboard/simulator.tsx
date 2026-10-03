import { useEffect, useRef, useState } from "react";
import { Link, redirect, useFetcher, useLoaderData } from "react-router";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Field } from "../../components/onboarding/Field";
import { isApiError, toUserMessage } from "../../lib/apiError";
import {
  getLatestScenario,
  simulate,
  type DifficultyGrade,
  type SimulationResult,
} from "../../lib/api/scenario";
import { type ApiError, UnauthorizedError } from "../../lib/coreApi";
import { toFieldErrors, type FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { formDataToObject } from "../../lib/formData";
import { LOGIN_PATH } from "../../lib/routeGuards";
import {
  scenarioSchema,
  SCENARIO_FIELD_MESSAGES,
} from "../../lib/schemas/scenario";

type ActionData =
  | { result: SimulationResult; isEligible: boolean }
  | { errors: FieldErrors; missingIncome?: boolean }
  | null;

const DIFFICULTY_GRADE_LABEL: Record<DifficultyGrade, string> = {
  EASY: "쉬움",
  REALISTIC: "현실적",
  CHALLENGING: "도전적",
  VERY_HIGH_CHALLENGE: "매우 도전적",
};

/**
 * `currentGrossSalary` 를 생략했는데 사용자에게 소득 이력이 없으면 서버가 400
 * `INVALID_INPUT` 을 준다(API 규격 §6.1) — 이 화면이 보내는 다른 다섯 필드는
 * `scenarioSchema` 가 이미 서버와 같거나 더 좁은 범위로 전부 걸러내므로, 검증을
 * 통과한 제출이 그래도 400 을 받는 현실적인 경우는 이 케이스뿐이다. 전용 코드가
 * 없어(같은 INVALID_INPUT) status 만으로 판별한다 — income.tsx 의 `isConflict` 와
 * 같은 판단(그쪽은 409), 판별자 자체는 `apiError.ts` 의 `isApiError` 하나를 공유한다.
 *
 * 이전에는 이 함수가 `"status" in error` 만 보고 `code`·`message` 존재는 확인하지
 * 않아 `isConflict`·`isApiError` 보다 느슨했다 — `coreApi` 가 던지는 `ApiError` 는
 * 항상 세 필드를 함께 갖고 있어(`coreApi.ts` `toApiError`) 이 화면이 실제로 받는
 * 오류에 대해서는 관찰 가능한 차이가 없다. 통일한다.
 */
function isLikelyMissingIncome(error: unknown): error is ApiError {
  return isApiError(error) && error.status === 400;
}

export async function clientLoader() {
  try {
    return { scenario: await getLatestScenario() };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 조회 실패는 이 화면에 그릴 자리가 없다 — ErrorBoundary 몫이다(S-05·S-06·S-07 과 동일 판단).
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();

  const raw = formDataToObject(formData, {
    numeric: [
      "stayExpectedRaiseRate",
      "moveTargetGrossSalary",
      "moveOneTimeCost",
      "expectedUnemploymentMonths",
    ],
  });
  // 체크박스류가 아니라 라디오라 formDataToObject 의 문자열 처리로는 boolean 이
  // 안 나온다 — 여기서만 별도로 boolean 으로 바꾼다. 둘 다 선택 안 했으면(고른 적
  // 없는 빈 폼 제출) 키 자체를 만들지 않아 zod 의 "필수" 오류로 자연스럽게 떨어진다.
  const eligibility = formData.get("isEligibleForUnemploymentBenefit");
  const input = {
    ...raw,
    ...(eligibility === "true" || eligibility === "false"
      ? { isEligibleForUnemploymentBenefit: eligibility === "true" }
      : {}),
  };

  const parsed = scenarioSchema.safeParse(input);
  if (!parsed.success) {
    return {
      errors: toFieldErrors(parsed.error.issues, SCENARIO_FIELD_MESSAGES),
    } satisfies ActionData;
  }

  try {
    return {
      result: await simulate(parsed.data),
      isEligible: parsed.data.isEligibleForUnemploymentBenefit,
    } satisfies ActionData;
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    return {
      errors: { _form: toUserMessage(error) },
      missingIncome: isLikelyMissingIncome(error),
    } satisfies ActionData;
  }
}

export default function DashboardSimulator() {
  const { scenario } = useLoaderData<typeof clientLoader>();
  const fetcher = useFetcher<ActionData>();
  const formRef = useRef<HTMLFormElement>(null);

  // 저장된 시나리오가 없으면(204) 아무것도 고르지 않은 상태로 둔다 — 라디오는
  // "미입력"을 표현할 방법이 텍스트 입력의 빈 문자열과 다르므로 세 번째 상태(null)
  // 를 따로 둔다. 기본값을 채우지 않는다는 규칙(브리프)은 이 필드에도 똑같이 적용된다.
  // 이 상태는 라디오 컨트롤 자체(다음 제출에 실릴 값)의 것이다 — 화면에 지금
  // 보이는 결과가 실업급여를 반영해 계산됐는지는 아래 resultEligible 이 따로 본다.
  const [isEligible, setIsEligible] = useState<boolean | null>(
    scenario?.inputs.isEligibleForUnemploymentBenefit ?? null,
  );

  // 계산이 실패하면 fetcher.data 는 {errors} 만 담고 {result} 는 없다 — loader 가
  // 준 이전 결과(scenario.result)로 자연히 폴백해, 실패한 계산이 화면에서 이전
  // 결과를 지우지 않는다(별도 "마지막 성공 결과" 상태를 만들 필요가 없다).
  const success =
    fetcher.data && "result" in fetcher.data ? fetcher.data : null;
  const result = success?.result ?? scenario?.result ?? null;
  // difficultyGrade 는 서버 응답을 실측한 적이 없다(검증 범위 절 참고) — 이 화면이
  // 아는 네 값(EASY/REALISTIC/CHALLENGING/VERY_HIGH_CHALLENGE) 밖의 문자열이 오면
  // DIFFICULTY_GRADE_LABEL 조회가 undefined 를 돌려준다. TS 는 DifficultyGrade
  // 유니온을 신뢰해 이 조회가 항상 string 이라고 믿지만, 실제 응답은 타입 검증을
  // 거치지 않으므로 값 자체(undefined 가능성)를 신뢰 소스로 삼아 렌더 여부를 가른다.
  const gradeLabel = result?.feasibility.difficultyGrade
    ? DIFFICULTY_GRADE_LABEL[result.feasibility.difficultyGrade]
    : undefined;
  // SimulationResult 응답 자체에는 isEligibleForUnemploymentBenefit 이 없다 —
  // 지금 화면에 보이는 result 가 어느 쪽으로 계산됐는지는 그 result 가 온 자리
  // (fetcher 성공 응답 또는 loader 의 저장분)에서 함께 따라온 값으로만 알 수
  // 있다. 라디오의 "현재 선택"(isEligible)으로 대신하면, 계산을 다시 누르지
  // 않고 라디오만 바꾼 순간 화면에 남아 있는 이전 결과(다른 가정으로 계산된
  // 금액)에 방금 고른 가정의 안내 문구가 잘못 붙는다 — 숫자와 문구가 서로 다른
  // 가정을 말하게 된다.
  const resultEligible = success
    ? success.isEligible
    : (scenario?.inputs.isEligibleForUnemploymentBenefit ?? null);
  const errors =
    fetcher.data && "errors" in fetcher.data ? fetcher.data.errors : undefined;
  const missingIncome = Boolean(
    fetcher.data &&
    "missingIncome" in fetcher.data &&
    fetcher.data.missingIncome,
  );
  const isSubmitting = fetcher.state !== "idle";
  const inputs = scenario?.inputs;

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

  return (
    <section className="mx-auto w-full max-w-3xl px-6 py-10">
      <h2 className="font-display text-2xl font-bold text-carix-text">
        12개월 시뮬레이터
      </h2>
      <p className="mt-1 text-sm text-carix-text/70">
        잔류와 이직의 12개월 보상을 비교합니다. 세금·성과급·주식보상·퇴직금은
        포함하지 않습니다.
      </p>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>시나리오 입력</CardTitle>
          <CardDescription>
            값을 비워 두면 임의로 채우지 않습니다 — 계산에 필요한 값을 직접
            입력해 주세요.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <fetcher.Form
            method="post"
            ref={formRef}
            className="flex flex-col gap-4"
          >
            <Field
              id="scenarioName"
              label="시나리오 이름 (선택)"
              error={errors?.scenarioName}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="scenarioName"
                  type="text"
                  placeholder="예: A사 오퍼 제안"
                  defaultValue={inputs?.scenarioName ?? ""}
                />
              )}
            </Field>

            <Field
              id="stayExpectedRaiseRate"
              label="예상 인상률 (%)"
              error={errors?.stayExpectedRaiseRate}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="stayExpectedRaiseRate"
                  type="number"
                  inputMode="decimal"
                  step="0.1"
                  placeholder="예: 3.5"
                  defaultValue={inputs?.stayExpectedRaiseRate ?? ""}
                />
              )}
            </Field>

            <Field
              id="moveTargetGrossSalary"
              label="이직 시 목표 세전 연봉 (원)"
              error={errors?.moveTargetGrossSalary}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="moveTargetGrossSalary"
                  type="number"
                  inputMode="numeric"
                  placeholder="예: 72000000"
                  defaultValue={inputs?.moveTargetGrossSalary ?? ""}
                />
              )}
            </Field>

            <Field
              id="moveOneTimeCost"
              label="이직에 드는 일회성 비용 (원)"
              error={errors?.moveOneTimeCost}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="moveOneTimeCost"
                  type="number"
                  inputMode="numeric"
                  placeholder="예: 3000000"
                  defaultValue={inputs?.moveOneTimeCost ?? ""}
                />
              )}
            </Field>

            <Field
              id="expectedUnemploymentMonths"
              label="예상 구직 공백 (개월, 0~6)"
              error={errors?.expectedUnemploymentMonths}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="expectedUnemploymentMonths"
                  type="number"
                  inputMode="numeric"
                  min={0}
                  max={6}
                  placeholder="예: 2"
                  defaultValue={inputs?.expectedUnemploymentMonths ?? ""}
                />
              )}
            </Field>

            <div className="flex flex-col gap-2">
              <span
                id="eligibility-label"
                className="text-sm font-medium text-carix-text"
              >
                실업급여 수급 자격
              </span>
              <div
                role="radiogroup"
                aria-labelledby="eligibility-label"
                aria-invalid={Boolean(errors?.isEligibleForUnemploymentBenefit)}
                aria-describedby={
                  errors?.isEligibleForUnemploymentBenefit
                    ? "eligibility-error"
                    : undefined
                }
                tabIndex={-1}
                className="flex gap-4"
              >
                <label className="flex items-center gap-2 text-sm text-carix-text">
                  <input
                    type="radio"
                    name="isEligibleForUnemploymentBenefit"
                    value="true"
                    checked={isEligible === true}
                    onChange={() => setIsEligible(true)}
                  />
                  있음
                </label>
                <label className="flex items-center gap-2 text-sm text-carix-text">
                  <input
                    type="radio"
                    name="isEligibleForUnemploymentBenefit"
                    value="false"
                    checked={isEligible === false}
                    onChange={() => setIsEligible(false)}
                  />
                  없음
                </label>
              </div>
              {errors?.isEligibleForUnemploymentBenefit && (
                <p
                  id="eligibility-error"
                  role="alert"
                  className="text-sm text-destructive"
                >
                  {errors.isEligibleForUnemploymentBenefit}
                </p>
              )}
            </div>

            {errors?._form && (
              <div className="flex flex-col gap-1">
                <p role="alert" className="text-sm text-destructive">
                  {errors._form}
                </p>
                {missingIncome && (
                  // /onboarding/income(S-06)은 온보딩 완료 사용자를 guardOnboarding 이
                  // 대시보드로 되돌려 보내 여기서는 죽은 링크다 — 대신 입력 관리
                  // (S-12, mypage/records)로 보낸다. 문구는 링크 없이도 뜻이 통하도록
                  // "무엇을 해야 하는지"까지 문장 안에 적는다.
                  <p className="text-sm text-carix-text/70">
                    소득 이력이 없으면 계산할 수 없습니다.{" "}
                    <Link to="/mypage/records" className="underline">
                      입력 관리
                    </Link>
                    에서 소득을 추가한 뒤 다시 계산해 주세요.
                  </p>
                )}
              </div>
            )}

            <Button
              type="submit"
              disabled={isSubmitting}
              className="self-start"
            >
              {isSubmitting ? "계산 중..." : "계산"}
            </Button>
          </fetcher.Form>
        </CardContent>
      </Card>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>비교 결과</CardTitle>
        </CardHeader>
        <CardContent>
          {!result && (
            <p className="text-sm text-muted-foreground">
              아직 계산한 시나리오가 없습니다. 값을 입력하고 계산해 주세요.
            </p>
          )}

          {result && (
            <div className="flex flex-col gap-4">
              <div className="flex items-center gap-2">
                <Badge>{result.typeLabel}</Badge>
                <span className="text-lg font-semibold text-carix-text">
                  {Math.abs(result.difference).toLocaleString()}원
                </span>
              </div>

              <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm text-carix-text">
                <dt className="text-carix-text/60">잔류 12개월 보상</dt>
                <dd>{result.stayCompensation12m.toLocaleString()}원</dd>
                <dt className="text-carix-text/60">이직 12개월 보상</dt>
                <dd>{result.moveCompensation12m.toLocaleString()}원</dd>
              </dl>

              {/* isEligibleForUnemploymentBenefit 은 응답(SimulationResult)이 아니라
                  입력값이다 — 라디오의 "현재 선택"이 아니라 지금 보이는 result 를
                  실제로 만든 가정(resultEligible)을 따른다. */}
              {resultEligible === false && (
                <p className="text-sm text-carix-text/70">실업급여 미반영</p>
              )}
              {resultEligible === true && (
                <p className="text-sm text-carix-text/70">
                  {result.unemploymentBenefitNote}
                </p>
              )}

              <p className="text-sm text-carix-text/70">{result.warningNote}</p>

              <div className="rounded-md border border-carix-border p-3 text-sm">
                <p className="font-medium text-carix-text">
                  목표 연봉 실현 가능성
                </p>
                {result.feasibility.status === "OK" ? (
                  <>
                    <p className="mt-1 text-carix-text">
                      {result.feasibility.targetPercentile !== null && (
                        <>상위 {result.feasibility.targetPercentile}%</>
                      )}
                      {gradeLabel && <> · {gradeLabel}</>}
                    </p>
                    {result.feasibility.feedbackMessage && (
                      <p className="mt-1 text-carix-text/70">
                        {result.feasibility.feedbackMessage}
                      </p>
                    )}
                  </>
                ) : (
                  <>
                    <p className="mt-1 text-carix-text">표본 부족</p>
                    {result.feasibility.minRequired !== null && (
                      <p className="mt-1 text-carix-text/70">
                        비교에 쓸 공개연봉 표본이{" "}
                        {result.feasibility.minRequired}건 이상 필요합니다(현재{" "}
                        {result.feasibility.sampleSize}건).
                      </p>
                    )}
                  </>
                )}
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </section>
  );
}

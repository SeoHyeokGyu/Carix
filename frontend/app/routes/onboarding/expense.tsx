import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { useEffect, useRef, useState } from "react";
import {
  Form,
  Link,
  redirect,
  useActionData,
  useLoaderData,
  useNavigation,
} from "react-router";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ExpenseDonut } from "../../components/expense/ExpenseDonut";
import { Field } from "../../components/onboarding/Field";
import { toUserMessage } from "../../lib/apiError";
import {
  listExpenses,
  putExpenses,
  type ExpenseItemStatus,
} from "../../lib/api/expense";
import { listIncome } from "../../lib/api/income";
import { UnauthorizedError } from "../../lib/coreApi";
import { toFieldErrors, type FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { formDataToObject } from "../../lib/formData";
import { advanceOnboarding } from "../../lib/onboardingApi";
import { isForwardStep, LOGIN_PATH } from "../../lib/routeGuards";
import {
  EXPENSE_CATEGORIES,
  EXPENSE_ITEM_AMOUNT_MESSAGE,
  expenseSchema,
} from "../../lib/schemas/expense";
import { requireSession, setSession } from "../../lib/session";
import { currentYearMonth } from "../../lib/yearMonth";

type ActionData = { errors: FieldErrors } | null;

const STATUS_LABELS: Record<ExpenseItemStatus, string> = {
  DEFAULT_CPI: "통계청 평균 기준",
  USER_DEFINED: "직접 입력",
};

// 월(baseYearMonth, "YYYYMM")은 URL 경로에만 있고 폼 어디에도 없다 — 이 화면에
// 월 선택 UI 자체가 없으므로 언제나 "당월"이다(currentYearMonth, ../../lib/yearMonth).
// 서버에 보낼 값을 항상 이 함수로만 새로 계산해, 사용자 입력이 이 값에 영향을 줄 수
// 있는 경로를 아예 없앤다(스키마에 제약을 둘 자리가 없는 "당월을 넘지 않는다" 요구는
// 이렇게 구조적으로 지켜진다). review.tsx 가 같은 함수로 저장분을 읽는다 — 둘이 각자
// 계산하면 월 경계에서 서로 어긋날 수 있다.

/**
 * 현재 입력값(문자열 맵) → 제출될 items 배열에서 각 카테고리가 차지할 인덱스.
 * 비워 둔 카테고리는 items 에서 아예 빠지므로(0 을 보내지 않는다), 인덱스는
 * EXPENSE_CATEGORIES 안의 "고정 위치"가 아니라 "실제로 제출되는 항목 중 몇
 * 번째인가"다 — clientAction 이 formDataToObject 로 items 를 만들 때와 반드시
 * 같은 순서·같은 필터 조건을 써야 toFieldErrors 가 만든 "items.N.amount" 키가
 * 화면에서 같은 카테고리로 다시 찾아진다.
 */
function buildItemIndex(
  values: Record<string, string>,
): Record<string, number> {
  const index: Record<string, number> = {};
  let cursor = 0;
  for (const category of EXPENSE_CATEGORIES) {
    const raw = values[category.value];
    if (typeof raw === "string" && raw !== "" && Number.isFinite(Number(raw))) {
      index[category.value] = cursor;
      cursor += 1;
    }
  }
  return index;
}

export async function clientLoader() {
  try {
    const [expenseHistory, income] = await Promise.all([
      listExpenses(),
      listIncome(),
    ]);
    const currentRow = expenseHistory.find(
      (row) => row.baseYearMonth === currentYearMonth(),
    );

    const amounts: Record<string, string> = {};
    const savedAmounts: Record<string, number> = {};
    const statuses: Record<string, ExpenseItemStatus> = {};
    for (const item of currentRow?.items ?? []) {
      // DEFAULT_CPI 항목은 서버가 통계청 평균으로 보간한 값이지 사용자가 입력한
      // 값이 아니다 — 입력칸을 채우면 다음 저장에서 그대로 다시 제출돼 "이
      // 사람이 실제로 이만큼 쓴다"는 USER_DEFINED 로 굳어 버린다(빈 칸을 0 으로
      // 보내는 것과 같은 종류의 실수, 다른 경로로 들어올 뿐이다). 뱃지는 모든
      // 카테고리의 상태를 그대로 그리되(서버가 돌려준 그대로), 입력칸은
      // USER_DEFINED 항목만 채운다.
      statuses[item.category] = item.status;
      savedAmounts[item.category] = item.amount;
      if (item.status === "USER_DEFINED") {
        amounts[item.category] = String(item.amount);
      }
    }

    // 월 실수령액(가장 최근 유효 시작일)을 넘는 지출을 경고하는 데 쓴다 — 소득
    // 이력이 없으면 비교할 기준이 없으므로 경고 자체를 내지 않는다.
    const latestIncome = [...income].sort((a, b) =>
      b.effectiveDate.localeCompare(a.effectiveDate),
    )[0];

    return {
      amounts,
      savedAmounts,
      statuses,
      takeHome: latestIncome?.netSalary ?? null,
    };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 조회 실패는 이 화면에 그릴 자리가 없다 — ErrorBoundary 몫이다(S-05·S-06 과 동일 판단).
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();

  // 빈 칸은 formDataToObject 가 이미 키를 만들지 않는다(FIX 5, formData.ts) —
  // 여기서 카테고리 고정 순서로 훑으며 "실제로 값이 있는" 카테고리만 items 로
  // 옮긴다. 명시적 "0" 은 유효한 값(진짜 지출 없음)이라 남고, 빈 문자열만 빠진다.
  const raw = formDataToObject(formData, {
    numeric: EXPENSE_CATEGORIES.map((category) => category.value),
  });
  const items = EXPENSE_CATEGORIES.filter(
    (category) => category.value in raw,
  ).map((category) => ({
    category: category.value,
    amount: raw[category.value] as number,
  }));

  // 메시지 맵을 items 길이만큼 동적으로 만든다 — 카테고리 개수가 고정이라도
  // "몇 번째가 실제로 제출됐는지"는 매번 다르므로 미리 완비할 수 없다. 바로 이
  // 지점이 팀 리드가 지목한, 행 인덱스 오류 키가 실제로 시험대에 오르는 자리다.
  const messages: Record<string, string> = {};
  items.forEach((_, index) => {
    messages[`items.${index}.amount`] = EXPENSE_ITEM_AMOUNT_MESSAGE;
  });

  const result = expenseSchema.safeParse({ items });
  if (!result.success) {
    return {
      errors: toFieldErrors(result.error.issues, messages),
    } satisfies ActionData;
  }

  try {
    await putExpenses(currentYearMonth(), result.data.items);

    // 이미 이 단계를 지난 사용자가 검토 화면에서 돌아와 다시 "다음"을 누르는
    // 정당한 흐름까지 서버 단계를 되돌리면(profile.tsx 와 같은 이유), 아직
    // 화면이 없는 단계로 사용자가 떠밀려 갇힌다. 실제로 앞으로 가는 경우에만
    // 서버 단계·세션 캐시를 갱신한다.
    const session = await requireSession();
    if (
      session.status === "authenticated" &&
      isForwardStep(session.onboardingStep, "SKILLS")
    ) {
      await advanceOnboarding("SKILLS");
      setSession({ ...session, onboardingStep: "SKILLS" });
    }

    return redirect("/onboarding/skills");
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
  }
}

export default function Expense() {
  const {
    amounts: initialAmounts,
    savedAmounts,
    statuses,
    takeHome,
  } = useLoaderData<typeof clientLoader>();
  const actionData = useActionData<typeof clientAction>();
  const navigation = useNavigation();
  const formRef = useRef<HTMLFormElement>(null);
  const [amounts, setAmounts] =
    useState<Record<string, string>>(initialAmounts);
  const errors = actionData?.errors;
  const isSubmitting =
    navigation.state === "submitting" || navigation.state === "loading";

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

  const itemIndex = buildItemIndex(amounts);

  // 실수령액 경고는 "지금 저장하면 무엇이 나갈지"만 본다 — 입력칸에 없는(서버가
  // 보간한) 값은 사용자가 지금 선언하는 지출이 아니므로 합계에 넣지 않는다.
  const enteredTotal = EXPENSE_CATEGORIES.reduce(
    (sum, category) => sum + (Number(amounts[category.value]) || 0),
    0,
  );
  const overBudget = takeHome != null && enteredTotal > takeHome;

  // 도넛은 반대다 — 구매력 계산을 실제로 움직이는 전체 비중 그림을 보여줘야
  // 하므로, 지금 입력칸에 없는 카테고리는 서버가 마지막으로 돌려준 값으로
  // 채운다(입력칸 자체는 여전히 비워 둔다 — 채워서 보여주는 자리와 채워서
  // 제출하는 자리는 다른 문제다). 그 대체값은 estimated 로 표시해 실제
  // 입력값과 구분한다(프로젝트 공통 규칙 — 색만으로 구분하지 않는다).
  const donutItems = EXPENSE_CATEGORIES.map((category) => {
    const entered = amounts[category.value];
    if (
      typeof entered === "string" &&
      entered !== "" &&
      Number.isFinite(Number(entered))
    ) {
      return { category: category.value, amount: Number(entered) };
    }
    const saved = savedAmounts[category.value];
    return saved === undefined
      ? { category: category.value, amount: 0 }
      : {
          category: category.value,
          amount: saved,
          estimated: statuses[category.value] === "DEFAULT_CPI",
        };
  });

  return (
    <PageFrame width="narrow">
      <PageHeader
        title="소비"
        description="6대 카테고리의 월 소비 금액을 입력해 주세요. 비워 둔 카테고리는 통계청 평균 소비지출 비중으로 자동 보완됩니다."
      />
      <Card>
        <CardContent>
          <Form method="post" ref={formRef} className="flex flex-col gap-6">
            {EXPENSE_CATEGORIES.map((category) => {
              const index = itemIndex[category.value];
              const errorKey =
                index !== undefined ? `items.${index}.amount` : undefined;
              const fieldError = errorKey ? errors?.[errorKey] : undefined;
              const status = statuses[category.value];

              return (
                <Field
                  key={category.value}
                  id={`amount-${category.value}`}
                  label={`${category.label} (원/월)`}
                  error={fieldError}
                >
                  {(aria) => (
                    <div className="flex items-center gap-2">
                      <Input
                        {...aria}
                        name={category.value}
                        type="number"
                        inputMode="numeric"
                        value={amounts[category.value] ?? ""}
                        onChange={(event) =>
                          setAmounts((prev) => ({
                            ...prev,
                            [category.value]: event.target.value,
                          }))
                        }
                      />
                      {status && (
                        <Badge
                          variant={
                            status === "DEFAULT_CPI" ? "secondary" : "outline"
                          }
                          className="shrink-0"
                        >
                          {STATUS_LABELS[status]}
                        </Badge>
                      )}
                    </div>
                  )}
                </Field>
              );
            })}

            {overBudget && (
              <p role="status" className="text-sm text-carix-warning">
                입력한 합계가 월 실수령액을 초과했습니다. 그래도 저장할 수
                있습니다.
              </p>
            )}

            <ExpenseDonut items={donutItems} />

            {errors?._form && (
              <p role="alert" className="text-sm text-carix-danger">
                {errors._form}
              </p>
            )}

            {/* 와이어프레임(03-expenses.html) 순서 그대로 "이전"이 먼저, "다음"(제출
                버튼)이 그 다음이다. 이 화면은 폼이 하나뿐이고 제출 버튼도 "다음"
                하나뿐이라 Enter 암묵 제출이 애초에 다른 버튼으로 갈 수 없다 —
                "이전"은 Link 라 이 <Form> 의 제출 버튼 판정에 관여하지 않는다.
                DOM 순서를 CSS 로 뒤집지 않는다. */}
            <div className="flex flex-wrap gap-3">
              <Link
                to="/onboarding/income"
                className={buttonVariants({ variant: "outline" })}
              >
                이전
              </Link>
              <Button type="submit" disabled={isSubmitting}>
                다음
              </Button>
            </div>
          </Form>
        </CardContent>
      </Card>
    </PageFrame>
  );
}

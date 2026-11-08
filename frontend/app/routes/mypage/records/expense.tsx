import { useEffect, useRef, useState } from "react";
import {
  redirect,
  useFetcher,
  useLoaderData,
  useRevalidator,
} from "react-router";

import { PageSection } from "@/components/layout/PageSection";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { type Tone, ToneBadge } from "@/components/ui/tone-badge";

import {
  keepUntouchedAmount,
  parseAmountInput,
  savedAmountFrom,
} from "../../../lib/amountInput";
import {
  type ExpenseItemStatus,
  listExpenses,
  putExpenses,
} from "../../../lib/api/expense";
import { listIncome } from "../../../lib/api/income";
import { toUserMessage } from "../../../lib/apiError";
import { UnauthorizedError } from "../../../lib/coreApi";
import { type FieldErrors, toFieldErrors } from "../../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../../lib/focusFirstInvalidField";
import { LOGIN_PATH } from "../../../lib/routeGuards";
import {
  EXPENSE_CATEGORIES,
  EXPENSE_ITEM_AMOUNT_MESSAGE,
  expenseSchema,
} from "../../../lib/schemas/expense";
import {
  currentYearMonth,
  formatYearMonth,
  isFutureYearMonth,
  toWireYearMonth,
} from "../../../lib/yearMonth";

/** 소득과 같은 만원 단위로 받고 서버에는 원으로 보낸다. */
const MANWON = 10_000;

type ActionData = { errors: FieldErrors } | null;

/**
 * 서버가 준 카테고리 상태를 그대로 문구와 톤으로 옮긴다 — 클라이언트가 계산하지 않는다.
 * 톤을 나누는 이유: 둘 다 중립색으로 그리면 "내가 입력한 값"과 "서버가 통계청 평균으로
 * 채운 값"이 화면에서 구분되지 않는다. 그 구분을 보여주려고 status 를 그대로 그리기로
 * 한 것이므로 색이 빠지면 전제가 무너진다. 와이어프레임(mypage.html)도 통계청 대체
 * 항목에 경고 톤을 지정한다.
 */
const STATUS_LABEL: Record<ExpenseItemStatus, string> = {
  USER_DEFINED: "직접 입력",
  DEFAULT_CPI: "통계청 평균 기준",
};

const STATUS_TONE: Record<ExpenseItemStatus, Tone> = {
  USER_DEFINED: "muted",
  DEFAULT_CPI: "warning",
};

/**
 * 카테고리 상태 뱃지에 붙일 카테고리 라벨. 여섯 칩이 전부 같은 상태 문구만
 * 달면 어느 칸이 보간됐는지 구분이 안 된다 — `EXPENSE_CATEGORIES` 로 이름을
 * 붙인다. 정본에 없는 값이 오면(서버 확장 등) enum 값 자체를 그대로 쓴다.
 */
const EXPENSE_CATEGORY_LABEL: Record<string, string> = Object.fromEntries(
  EXPENSE_CATEGORIES.map((category) => [category.value, category.label]),
);

/**
 * 월 단위 종합 상태(`weightStatus`)는 카테고리별 `status` 와 별개로 서버가
 * 계산해 내려준다(`ExpenseService.computeOverallStatus`). `PARTIALLY_ESTIMATED`
 * 는 카테고리별 상태가 섞였을 때, `PROPAGATED` 는 과거 수정의 미래 전파용으로
 * 값 목록에는 있지만 PPI 엔진 미착수라 실제로는 안 내려온다(명세 §3.3).
 * 타입이 `string` 이라 표에 없는 값도 올 수 있어 라벨·톤 둘 다 폴백을 둔다.
 */
const WEIGHT_STATUS_LABEL: Record<string, string> = {
  USER_DEFINED: "직접 입력",
  DEFAULT_CPI: "통계청 평균 기준",
  PARTIALLY_ESTIMATED: "일부 보간",
  PROPAGATED: "이전 수정 반영",
};

const WEIGHT_STATUS_TONE: Record<string, Tone> = {
  USER_DEFINED: "muted",
  DEFAULT_CPI: "warning",
  PARTIALLY_ESTIMATED: "warning",
  PROPAGATED: "info",
};

export async function clientLoader() {
  try {
    // 두 콜은 서로 독립이다 — 직렬로 기다리면 화면이 두 배 늦는다.
    const [expenses, income] = await Promise.all([
      listExpenses(),
      listIncome(),
    ]);
    const latestNet =
      [...income].sort((a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate),
      )[0]?.netSalary ?? null;
    return {
      months: [...expenses].sort((a, b) =>
        b.baseYearMonth.localeCompare(a.baseYearMonth),
      ),
      latestNet,
    };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const baseYearMonth = String(formData.get("baseYearMonth") ?? "");

  // 백엔드는 `^\d{6}$` 만 보고 미래를 안 막는다 — US-R1 의 "미래 날짜 입력 거부"를
  // 여기서 지킨다. `<input type="month">` 의 max 만으로는 부족하다(직접 입력·자동화).
  if (isFutureYearMonth(baseYearMonth)) {
    return {
      errors: { _form: "미래 기준월은 추가할 수 없습니다." },
    } satisfies ActionData;
  }
  if (!/^\d{6}$/.test(baseYearMonth)) {
    return {
      errors: { _form: "기준월 형식이 올바르지 않습니다." },
    } satisfies ActionData;
  }

  // 미입력 카테고리는 items 에서 빼서 보낸다 — 서버가 DEFAULT_CPI 로 보간한다.
  // 0 으로 채우지 않는다. `lib/api/expense.ts` 의 규약이며 호출부 책임이다.
  // 범위를 벗어난 값은 여기서 조용히 버리지 않는다 — 버리면 사용자가 잘못
  // 입력한 값이 DEFAULT_CPI 로 슬쩍 바뀌어 나가는 무언의 데이터 손실이 된다.
  // 검증은 expenseSchema(app/lib/schemas/expense.ts) 하나로만 한다 — 화면마다
  // 범위 숫자를 다시 적지 않는다(onboarding/expense.tsx 와 같은 규약).
  const items = EXPENSE_CATEGORIES.flatMap((category) => {
    const entered = parseAmountInput(
      String(formData.get(`amount-${category.value}`) ?? ""),
    );
    if (entered === null) return [];
    return [
      {
        category: category.value,
        amount: keepUntouchedAmount(
          entered * MANWON,
          savedAmountFrom(formData, category.value),
        ),
      },
    ];
  });

  // I1: 아직 목록에 없는 월(신규 기준월)을 전부 빈칸으로 저장하면 items=[] 가
  // 스키마를 그대로 통과해 PUT 된다 — 서버가 여섯 카테고리를 전부 DEFAULT_CPI 로
  // 채운 "기준월"을 만든다. D2 상 월 삭제 API 가 없으므로 이건 영구 통계전용
  // 월이 된다. 반면 이미 목록에 있는 월은 전부 기본값으로 되돌리는 것 자체가
  // 정당한 사용자 행동(D2)이라 items=[] 를 막으면 안 된다 — isNew 로만 가른다.
  const isNew = formData.get("isNew") === "true";
  if (isNew && items.length === 0) {
    return {
      errors: {
        _form: "새 기준월은 최소 한 카테고리 이상 입력해야 합니다.",
      },
    } satisfies ActionData;
  }

  // 메시지 맵을 items 길이만큼 동적으로 만든다 — items 는 실제로 값이 있는
  // 카테고리만 순서대로 담으므로 "몇 번째가 제출됐는지"는 매번 다르다
  // (onboarding/expense.tsx clientAction 과 같은 이유).
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
    await putExpenses(baseYearMonth, result.data.items);
    return null;
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
  }
}

/**
 * 현재 입력값(카테고리 → 문자열) → 제출될 items 배열에서 그 카테고리가 차지할
 * 인덱스. clientAction 의 items 필터(빈 문자열만 제외)와 반드시 같은 순서·같은
 * 조건으로 훑어야 toFieldErrors 가 만든 "items.N.amount" 키가 화면에서 같은
 * 카테고리로 다시 찾아진다 — 둘 중 하나만 고치면 오류가 엉뚱한 칸에 붙는다
 * (onboarding/expense.tsx buildItemIndex 와 같은 결합).
 */
function buildItemIndex(
  amounts: Record<string, string>,
): Record<string, number> {
  const index: Record<string, number> = {};
  let cursor = 0;
  for (const category of EXPENSE_CATEGORIES) {
    const raw = amounts[category.value];
    if (typeof raw === "string" && raw !== "") {
      index[category.value] = cursor;
      cursor += 1;
    }
  }
  return index;
}

export default function RecordsExpense() {
  const { months, latestNet } = useLoaderData<typeof clientLoader>();
  const fetcher = useFetcher<ActionData>();
  const formRef = useRef<HTMLFormElement>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [newMonth, setNewMonth] = useState("");
  const [monthError, setMonthError] = useState<string | null>(null);
  const errors = fetcher.data?.errors;
  const itemIndex = buildItemIndex(amounts);

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

  // 저장이 성공하면(action 이 null 을 돌려준다) 편집 폼을 닫는다 — 그렇지 않으면
  // 표를 보고서야 성공을 짐작해야 한다. 실패하면 입력을 남겨 다시 고치게 한다.
  // 소득 탭(income.tsx)과 같은 렌더 단계 갱신 패턴이다.
  const [prevFetcherState, setPrevFetcherState] = useState(fetcher.state);
  if (fetcher.state !== prevFetcherState) {
    setPrevFetcherState(fetcher.state);
    if (fetcher.state === "idle" && fetcher.data === null) {
      setEditing(null);
    }
  }

  const openMonth = (wire: string) => {
    const target = months.find((m) => m.baseYearMonth === wire);
    const next: Record<string, string> = {};
    for (const item of target?.items ?? []) {
      // DEFAULT_CPI 는 사용자가 넣은 값이 아니다 — 폼을 서버 보간값으로 채우면
      // 저장할 때 그 값이 사용자 입력으로 굳어버린다. 빈 칸으로 연다.
      if (item.status === "USER_DEFINED")
        next[item.category] = String(Math.round(item.amount / MANWON));
    }
    setAmounts(next);
    setEditing(wire);
  };

  // 손대지 않은 칸이 반올림 값으로 다시 저장되지 않도록 원 단위 저장값을 숨은 칸으로 함께 보낸다.
  const savedUserAmounts: Record<string, number> = Object.fromEntries(
    (months.find((m) => m.baseYearMonth === editing)?.items ?? [])
      .filter((item) => item.status === "USER_DEFINED")
      .map((item) => [item.category, item.amount]),
  );

  const isNewMonth =
    editing !== null && !months.some((m) => m.baseYearMonth === editing);

  const total = Object.values(amounts).reduce(
    (sum, value) => sum + (parseAmountInput(value) ?? 0) * MANWON,
    0,
  );
  const exceedsNet = latestNet !== null && total > latestNet;

  const handleAddMonth = () => {
    if (!newMonth) return;
    const wire = toWireYearMonth(newMonth);
    if (isFutureYearMonth(wire)) {
      setMonthError("미래 기준월은 추가할 수 없습니다.");
      return;
    }
    setMonthError(null);
    openMonth(wire);
  };

  // 월 목록과 고르는 달의 편집 폼을 넓은 화면에서 나란히 둔다.
  return (
    <div className="grid gap-8 lg:grid-cols-2 lg:items-start">
      <PageSection title="월별 소비">
        {/* 표와 기준월 추가 칸 사이 16px — 제목 → 내용 12px 는 섹션이 정한다. */}
        <div className="flex flex-col gap-4">
          {months.length === 0 ? (
            <EmptyState
              icon="📋"
              title="등록된 소비 기록이 없습니다"
              description="아래에서 기준월을 고르면 6개 카테고리를 입력할 수 있습니다. 기준월은 삭제할 수 없습니다 — 금액만 고치거나 기본값으로 되돌릴 수 있습니다."
            />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>기준월</TableHead>
                  <TableHead>월 상태</TableHead>
                  <TableHead>카테고리 상태</TableHead>
                  <TableHead>작업</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {months.map((month) => (
                  <TableRow key={month.baseYearMonth}>
                    {/* 짧은 고정 길이 값이다. 카테고리 뱃지 묶음이 폭을 가져가도 꺾지 않는다. */}
                    <TableCell className="whitespace-nowrap">
                      {formatYearMonth(month.baseYearMonth)}
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      <ToneBadge
                        tone={WEIGHT_STATUS_TONE[month.weightStatus] ?? "muted"}
                      >
                        {WEIGHT_STATUS_LABEL[month.weightStatus] ??
                          month.weightStatus}
                      </ToneBadge>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {month.items.map((item) => (
                          <ToneBadge
                            key={item.category}
                            tone={STATUS_TONE[item.status]}
                          >
                            {EXPENSE_CATEGORY_LABEL[item.category] ??
                              item.category}{" "}
                            · {STATUS_LABEL[item.status]}
                          </ToneBadge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => openMonth(month.baseYearMonth)}
                      >
                        수정
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          <Card className="gap-2">
            <Field
              id="newMonth"
              label="추가할 기준월"
              hint="당월까지만 추가할 수 있습니다."
            >
              {(aria) => (
                <Input
                  {...aria}
                  type="month"
                  max={formatYearMonth(currentYearMonth())}
                  value={newMonth}
                  onChange={(e) => setNewMonth(e.target.value)}
                />
              )}
            </Field>
            <div>
              <Button type="button" variant="outline" onClick={handleAddMonth}>
                기준월 추가
              </Button>
            </div>
            {monthError && (
              <p role="alert" className="text-sm text-carix-danger">
                {monthError}
              </p>
            )}
          </Card>
        </div>
      </PageSection>

      {editing && (
        <PageSection title={`${formatYearMonth(editing)} 소비`}>
          <Card>
            <fetcher.Form
              method="post"
              ref={formRef}
              className="flex flex-col gap-4"
            >
              <input type="hidden" name="baseYearMonth" value={editing} />
              <input
                type="hidden"
                name="isNew"
                value={isNewMonth ? "true" : "false"}
              />

              {EXPENSE_CATEGORIES.map((category) => {
                const index = itemIndex[category.value];
                const errorKey =
                  index !== undefined ? `items.${index}.amount` : undefined;
                const fieldError = errorKey ? errors?.[errorKey] : undefined;

                return (
                  <div key={category.value} className="flex items-end gap-2">
                    <div className="flex-1">
                      <Field
                        id={`amount-${category.value}`}
                        label={`${category.label} (만원/월)`}
                        error={fieldError}
                      >
                        {(aria) => (
                          <>
                            {savedUserAmounts[category.value] !== undefined && (
                              <input
                                type="hidden"
                                name={`saved-${category.value}`}
                                value={savedUserAmounts[category.value]}
                              />
                            )}
                            <Input
                              {...aria}
                              name={`amount-${category.value}`}
                              type="text"
                              inputMode="numeric"
                              placeholder="미입력 시 통계청 평균으로 보간됩니다"
                              value={amounts[category.value] ?? ""}
                              onChange={(e) =>
                                setAmounts((prev) => ({
                                  ...prev,
                                  [category.value]: e.target.value.replace(
                                    /[^0-9]/g,
                                    "",
                                  ),
                                }))
                              }
                            />
                          </>
                        )}
                      </Field>
                    </div>
                    {/* D2: 라벨은 "삭제"가 아니다. 값을 빼면 지워지는 게 아니라 서버가
                  통계청 평균으로 보간해 DEFAULT_CPI 로 돌려준다. */}
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      aria-label={`${category.label} 기본값으로 되돌리기`}
                      onClick={() =>
                        setAmounts((prev) => {
                          const next = { ...prev };
                          delete next[category.value];
                          return next;
                        })
                      }
                    >
                      기본값으로
                    </Button>
                  </div>
                );
              })}

              {exceedsNet && (
                <p role="alert" className="text-sm text-carix-warning">
                  입력한 합계 {total.toLocaleString()}원이 월 실수령액을
                  초과합니다. 월 실수령액은 {latestNet?.toLocaleString()}
                  원입니다. 그대로 저장할 수 있습니다.
                </p>
              )}

              {errors?._form && (
                <p role="alert" className="text-sm text-carix-danger">
                  {errors._form}
                </p>
              )}

              <div className="flex items-center gap-2">
                <Button type="submit" disabled={fetcher.state !== "idle"}>
                  저장
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setEditing(null)}
                >
                  취소
                </Button>
                <p className="text-xs text-carix-text/60">
                  저장하면 해당 월 이후 지수에 반영됩니다.
                </p>
              </div>
            </fetcher.Form>
          </Card>
        </PageSection>
      )}
    </div>
  );
}

export function ErrorBoundary() {
  const revalidator = useRevalidator();
  return (
    <EmptyState
      variant="plain"
      tone="danger"
      icon="⚠️"
      title="소비 이력을 불러오지 못했습니다"
      description="잠시 후 다시 시도해 주세요. 다른 탭은 그대로 열립니다."
    >
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={revalidator.state === "loading"}
        onClick={() => revalidator.revalidate()}
      >
        다시 시도
      </Button>
    </EmptyState>
  );
}

import { useState } from "react";
import { redirect, useFetcher, useLoaderData } from "react-router";
import { ToneBadge, type Tone } from "@/components/ui/tone-badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import { Field } from "../../../components/onboarding/Field";
import { toUserMessage } from "../../../lib/apiError";
import {
  listExpenses,
  putExpenses,
  type ExpenseItemStatus,
} from "../../../lib/api/expense";
import { listIncome } from "../../../lib/api/income";
import { UnauthorizedError } from "../../../lib/coreApi";
import { LOGIN_PATH } from "../../../lib/routeGuards";
import { EXPENSE_CATEGORIES } from "../../../lib/schemas/expense";
import {
  formatYearMonth,
  isFutureYearMonth,
  toWireYearMonth,
} from "../../../lib/yearMonth";

type ActionData = { error: string } | null;

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
    return { error: "미래 기준월은 추가할 수 없습니다." } satisfies ActionData;
  }
  if (!/^\d{6}$/.test(baseYearMonth)) {
    return { error: "기준월 형식이 올바르지 않습니다." } satisfies ActionData;
  }

  // 미입력 카테고리는 items 에서 빼서 보낸다 — 서버가 DEFAULT_CPI 로 보간한다.
  // 0 으로 채우지 않는다. `lib/api/expense.ts` 의 규약이며 호출부 책임이다.
  const items = EXPENSE_CATEGORIES.flatMap((category) => {
    const raw = String(formData.get(`amount-${category.value}`) ?? "").trim();
    if (raw === "") return [];
    const amount = Number(raw.replace(/,/g, ""));
    if (!Number.isInteger(amount) || amount < 0 || amount > 50_000_000) {
      return [];
    }
    return [{ category: category.value, amount }];
  });

  try {
    await putExpenses(baseYearMonth, items);
    return null;
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    return { error: toUserMessage(error) } satisfies ActionData;
  }
}

export default function RecordsExpense() {
  const { months, latestNet } = useLoaderData<typeof clientLoader>();
  const fetcher = useFetcher<ActionData>();
  const [editing, setEditing] = useState<string | null>(null);
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [newMonth, setNewMonth] = useState("");
  const [monthError, setMonthError] = useState<string | null>(null);
  const actionError = fetcher.data?.error;

  const openMonth = (wire: string) => {
    const target = months.find((m) => m.baseYearMonth === wire);
    const next: Record<string, string> = {};
    for (const item of target?.items ?? []) {
      // DEFAULT_CPI 는 사용자가 넣은 값이 아니다 — 폼을 서버 보간값으로 채우면
      // 저장할 때 그 값이 사용자 입력으로 굳어버린다. 빈 칸으로 연다.
      if (item.status === "USER_DEFINED")
        next[item.category] = String(item.amount);
    }
    setAmounts(next);
    setEditing(wire);
  };

  const total = Object.values(amounts).reduce(
    (sum, value) => sum + (Number(value.replace(/,/g, "")) || 0),
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

  return (
    <div className="flex flex-col gap-6">
      {months.length === 0 ? (
        <EmptyState
          icon="📋"
          title="등록된 소비 기록이 없습니다"
          description="아래에서 기준월을 고르면 6개 카테고리를 입력할 수 있습니다. 기준월은 삭제할 수 없습니다 — 금액만 고치거나 기본값으로 되돌릴 수 있습니다."
        />
      ) : (
        <table className="w-full text-left text-sm">
          <thead className="text-carix-text/70">
            <tr>
              <th className="px-3 py-2 font-medium">기준월</th>
              <th className="px-3 py-2 font-medium">카테고리 상태</th>
              <th className="px-3 py-2 font-medium">작업</th>
            </tr>
          </thead>
          <tbody>
            {months.map((month) => (
              <tr
                key={month.baseYearMonth}
                className="border-b border-carix-border/60"
              >
                <td className="px-3 py-2">
                  {formatYearMonth(month.baseYearMonth)}
                </td>
                <td className="px-3 py-2">
                  <div className="flex flex-wrap gap-1">
                    {month.items.map((item) => (
                      <ToneBadge
                        key={item.category}
                        tone={STATUS_TONE[item.status]}
                      >
                        {STATUS_LABEL[item.status]}
                      </ToneBadge>
                    ))}
                  </div>
                </td>
                <td className="px-3 py-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => openMonth(month.baseYearMonth)}
                  >
                    편집
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="flex flex-col gap-2 rounded-xl border border-carix-border bg-carix-surface p-6">
        <Field
          id="newMonth"
          label="추가할 기준월"
          hint="당월까지만 추가할 수 있습니다."
        >
          {(aria) => (
            <Input
              {...aria}
              type="month"
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
          <p role="alert" className="text-sm text-destructive">
            {monthError}
          </p>
        )}
      </div>

      {editing && (
        <fetcher.Form
          method="post"
          className="flex flex-col gap-4 rounded-xl border border-carix-border bg-carix-surface p-6"
        >
          <input type="hidden" name="baseYearMonth" value={editing} />
          <h2 className="text-lg font-bold text-carix-text">
            {formatYearMonth(editing)} 소비
          </h2>

          {EXPENSE_CATEGORIES.map((category) => (
            <div key={category.value} className="flex items-end gap-2">
              <div className="flex-1">
                <Field id={`amount-${category.value}`} label={category.label}>
                  {(aria) => (
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
          ))}

          {exceedsNet && (
            <p role="alert" className="text-sm text-carix-warning">
              입력한 합계 {total.toLocaleString()}원이 월 실수령액을 초과합니다.
              월 실수령액은 {latestNet?.toLocaleString()}원입니다. 그대로 저장할
              수 있습니다.
            </p>
          )}

          {actionError && (
            <p role="alert" className="text-sm text-destructive">
              {actionError}
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
      )}
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <EmptyState
      variant="plain"
      tone="danger"
      icon="⚠️"
      title="소비 이력을 불러오지 못했습니다"
      description="잠시 후 다시 시도해 주세요. 다른 탭은 그대로 열립니다."
    />
  );
}

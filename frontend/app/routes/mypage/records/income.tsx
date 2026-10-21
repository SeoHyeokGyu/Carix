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
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { BonusTermsForm } from "../../../components/income/BonusTermsForm";
import { PerformanceBonusList } from "../../../components/income/PerformanceBonusList";
import { Field } from "../../../components/onboarding/Field";
import { NativeSelect } from "../../../components/onboarding/NativeSelect";
import {
  createBonus,
  createIncome,
  deleteBonus,
  deleteIncome,
  type IncomeResponse,
  listBonuses,
  listIncome,
  updateBonusTerms,
  updateIncome,
} from "../../../lib/api/income";
import { isApiError, toUserMessage } from "../../../lib/apiError";
import { type ApiError, UnauthorizedError } from "../../../lib/coreApi";
import { type FieldErrors, toFieldErrors } from "../../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../../lib/focusFirstInvalidField";
import { formatKoreanSalary } from "../../../lib/koreanSalary";
import { LOGIN_PATH } from "../../../lib/routeGuards";
import {
  INCOME_CHANGE_REASON_HINT,
  INCOME_CHANGE_REASONS,
  INCOME_FIELD_MESSAGES,
  incomeChangeReasonCell,
  incomeSchema,
} from "../../../lib/schemas/income";

type ActionData = { errors: FieldErrors } | null;

/** 표 셀 문구. 따로 받는 고정상여는 금액까지 낸다. */
function bonusTermsCell(income: IncomeResponse): string {
  if (income.bonusIncluded) return "상여 포함";
  return `고정상여 별도 ${formatKoreanSalary(income.fixedBonusAnnual)}`;
}

/** 유효 시작일 중복은 서버가 409 로 알린다 — `_form` 이 아니라 그 필드에 붙여야 한다. */
function isConflict(error: unknown): error is ApiError {
  return isApiError(error) && error.status === 409;
}

export async function clientLoader() {
  try {
    const [income, bonuses] = await Promise.all([listIncome(), listBonuses()]);
    // 서버 정렬 순서를 신뢰하지 않고 항상 클라이언트에서 다시 정렬한다.
    return {
      income: [...income].sort((a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate),
      ),
      bonuses: [...bonuses].sort((a, b) =>
        b.paidMonth.localeCompare(a.paidMonth),
      ),
    };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

/** 상여 섹션 세 intent. 실패는 화면에 남기고 401 만 로그인으로 보낸다. */
async function runBonusIntent(
  intent: string,
  formData: FormData,
): Promise<ActionData> {
  try {
    if (intent === "bonus-terms") {
      const included = formData.get("bonusIncluded") === "true";
      const amount = String(formData.get("fixedBonusAnnual") ?? "");
      await updateBonusTerms({
        applyFromIncomeId: String(formData.get("applyFromIncomeId") ?? ""),
        bonusIncluded: included,
        fixedBonusAnnual: amount === "" ? null : Number(amount),
      });
    } else if (intent === "bonus-create") {
      await createBonus({
        paidMonth: String(formData.get("paidMonth") ?? ""),
        amount: Number(formData.get("amount")),
      });
    } else {
      const id = formData.get("id");
      if (typeof id !== "string" || !id) {
        return { errors: { _form: "삭제할 항목을 찾을 수 없습니다." } };
      }
      await deleteBonus(id);
    }
    return null;
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 상여 조건의 400 은 고정상여 칸에 붙인다 — 서버가 그 칸의 조합 위반만 400 으로 돌려준다.
    if (intent === "bonus-terms" && isApiError(error) && error.status === 400) {
      return { errors: { fixedBonusAnnual: error.message } };
    }
    return { errors: { _form: toUserMessage(error) } };
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");

  if (
    intent === "bonus-terms" ||
    intent === "bonus-create" ||
    intent === "bonus-delete"
  ) {
    return runBonusIntent(intent, formData);
  }

  if (intent === "delete") {
    const id = formData.get("id");
    // M5: id 가 없거나 문자열이 아니면 전에는 아무것도 안 하고 null(성공)을
    // 돌려줘 폼이 성공한 것처럼 닫혔다. 지금은 hidden input 이 항상 id 를
    // 실어 보내 실전에서는 닿지 않는 경로지만, 그렇다고 조용히 성공을 가장할
    // 이유는 없다 — 정직하게 _form 오류로 돌려준다.
    if (typeof id !== "string" || !id) {
      return {
        errors: { _form: "삭제할 항목을 찾을 수 없습니다." },
      } satisfies ActionData;
    }
    try {
      await deleteIncome(id);
      return null;
    } catch (error) {
      if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
      return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
    }
  }

  // 사용자가 만원 단위(콤마 포함)로 입력하므로 콤마를 지우고 원 단위로 환산해 검증한다.
  const grossRaw = String(formData.get("grossSalary") ?? "")
    .replace(/,/g, "")
    .trim();
  const netRaw = String(formData.get("netSalary") ?? "")
    .replace(/,/g, "")
    .trim();
  const effectiveDate = String(formData.get("effectiveDate") ?? "").trim();
  const changeReasonRaw = String(formData.get("changeReason") ?? "").trim();

  const result = incomeSchema.safeParse({
    grossSalary: grossRaw === "" ? NaN : Number(grossRaw) * 10_000,
    netSalary: netRaw === "" ? NaN : Number(netRaw) * 10_000,
    effectiveDate,
    // 빈 문자열은 "고르지 않음" 이다 — undefined 로 보내야 서버가 null 로 저장한다.
    // "" 를 그대로 실으면 enum 검증에서 떨어진다.
    changeReason: changeReasonRaw === "" ? undefined : changeReasonRaw,
  });
  if (!result.success) {
    return {
      errors: toFieldErrors(result.error.issues, INCOME_FIELD_MESSAGES),
    } satisfies ActionData;
  }

  try {
    const id = formData.get("id");
    if (intent === "update" && typeof id === "string" && id) {
      await updateIncome(id, result.data);
    } else {
      await createIncome(result.data);
    }
    return null;
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    if (isConflict(error)) {
      return { errors: { effectiveDate: error.message } } satisfies ActionData;
    }
    return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
  }
}

interface IncomeRowProps {
  income: IncomeResponse;
  onEdit: (id: string) => void;
}

function IncomeRow({ income, onEdit }: IncomeRowProps) {
  const deleteFetcher = useFetcher<ActionData>();
  const isDeleting = deleteFetcher.state !== "idle";
  const deleteError = deleteFetcher.data?.errors?._form;

  return (
    <TableRow>
      <TableCell>{income.effectiveDate}</TableCell>
      <TableCell>{income.grossSalary.toLocaleString()}원</TableCell>
      <TableCell>{income.netSalary.toLocaleString()}원</TableCell>
      {/* 두 필드가 갈리는 것은 최소 유효일 행뿐이고, 그때는 둘 다 낸다 — 폼은
          selectedChangeReason 하나만 쓴다. */}
      <TableCell>
        {incomeChangeReasonCell(
          income.changeReason,
          income.selectedChangeReason,
        )}
      </TableCell>
      <TableCell>{bonusTermsCell(income)}</TableCell>
      <TableCell>
        <div className="flex flex-col gap-1">
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onEdit(income.id)}
            >
              수정
            </Button>
            <ConfirmDialog
              trigger={
                <Button
                  type="button"
                  variant="destructive"
                  size="sm"
                  disabled={isDeleting}
                >
                  삭제
                </Button>
              }
              title="소득 이력을 삭제할까요?"
              description={`${income.effectiveDate} 소득 이력이 지워지고, 그 시점 이후 지수가 다시 계산됩니다. 되돌릴 수 없습니다.`}
              confirmLabel="삭제"
              onConfirm={() =>
                deleteFetcher.submit(
                  { intent: "delete", id: income.id },
                  { method: "post" },
                )
              }
            />
          </div>
          {deleteError && (
            <p role="alert" className="text-xs text-carix-danger">
              {deleteError}
            </p>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}

export default function RecordsIncome() {
  const { income, bonuses } = useLoaderData<typeof clientLoader>();
  const formFetcher = useFetcher<ActionData>();
  const termsFetcher = useFetcher<ActionData>();
  const bonusFetcher = useFetcher<ActionData>();
  const formRef = useRef<HTMLFormElement>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [grossInput, setGrossInput] = useState("");
  const [netInput, setNetInput] = useState("");
  const [dateInput, setDateInput] = useState("");
  const [changeReasonInput, setChangeReasonInput] = useState("");

  const errors = formFetcher.data?.errors;
  const isSubmitting = formFetcher.state !== "idle";

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

  // 저장이 성공하면(action 이 null 을 돌려준다) 폼을 비운다. 실패하면 값을 남긴다 —
  // 기능 명세 §5 의 "저장 실패 시 입력 보존".
  const [prevState, setPrevState] = useState(formFetcher.state);
  if (formFetcher.state !== prevState) {
    setPrevState(formFetcher.state);
    if (formFetcher.state === "idle" && formFetcher.data === null) {
      setEditingId(null);
      setGrossInput("");
      setNetInput("");
      setDateInput("");
      setChangeReasonInput("");
    }
  }

  const handleEdit = (id: string) => {
    const target = income.find((item) => item.id === id);
    if (!target) return;
    setEditingId(id);
    setGrossInput(Math.round(target.grossSalary / 10_000).toLocaleString());
    setNetInput(Math.round(target.netSalary / 10_000).toLocaleString());
    setDateInput(target.effectiveDate);
    // 표시값(changeReason)이 아니라 컬럼 원값을 채운다 — INITIAL 로 가려진
    // 원값을 못 보면 저장 한 번에 그 값이 지워진다.
    setChangeReasonInput(target.selectedChangeReason ?? "");
  };

  const formatDigits = (value: string) => {
    const digits = value.replace(/[^0-9]/g, "");
    return digits ? Number(digits).toLocaleString() : "";
  };

  return (
    <div className="flex flex-col gap-8">
      <PageSection title="소득 이력">
        {income.length === 0 ? (
          <EmptyState
            icon="📋"
            title="등록된 연봉 이력이 없습니다"
            description="아래에서 세전 연봉과 월 실수령액, 적용 시작일을 입력해 첫 이력을 추가하세요."
          />
        ) : (
          // 가로 스크롤은 Table 이 갖는다(c13)
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>유효 시작일</TableHead>
                <TableHead>세전 연봉</TableHead>
                <TableHead>월 실수령액</TableHead>
                <TableHead>변경 사유</TableHead>
                <TableHead>상여 조건</TableHead>
                <TableHead>작업</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {income.map((item) => (
                <IncomeRow key={item.id} income={item} onEdit={handleEdit} />
              ))}
            </TableBody>
          </Table>
        )}
      </PageSection>

      {income.length > 0 && (
        <PageSection title="상여 조건">
          <Card>
            {/* 저장 뒤 loader 가 갱신한 행 값으로 폼을 다시 채운다 — 값이 바뀌면 리마운트. */}
            <BonusTermsForm
              key={income
                .map((i) => `${i.id}:${i.bonusIncluded}:${i.fixedBonusAnnual}`)
                .join()}
              incomes={income}
              isSubmitting={termsFetcher.state !== "idle"}
              errors={termsFetcher.data?.errors}
              onSubmit={(input) =>
                termsFetcher.submit(
                  {
                    intent: "bonus-terms",
                    applyFromIncomeId: input.applyFromIncomeId,
                    bonusIncluded: String(input.bonusIncluded),
                    fixedBonusAnnual:
                      input.fixedBonusAnnual === null
                        ? ""
                        : String(input.fixedBonusAnnual),
                  },
                  { method: "post" },
                )
              }
            />
          </Card>
        </PageSection>
      )}

      <PageSection title="성과급 기록">
        <Card>
          <PerformanceBonusList
            key={bonuses.map((b) => b.id).join()}
            bonuses={bonuses}
            isSubmitting={bonusFetcher.state !== "idle"}
            errors={bonusFetcher.data?.errors}
            onCreate={(input) =>
              bonusFetcher.submit(
                {
                  intent: "bonus-create",
                  paidMonth: input.paidMonth,
                  amount: String(input.amount),
                },
                { method: "post" },
              )
            }
            onDelete={(id) =>
              bonusFetcher.submit(
                { intent: "bonus-delete", id },
                { method: "post" },
              )
            }
          />
        </Card>
      </PageSection>

      <PageSection title="소득 추가">
        <Card>
          <formFetcher.Form
            method="post"
            ref={formRef}
            key={editingId ?? "new"}
            className="flex flex-col gap-4"
          >
            <input
              type="hidden"
              name="intent"
              value={editingId ? "update" : "create"}
            />
            {editingId && <input type="hidden" name="id" value={editingId} />}

            <Field
              id="grossSalary"
              label="세전 연봉 (만원)"
              hint="1,000만 원 ~ 300,000만 원 사이로 입력해 주세요. (예: 5,000)"
              error={errors?.grossSalary}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="grossSalary"
                  type="text"
                  inputMode="numeric"
                  placeholder="예: 5,000"
                  value={grossInput}
                  onChange={(e) => setGrossInput(formatDigits(e.target.value))}
                />
              )}
            </Field>

            <Field
              id="netSalary"
              label="월 실수령액 (만원)"
              hint="구매력 지수(PPI) 계산에 쓰이므로 필수입니다. (50만 원 ~ 20,000만 원, 예: 350)"
              error={errors?.netSalary}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="netSalary"
                  type="text"
                  inputMode="numeric"
                  placeholder="예: 350"
                  value={netInput}
                  onChange={(e) => setNetInput(formatDigits(e.target.value))}
                />
              )}
            </Field>

            <Field
              id="effectiveDate"
              label="연봉 적용 시작일"
              hint="해당 연봉 계약이 적용되기 시작한 날짜입니다. 미래 날짜는 입력할 수 없습니다."
              error={errors?.effectiveDate}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="effectiveDate"
                  type="text"
                  placeholder="YYYY-MM-DD (예: 2024-03-01)"
                  value={dateInput}
                  onChange={(e) => setDateInput(e.target.value)}
                />
              )}
            </Field>

            {/* 이력이 0건이면 그리지 않는다 — 그 행은 서버가 INITIAL 로 확정하고
            사용자에게 묻지 않는다(기능 명세 US-O3). 컨트롤드 컴포넌트다 — 생성→
            생성 경로는 폼 key(editingId)가 안 바뀌어 리마운트가 없으므로,
            defaultValue 로는 이전 선택이 다음 저장에 그대로 실린다. */}
            {income.length > 0 && (
              <Field
                id="changeReason"
                label="변경 사유"
                hint={INCOME_CHANGE_REASON_HINT}
                error={errors?.changeReason}
              >
                {(aria) => (
                  <NativeSelect
                    {...aria}
                    name="changeReason"
                    value={changeReasonInput}
                    onChange={(e) => setChangeReasonInput(e.target.value)}
                    placeholder="선택 안 함"
                    options={INCOME_CHANGE_REASONS}
                  />
                )}
              </Field>
            )}

            {errors?._form && (
              <p role="alert" className="text-sm text-carix-danger">
                {errors._form}
              </p>
            )}

            <div className="flex items-center gap-2">
              <Button type="submit" disabled={isSubmitting}>
                저장
              </Button>
              {editingId && (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setEditingId(null);
                    setGrossInput("");
                    setNetInput("");
                    setDateInput("");
                    setChangeReasonInput("");
                  }}
                >
                  취소
                </Button>
              )}
              <p className="text-xs text-carix-text/60">
                저장하면 해당 시점 이후 지수에 반영됩니다.
              </p>
            </div>
          </formFetcher.Form>
        </Card>
      </PageSection>
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
      title="소득 이력을 불러오지 못했습니다"
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

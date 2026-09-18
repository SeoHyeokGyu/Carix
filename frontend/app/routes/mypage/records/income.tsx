import { useEffect, useRef, useState } from "react";
import { redirect, useFetcher, useLoaderData } from "react-router";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "../../../components/onboarding/Field";
import { isApiError, toUserMessage } from "../../../lib/apiError";
import {
  createIncome,
  deleteIncome,
  listIncome,
  updateIncome,
  type IncomeResponse,
} from "../../../lib/api/income";
import { type ApiError, UnauthorizedError } from "../../../lib/coreApi";
import { toFieldErrors, type FieldErrors } from "../../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../../lib/focusFirstInvalidField";
import { LOGIN_PATH } from "../../../lib/routeGuards";
import {
  incomeSchema,
  INCOME_FIELD_MESSAGES,
} from "../../../lib/schemas/income";

type ActionData = { errors: FieldErrors } | null;

/** 유효 시작일 중복은 서버가 409 로 알린다 — `_form` 이 아니라 그 필드에 붙여야 한다. */
function isConflict(error: unknown): error is ApiError {
  return isApiError(error) && error.status === 409;
}

export async function clientLoader() {
  try {
    const income = await listIncome();
    // 서버 정렬 순서를 신뢰하지 않고 항상 클라이언트에서 다시 정렬한다.
    return {
      income: [...income].sort((a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate),
      ),
    };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");

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

  const result = incomeSchema.safeParse({
    grossSalary: grossRaw === "" ? NaN : Number(grossRaw) * 10_000,
    netSalary: netRaw === "" ? NaN : Number(netRaw) * 10_000,
    effectiveDate,
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
    <tr className="border-b border-carix-border/60">
      <td className="px-3 py-2">{income.effectiveDate}</td>
      <td className="px-3 py-2">{income.grossSalary.toLocaleString()}원</td>
      <td className="px-3 py-2">{income.netSalary.toLocaleString()}원</td>
      <td className="px-3 py-2">
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
            <deleteFetcher.Form
              method="post"
              className="inline"
              onSubmit={(e) => {
                // M3: skills 탭·onboarding/skills.tsx 와 같은 확인 절차 — 삭제는
                // 되돌릴 수 없다.
                if (
                  !window.confirm(
                    `${income.effectiveDate} 소득 이력을 삭제하시겠습니까?`,
                  )
                ) {
                  e.preventDefault();
                }
              }}
            >
              <input type="hidden" name="intent" value="delete" />
              <input type="hidden" name="id" value={income.id} />
              <Button
                type="submit"
                variant="destructive"
                size="sm"
                disabled={isDeleting}
              >
                삭제
              </Button>
            </deleteFetcher.Form>
          </div>
          {deleteError && (
            <p role="alert" className="text-xs text-destructive">
              {deleteError}
            </p>
          )}
        </div>
      </td>
    </tr>
  );
}

export default function RecordsIncome() {
  const { income } = useLoaderData<typeof clientLoader>();
  const formFetcher = useFetcher<ActionData>();
  const formRef = useRef<HTMLFormElement>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [grossInput, setGrossInput] = useState("");
  const [netInput, setNetInput] = useState("");
  const [dateInput, setDateInput] = useState("");

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
    }
  }

  const handleEdit = (id: string) => {
    const target = income.find((item) => item.id === id);
    if (!target) return;
    setEditingId(id);
    setGrossInput(Math.round(target.grossSalary / 10_000).toLocaleString());
    setNetInput(Math.round(target.netSalary / 10_000).toLocaleString());
    setDateInput(target.effectiveDate);
  };

  const formatDigits = (value: string) => {
    const digits = value.replace(/[^0-9]/g, "");
    return digits ? Number(digits).toLocaleString() : "";
  };

  return (
    <div className="flex flex-col gap-6">
      {income.length === 0 ? (
        <EmptyState
          icon="📋"
          title="등록된 연봉 이력이 없습니다"
          description="아래에서 세전 연봉과 월 실수령액, 적용 시작일을 입력해 첫 이력을 추가하세요."
        />
      ) : (
        <table className="w-full text-left text-sm">
          <thead className="text-carix-text/70">
            <tr>
              <th className="px-3 py-2 font-medium">유효 시작일</th>
              <th className="px-3 py-2 font-medium">세전 연봉</th>
              <th className="px-3 py-2 font-medium">월 실수령액</th>
              <th className="px-3 py-2 font-medium">작업</th>
            </tr>
          </thead>
          <tbody>
            {income.map((item) => (
              <IncomeRow key={item.id} income={item} onEdit={handleEdit} />
            ))}
          </tbody>
        </table>
      )}

      <formFetcher.Form
        method="post"
        ref={formRef}
        key={editingId ?? "new"}
        className="flex flex-col gap-4 rounded-xl border border-carix-border bg-carix-surface p-6"
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

        {errors?._form && (
          <p role="alert" className="text-sm text-destructive">
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
    </div>
  );
}

export function ErrorBoundary() {
  return (
    <EmptyState
      variant="plain"
      tone="danger"
      icon="⚠️"
      title="소득 이력을 불러오지 못했습니다"
      description="잠시 후 다시 시도해 주세요. 다른 탭은 그대로 열립니다."
    />
  );
}

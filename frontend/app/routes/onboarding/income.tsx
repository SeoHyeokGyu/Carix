import { useEffect, useRef, useState } from "react";
import {
  Form,
  Link,
  redirect,
  useActionData,
  useFetcher,
  useLoaderData,
  useNavigation,
} from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import { onboardingStepLabel } from "../../components/onboarding/StepIndicator";
import {
  createIncome,
  deleteIncome,
  type IncomeResponse,
  listIncome,
  updateIncome,
} from "../../lib/api/income";
import { isApiError, toUserMessage } from "../../lib/apiError";
import { type ApiError, UnauthorizedError } from "../../lib/coreApi";
import { type FieldErrors, toFieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { advanceOnboarding } from "../../lib/onboardingApi";
import { isReturnToReview, REVIEW_PATH } from "../../lib/reviewReturn";
import { isForwardStep, LOGIN_PATH } from "../../lib/routeGuards";
import {
  INCOME_CHANGE_REASON_HINT,
  INCOME_CHANGE_REASONS,
  INCOME_FIELD_MESSAGES,
  incomeChangeReasonCell,
  incomeSchema,
} from "../../lib/schemas/income";
import { requireSession, setSession } from "../../lib/session";

type ActionData = { errors: FieldErrors } | null;

/** 유효 시작일 중복(설계 §8-a) 은 서버가 409 로 알린다 — 다른 오류와 달리 특정
 * 필드(effectiveDate)에 붙여야 하므로 `toUserMessage` 의 일반 `_form` 경로를
 * 타지 않고 여기서 먼저 갈라낸다. */
function isConflict(error: unknown): error is ApiError {
  return isApiError(error) && error.status === 409;
}

export async function clientLoader() {
  try {
    const income = await listIncome();
    // 화면·테스트 모두 "유효 시작일 내림차순"을 기대한다 — 서버 정렬 순서를
    // 신뢰하지 않고 항상 클라이언트에서 다시 정렬한다.
    return {
      income: [...income].sort((a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate),
      ),
    };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
    // 조회 실패는 이 화면에 그릴 자리가 없다 — ErrorBoundary 몫이다(S-05 와 동일 판단).
    throw error;
  }
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");

  if (intent === "next") {
    // 이 화면은 행 단위로 이미 저장된 상태라("추가"·"수정" 각각이 즉시 서버에
    // 반영된다) "다음" 은 검증할 폼 필드가 없다 — 온보딩 단계만 넘긴다.
    try {
      // 이미 이 단계를 지난 사용자가 검토 화면에서 돌아와 다시 "다음"을 누르는
      // 정당한 흐름까지 서버 단계를 되돌리면(profile.tsx 와 같은 이유), 아직
      // 화면이 없는 단계로 사용자가 떠밀려 갇힌다. 실제로 앞으로 가는 경우에만
      // 서버 단계·세션 캐시를 갱신한다.
      const session = await requireSession();
      if (
        session.status === "authenticated" &&
        isForwardStep(session.onboardingStep, "EXPENSE")
      ) {
        await advanceOnboarding("EXPENSE");
        setSession({ ...session, onboardingStep: "EXPENSE" });
      }

      return redirect(
        isReturnToReview(request.url) ? REVIEW_PATH : "/onboarding/expense",
      );
    } catch (error) {
      if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
      return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
    }
  }

  if (intent === "delete") {
    const id = formData.get("id");
    try {
      if (typeof id === "string" && id) await deleteIncome(id);
      return null;
    } catch (error) {
      if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
      return { errors: { _form: toUserMessage(error) } } satisfies ActionData;
    }
  }

  // "create" 또는 "update" — 소득 이력은 부분 저장 개념이 없다(행마다 완결된
  // 레코드다), 그래서 항상 전체 스키마로 검증한다.
  // 사용자가 화면에서 만원 단위(천 단위 콤마 포함)로 입력하므로 콤마를 제거하고
  // 원 단위(* 10,000)로 환산해 스키마로 검증한다.
  const grossRaw = String(formData.get("grossSalary") ?? "")
    .replace(/,/g, "")
    .trim();
  const netRaw = String(formData.get("netSalary") ?? "")
    .replace(/,/g, "")
    .trim();
  const effectiveDate = String(formData.get("effectiveDate") ?? "").trim();
  const changeReasonRaw = String(formData.get("changeReason") ?? "").trim();

  const grossSalary = grossRaw === "" ? NaN : Number(grossRaw) * 10_000;
  const netSalary = netRaw === "" ? NaN : Number(netRaw) * 10_000;

  const result = incomeSchema.safeParse({
    grossSalary,
    netSalary,
    effectiveDate,
    // 빈 문자열은 "고르지 않음" 이다 — undefined 로 보내야 서버가 null 로 저장한다.
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
  isEditing: boolean;
  onEdit: (id: string) => void;
}

function IncomeRow({ income, isEditing, onEdit }: IncomeRowProps) {
  const deleteFetcher = useFetcher<ActionData>();
  const isDeleting = deleteFetcher.state !== "idle";
  const deleteError = deleteFetcher.data?.errors?._form;

  // 만원 단위 및 천 단위 콤마 포맷팅
  const grossInTenThousand = Math.round(
    income.grossSalary / 10_000,
  ).toLocaleString();
  const netInTenThousand = Math.round(
    income.netSalary / 10_000,
  ).toLocaleString();

  return (
    <TableRow aria-current={isEditing ? "true" : undefined}>
      <TableCell>{income.effectiveDate}</TableCell>
      <TableCell>{grossInTenThousand}만 원</TableCell>
      <TableCell>{netInTenThousand}만 원</TableCell>
      {/* 두 필드가 갈리는 것은 최소 유효일 행뿐이고, 그때는 둘 다 낸다 — 폼은
          selectedChangeReason 하나만 쓴다. */}
      <TableCell>
        {incomeChangeReasonCell(
          income.changeReason,
          income.selectedChangeReason,
        )}
      </TableCell>
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
            <deleteFetcher.Form method="post" className="inline">
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
            <p role="alert" className="text-xs text-carix-danger">
              {deleteError}
            </p>
          )}
        </div>
      </TableCell>
    </TableRow>
  );
}

export default function Income() {
  const { income } = useLoaderData<typeof clientLoader>();
  const formFetcher = useFetcher<ActionData>();
  const nextActionData = useActionData<ActionData>();
  const navigation = useNavigation();
  const [editingId, setEditingId] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const errors = formFetcher.data?.errors;
  const nextErrors = nextActionData?.errors;
  const editingIncome = editingId
    ? (income.find((item) => item.id === editingId) ?? null)
    : null;
  const isSubmittingForm = formFetcher.state !== "idle";
  const isAdvancing =
    navigation.state === "submitting" || navigation.state === "loading";

  // 실시간 천 단위 콤마 입력 상태 (만원 단위)
  const [grossInput, setGrossInput] = useState(
    editingIncome
      ? Math.round(editingIncome.grossSalary / 10_000).toLocaleString()
      : "",
  );
  const [netInput, setNetInput] = useState(
    editingIncome
      ? Math.round(editingIncome.netSalary / 10_000).toLocaleString()
      : "",
  );
  const [effectiveDateInput, setEffectiveDateInput] = useState(
    editingIncome?.effectiveDate ?? "",
  );
  // 표시값(changeReason)이 아니라 컬럼 원값을 채운다 — INITIAL 로 가려진
  // 원값을 못 보면 저장 한 번에 그 값이 지워진다.
  const [changeReasonInput, setChangeReasonInput] = useState(
    editingIncome?.selectedChangeReason ?? "",
  );

  const [prevEditingId, setPrevEditingId] = useState<string | null>(editingId);
  if (editingId !== prevEditingId) {
    setPrevEditingId(editingId);
    if (editingIncome) {
      setGrossInput(
        Math.round(editingIncome.grossSalary / 10_000).toLocaleString(),
      );
      setNetInput(
        Math.round(editingIncome.netSalary / 10_000).toLocaleString(),
      );
      setEffectiveDateInput(editingIncome.effectiveDate);
      setChangeReasonInput(editingIncome.selectedChangeReason ?? "");
    } else {
      setGrossInput("");
      setNetInput("");
      setEffectiveDateInput("");
      setChangeReasonInput("");
    }
  }

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

  const [prevFormFetcherState, setPrevFormFetcherState] = useState(
    formFetcher.state,
  );
  if (formFetcher.state !== prevFormFetcherState) {
    setPrevFormFetcherState(formFetcher.state);
    if (formFetcher.state === "idle" && formFetcher.data === null) {
      setEditingId(null);
      setGrossInput("");
      setNetInput("");
      setEffectiveDateInput("");
      setChangeReasonInput("");
    }
  }

  // 숫자 입력 시 천 단위 콤마 자동 포맷팅
  const handleGrossChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const rawDigits = e.target.value.replace(/[^0-9]/g, "");
    if (!rawDigits) {
      setGrossInput("");
      return;
    }
    setGrossInput(Number(rawDigits).toLocaleString());
  };

  const handleNetChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const rawDigits = e.target.value.replace(/[^0-9]/g, "");
    if (!rawDigits) {
      setNetInput("");
      return;
    }
    setNetInput(Number(rawDigits).toLocaleString());
  };

  const [nextBlockedMessage, setNextBlockedMessage] = useState<string | null>(
    null,
  );

  // 입력칸에 값을 남긴 채 넘어가면 그 값은 저장되지 않고 사라진다 — 「추가」와
  // 「다음」이 따로라 사용자가 「다음」 하나로 저장된다고 여기기 쉽다.
  const handleNextSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    const hasPendingInput =
      grossInput !== "" || netInput !== "" || effectiveDateInput !== "";
    const blocked = hasPendingInput
      ? "입력한 연봉을 「추가」로 저장한 뒤 넘어가 주세요."
      : income.length === 0
        ? "연봉을 하나 이상 추가해야 넘어갈 수 있습니다."
        : null;
    setNextBlockedMessage(blocked);
    if (blocked) event.preventDefault();
  };

  const handleCancel = () => {
    setEditingId(null);
    setGrossInput("");
    setNetInput("");
    setEffectiveDateInput("");
    setChangeReasonInput("");
  };

  // 입력된 만원 단위를 원 단위로 계산해주는 헬퍼
  const getWonHint = (val: string) => {
    const digits = val.replace(/,/g, "");
    if (!digits) return null;
    const won = Number(digits) * 10_000;
    if (isNaN(won) || won <= 0) return null;
    return `= ${won.toLocaleString()}원`;
  };

  const grossWonHint = getWonHint(grossInput);
  const netWonHint = getWonHint(netInput);

  return (
    <PageFrame width="narrow">
      <PageHeader
        title={onboardingStepLabel(2)}
        description="지금 받는 연봉을 넣고 「추가」를 눌러 주세요. 물가와 비교하는 기준이 됩니다."
      />
      <Card>
        <CardContent>
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
              hint={
                grossWonHint
                  ? `${grossInput}만 원 (${grossWonHint})`
                  : "1천만~30억 원 사이로 입력해 주세요. (예: 5,000)"
              }
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
                  onChange={handleGrossChange}
                />
              )}
            </Field>

            {/* 서버 IncomeRequest 가 @NotNull 이라 필수로 받는다 — 0 을 대신 넣으면
                미입력과 구분이 안 돼 PPI 가 세전 기반 추정으로 잘못 떨어진다. "안 넣어도
                된다"는 인상을 주면 안 되므로 필수라는 사실과 이유를 힌트로 남긴다 —
                Field 의 hint 는 aria-describedby 로 오류 문단과 함께 연결되어, 오류가
                나도 이 안내가 밀려나지 않는다. */}
            <Field
              id="netSalary"
              label="월 실수령액 (만원)"
              hint={
                netWonHint
                  ? `${netInput}만 원 (${netWonHint})`
                  : "물가 대비 실제 생활비 여력을 계산하는 데 씁니다. 50만~2억 원 사이로 입력해 주세요. (예: 350)"
              }
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
                  onChange={handleNetChange}
                />
              )}
            </Field>

            <Field
              id="effectiveDate"
              label="연봉 적용 시작일"
              hint="이 연봉을 받기 시작한 날입니다. 예: 2024-03-01. 정확히 모르면 그 달 1일로 넣으세요."
              error={errors?.effectiveDate}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="effectiveDate"
                  type="text"
                  placeholder="YYYY-MM-DD (예: 2024-03-01)"
                  value={effectiveDateInput}
                  onChange={(e) => setEffectiveDateInput(e.target.value)}
                />
              )}
            </Field>

            {/* 이력이 0건이면 그리지 않는다 — 그 행은 서버가 INITIAL 로 확정하고
                사용자에게 묻지 않는다(기능 명세 US-O3). 컨트롤드 컴포넌트다 —
                생성→생성 경로는 폼 key(editingId)가 안 바뀌어 리마운트가 없으므로,
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

            <div className="flex flex-wrap gap-3">
              <Button type="submit" disabled={isSubmittingForm}>
                {editingId ? "저장" : "추가"}
              </Button>
              {editingId && (
                <Button
                  type="button"
                  variant="outline"
                  disabled={isSubmittingForm}
                  onClick={handleCancel}
                >
                  취소
                </Button>
              )}
            </div>
          </formFetcher.Form>
        </CardContent>
      </Card>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle as="h2">과거 연봉 이력</CardTitle>
        </CardHeader>
        <CardContent>
          {income.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              아직 추가한 연봉이 없습니다. 위에 입력하고 「추가」를 눌러 주세요.
            </p>
          ) : (
            <Table variant="plain">
              <TableHeader>
                <TableRow>
                  <TableHead>연봉 적용 시작일</TableHead>
                  <TableHead>세전 연봉</TableHead>
                  <TableHead>월 실수령액</TableHead>
                  <TableHead>변경 사유</TableHead>
                  <TableHead>작업</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {income.map((item) => (
                  <IncomeRow
                    key={item.id}
                    income={item}
                    isEditing={item.id === editingId}
                    onEdit={setEditingId}
                  />
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <div className="mt-6 flex flex-col gap-3">
        {(nextBlockedMessage ?? nextErrors?._form) && (
          <p role="alert" className="text-sm text-carix-danger">
            {nextBlockedMessage ?? nextErrors?._form}
          </p>
        )}
        {/* 와이어프레임(02-income.html) 순서를 그대로 따른다 — "이전" 이 먼저, "다음"
            이 그 다음(강조). "이전" 은 순수 내비게이션 Link 라 이 화면의 어떤
            <Form> 에도 속하지 않으므로, 어느 입력에서 Enter 를 눌러도 관여하지
            않는다 — DOM 순서를 CSS 로 뒤집을 필요가 없다. guardOnboarding 이
            이제 앞선 단계로의 이동을 허용하므로(routeGuards.ts) 이 링크가 더 이상
            죽은 버튼이 아니다. */}
        <div className="flex flex-wrap gap-3">
          <Link
            to="/onboarding/profile"
            className={buttonVariants({ variant: "outline" })}
          >
            이전
          </Link>
          <Form method="post" onSubmit={handleNextSubmit}>
            <input type="hidden" name="intent" value="next" />
            <Button type="submit" disabled={isAdvancing}>
              다음
            </Button>
          </Form>
        </div>
      </div>
    </PageFrame>
  );
}

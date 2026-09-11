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
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Field } from "../../components/onboarding/Field";
import { StepBadges } from "../../components/onboarding/StepBadges";
import { isApiError, toUserMessage } from "../../lib/apiError";
import {
  createIncome,
  deleteIncome,
  listIncome,
  updateIncome,
  type IncomeResponse,
} from "../../lib/api/income";
import { type ApiError, UnauthorizedError } from "../../lib/coreApi";
import { toFieldErrors, type FieldErrors } from "../../lib/fieldErrors";
import { focusFirstInvalidField } from "../../lib/focusFirstInvalidField";
import { formDataToObject } from "../../lib/formData";
import { advanceOnboarding } from "../../lib/onboardingApi";
import { isForwardStep, LOGIN_PATH } from "../../lib/routeGuards";
import { incomeSchema, INCOME_FIELD_MESSAGES } from "../../lib/schemas/income";
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
      income: [...income].sort((a, b) => b.effectiveDate.localeCompare(a.effectiveDate)),
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
      if (session.status === "authenticated" && isForwardStep(session.onboardingStep, "EXPENSE")) {
        await advanceOnboarding("EXPENSE");
        setSession({ ...session, onboardingStep: "EXPENSE" });
      }

      return redirect("/onboarding/expense");
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
  const raw = formDataToObject(formData, { numeric: ["grossSalary", "netSalary"] });
  const result = incomeSchema.safeParse(raw);
  if (!result.success) {
    return { errors: toFieldErrors(result.error.issues, INCOME_FIELD_MESSAGES) } satisfies ActionData;
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

  return (
    <tr aria-current={isEditing ? "true" : undefined}>
      <td className="px-3 py-2">{income.effectiveDate}</td>
      <td className="px-3 py-2">{income.grossSalary.toLocaleString()}</td>
      <td className="px-3 py-2">{income.netSalary.toLocaleString()}</td>
      <td className="px-3 py-2">
        <div className="flex flex-col gap-1">
          <div className="flex gap-2">
            <Button type="button" variant="outline" size="sm" onClick={() => onEdit(income.id)}>
              수정
            </Button>
            <deleteFetcher.Form method="post" className="inline">
              <input type="hidden" name="intent" value="delete" />
              <input type="hidden" name="id" value={income.id} />
              <Button type="submit" variant="destructive" size="sm" disabled={isDeleting}>
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

export default function Income() {
  const { income } = useLoaderData<typeof clientLoader>();
  const formFetcher = useFetcher<ActionData>();
  const nextActionData = useActionData<ActionData>();
  const navigation = useNavigation();
  const [editingId, setEditingId] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const errors = formFetcher.data?.errors;
  const nextErrors = nextActionData?.errors;
  const editingIncome = editingId ? (income.find((item) => item.id === editingId) ?? null) : null;
  const isSubmittingForm = formFetcher.state !== "idle";
  const isAdvancing = navigation.state === "submitting" || navigation.state === "loading";

  useEffect(() => {
    if (errors) focusFirstInvalidField(formRef.current);
  }, [errors]);

  // 성공한 제출은 항상 null 을 반환하고(실패는 { errors }), 초기값은 undefined 다 —
  // 그래서 "state 가 idle 로 막 돌아왔을 때 data 가 정확히 null" 은 방금 create·
  // update 가 성공했다는 신호로 안전하게 쓸 수 있다(제출 전에는 data 가 undefined
  // 라 걸리지 않고, 실패하면 {errors} 라 역시 걸리지 않는다). 수정 성공 뒤
  // editingId 를 그대로 두면 폼이 계속 "저장"/취소 가 있는 편집 모드에 머물고,
  // 숨은 intent=update·id 필드가 방금 수정한(또는 그 뒤 삭제된) 행의 id 를 계속
  // 가리킨다 — 그 상태로 다시 제출하면 죽은 id 로 update 를 보낸다. create 성공
  // 시에는 editingId 가 이미 null 이라 이 설정이 아무 것도 바꾸지 않는다.
  //
  // useEffect 로 동기화하면 렌더 한 번 더 도는 캐스케이드가 생기므로
  // (react-hooks/set-state-in-effect, dashboard/index.tsx 의 lastGood 과 같은
  // 이유), 렌더링 중에 이전 state 와 비교해 바로 갱신한다(React 공식 패턴 —
  // https://react.dev/learn/you-might-not-need-an-effect).
  const [prevFormFetcherState, setPrevFormFetcherState] = useState(formFetcher.state);
  if (formFetcher.state !== prevFormFetcherState) {
    setPrevFormFetcherState(formFetcher.state);
    if (formFetcher.state === "idle" && formFetcher.data === null) {
      setEditingId(null);
    }
  }

  return (
    <section className="mx-auto w-full max-w-3xl px-6 py-16">
      <StepBadges current={2} />

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>소득 관리</CardTitle>
          <CardDescription>
            동료집단 비교와 구매력 계산을 위해 세전 연봉과 월 실수령액, 유효 시작일을
            입력해 주세요.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <formFetcher.Form
            method="post"
            ref={formRef}
            key={editingId ?? "new"}
            className="flex flex-col gap-4"
          >
            <input type="hidden" name="intent" value={editingId ? "update" : "create"} />
            {editingId && <input type="hidden" name="id" value={editingId} />}

            <Field id="grossSalary" label="세전 연봉 (원)" error={errors?.grossSalary}>
              {(aria) => (
                <Input
                  {...aria}
                  name="grossSalary"
                  type="number"
                  inputMode="numeric"
                  defaultValue={editingIncome?.grossSalary ?? ""}
                />
              )}
            </Field>

            {/* 명세·와이어프레임은 선택이라 하지만 서버 IncomeRequest 가 @NotNull 이라
                필수로 받는다 — 0 을 대신 넣으면 미입력과 구분이 안 돼 PPI 가 세전
                기반 추정으로 잘못 떨어진다. "안 넣어도 된다"는 인상을 주면 안 되므로
                필수라는 사실과 이유를 힌트로 남긴다 — Field 의 hint 는 aria-describedby
                로 오류 문단과 함께 연결되어, 오류가 나도 이 안내가 밀려나지 않는다. */}
            <Field
              id="netSalary"
              label="월 실수령액 (원)"
              hint="구매력 지수(PPI) 계산에 정확히 반영하기 위해 필수로 입력해야 합니다."
              error={errors?.netSalary}
            >
              {(aria) => (
                <Input
                  {...aria}
                  name="netSalary"
                  type="number"
                  inputMode="numeric"
                  defaultValue={editingIncome?.netSalary ?? ""}
                />
              )}
            </Field>

            <Field id="effectiveDate" label="유효 시작일" error={errors?.effectiveDate}>
              {(aria) => (
                <Input
                  {...aria}
                  name="effectiveDate"
                  type="text"
                  placeholder="YYYY-MM-DD"
                  defaultValue={editingIncome?.effectiveDate ?? ""}
                />
              )}
            </Field>

            {errors?._form && (
              <p role="alert" className="text-sm text-destructive">
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
                  onClick={() => setEditingId(null)}
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
          <CardTitle>과거 연봉 이력</CardTitle>
        </CardHeader>
        <CardContent>
          {income.length === 0 ? (
            <p className="text-sm text-muted-foreground">등록된 이력이 없습니다.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="px-3 py-2 font-medium">유효 시작일</th>
                    <th className="px-3 py-2 font-medium">세전 연봉</th>
                    <th className="px-3 py-2 font-medium">월 실수령액</th>
                    <th className="px-3 py-2 font-medium">작업</th>
                  </tr>
                </thead>
                <tbody>
                  {income.map((item) => (
                    <IncomeRow
                      key={item.id}
                      income={item}
                      isEditing={item.id === editingId}
                      onEdit={setEditingId}
                    />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="mt-6 flex flex-col gap-3">
        {nextErrors?._form && (
          <p role="alert" className="text-sm text-destructive">
            {nextErrors._form}
          </p>
        )}
        {/* 와이어프레임(02-income.html) 순서를 그대로 따른다 — "이전" 이 먼저, "다음"
            이 그 다음(강조). "이전" 은 순수 내비게이션 Link 라 이 화면의 어떤
            <Form> 에도 속하지 않으므로, 어느 입력에서 Enter 를 눌러도 관여하지
            않는다 — DOM 순서를 CSS 로 뒤집을 필요가 없다. guardOnboarding 이
            이제 앞선 단계로의 이동을 허용하므로(routeGuards.ts) 이 링크가 더 이상
            죽은 버튼이 아니다. */}
        <div className="flex flex-wrap gap-3">
          <Link to="/onboarding/profile" className={buttonVariants({ variant: "outline" })}>
            이전
          </Link>
          <Form method="post">
            <input type="hidden" name="intent" value="next" />
            <Button type="submit" disabled={isAdvancing}>
              다음
            </Button>
          </Form>
        </div>
      </div>
    </section>
  );
}

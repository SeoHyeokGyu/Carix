import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { useState, useEffect } from "react";
import {
  Form,
  redirect,
  useActionData,
  useNavigation,
  useSubmit,
} from "react-router";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { toUserMessage } from "../../lib/apiError";
import { deleteAccount, logout, logoutAll } from "../../lib/authApi";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetOnboarding } from "../../lib/onboardingApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { requireSession, setSession } from "../../lib/session";

interface ActionData {
  error?: string;
  successMessage?: string;
  intent?: string;
}

export async function clientAction({ request }: { request: Request }) {
  const formData = await request.formData();
  const intent = formData.get("intent");

  if (intent === "reset-onboarding") {
    try {
      await resetOnboarding();
      const session = await requireSession();
      if (session.status === "authenticated") {
        setSession({ ...session, onboardingStep: "PROFILE" });
      }
      return redirect("/onboarding/profile");
    } catch (error) {
      if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
      return {
        error: toUserMessage(error),
        intent: "reset-onboarding",
      } satisfies ActionData;
    }
  }

  // 상단 계정 메뉴(AccountMenu)가 이 화면 action 으로 보낸다 — 현재 세션만 폐기한다.
  if (intent === "logout") {
    try {
      await logout();
      setSession({ status: "anonymous" });
      return redirect(LOGIN_PATH);
    } catch (error) {
      return {
        error: toUserMessage(error),
        intent: "logout",
      } satisfies ActionData;
    }
  }

  if (intent === "logout-all") {
    try {
      await logoutAll();
      setSession({ status: "anonymous" });
      return redirect(LOGIN_PATH);
    } catch (error) {
      return {
        error: toUserMessage(error),
        intent: "logout-all",
      } satisfies ActionData;
    }
  }

  if (intent === "delete-account") {
    const email = String(formData.get("email") ?? "").trim();
    if (!email) {
      return {
        error: "계정 삭제를 확인하기 위해 이메일을 입력해 주세요.",
        intent: "delete-account",
      } satisfies ActionData;
    }

    try {
      await deleteAccount(email);
      await logoutAll();
      setSession({ status: "anonymous" });
      return redirect(`${LOGIN_PATH}?deleted=true`);
    } catch (error) {
      if (error instanceof UnauthorizedError) throw redirect(LOGIN_PATH);
      return {
        error: toUserMessage(error),
        intent: "delete-account",
      } satisfies ActionData;
    }
  }

  return null;
}

function useSafeActionData<T>() {
  try {
    return useActionData<T>();
  } catch {
    return undefined;
  }
}

function useSafeNavigation() {
  try {
    return useNavigation();
  } catch {
    return { state: "idle" } as unknown as ReturnType<typeof useNavigation>;
  }
}

export default function MypageProfile() {
  const actionData = useSafeActionData<typeof clientAction>();
  const navigation = useSafeNavigation();
  const isSubmitting =
    navigation.state === "submitting" || navigation.state === "loading";
  const submit = useSubmit();

  const [currentEmail, setCurrentEmail] = useState<string | null>(null);
  const [deleteEmailInput, setDeleteEmailInput] = useState("");
  const [isDeleteConfirmed, setIsDeleteConfirmed] = useState(false);

  useEffect(() => {
    void requireSession().then((session) => {
      if (session.status === "authenticated" && session.email) {
        setCurrentEmail(session.email);
      }
    });
  }, []);

  return (
    <PageFrame width="narrow">
      <PageHeader
        title="설정"
        description="세션을 관리하고 온보딩 데이터 및 계정을 관리합니다."
      />

      {currentEmail && (
        <div className="rounded-lg bg-carix-surface border border-carix-border p-3.5 text-xs text-carix-text/80 flex items-center justify-between">
          <span>
            로그인 계정: <strong>{currentEmail}</strong>
          </span>
          <span className="text-[11px] text-carix-text/50">세션 활성 상태</span>
        </div>
      )}

      <div className="mt-8 flex flex-col gap-8">
        {/* 1. 세션 관리 카드 (US-C1) */}
        <Card data-page-section="">
          <CardHeader>
            <CardTitle as="h2">세션 관리</CardTitle>
            <CardDescription>
              현재 기기를 포함하여 모든 브라우저 및 기기에서의 로그인 세션을
              일괄 종료합니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-carix-text/80">
              공용 PC나 다른 환경에서 로그인한 기록이 남아있을 때 모든 기기에서
              안전하게 로그아웃할 수 있습니다.
            </p>

            {(actionData?.intent === "logout" ||
              actionData?.intent === "logout-all") &&
              actionData.error && (
                <p role="alert" className="text-sm text-destructive">
                  {actionData.error}
                </p>
              )}

            <Form method="post">
              <Button
                type="submit"
                variant="outline"
                name="intent"
                value="logout-all"
                disabled={isSubmitting}
              >
                모든 기기에서 로그아웃
              </Button>
            </Form>
          </CardContent>
        </Card>

        {/* 2. 온보딩 재설정 카드 */}
        <Card data-page-section="">
          <CardHeader>
            <CardTitle as="h2">온보딩 재설정</CardTitle>
            <CardDescription>
              초기 설정(프로필, 소득, 소비, 스킬)을 처음부터 다시 검토하고
              수정합니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <p className="text-sm text-carix-text/80">
              온보딩을 다시 시작하더라도 기존에 등록하신 프로필, 소득 내역, 소비
              설정, 스킬 목록은 삭제되지 않고 각 단계에 그대로 유지됩니다. 처음
              단계부터 순서대로 확인하며 필요한 항목을 수정하실 수 있습니다.
            </p>

            {actionData?.intent === "reset-onboarding" && actionData.error && (
              <p role="alert" className="text-sm text-destructive">
                {actionData.error}
              </p>
            )}

            <ConfirmDialog
              trigger={
                <Button type="button" variant="outline" disabled={isSubmitting}>
                  온보딩 처음부터 다시 시작
                </Button>
              }
              title="온보딩을 처음부터 다시 할까요?"
              description="입력한 프로필·소득·소비·스킬은 그대로 남습니다. 다만 온보딩을 다시 마칠 때까지 판정 화면에 들어갈 수 없습니다."
              confirmLabel="다시 시작"
              tone="default"
              onConfirm={() =>
                submit({ intent: "reset-onboarding" }, { method: "post" })
              }
            />
          </CardContent>
        </Card>

        {/* 3. 계정 및 개인 데이터 완전 삭제 카드 (US-C2) */}
        <Card data-page-section="" className="border-destructive/30">
          <CardHeader>
            <CardTitle as="h2" className="text-destructive">
              회원 탈퇴 및 개인 데이터 완전 삭제
            </CardTitle>
            <CardDescription>
              계정을 영구적으로 탈퇴하고 관련된 모든 개인 데이터를 완전히
              삭제합니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div className="rounded-lg bg-destructive/10 border border-destructive/20 p-4 text-xs text-destructive space-y-1.5">
              <div className="font-bold flex items-center gap-1.5 text-sm">
                ⚠️ 삭제되는 데이터 안내
              </div>
              <ul className="list-disc list-inside space-y-0.5 text-destructive/90">
                <li>커리어 프로필 (직무, 연차, 지역, 고용형태 등)</li>
                <li>소득 내역 및 지출 카테고리 설정 이력</li>
                <li>등록된 모든 보유 스킬 목록</li>
                <li>산출된 월별 지수 스냅샷 및 시뮬레이션 시나리오</li>
                <li>로그인 세션 및 회원 계정 정보</li>
              </ul>
              <p className="mt-2 text-[11px] text-carix-text/60">
                * 탈퇴가 완료된 데이터는 복구할 수 없습니다. 단, 시장 통계
                분석용 공용 집계 데이터는 영향을 받지 않습니다.
              </p>
            </div>

            {actionData?.intent === "delete-account" && actionData.error && (
              <p
                role="alert"
                className="text-sm text-destructive font-semibold"
              >
                {actionData.error}
              </p>
            )}

            <Form method="post" className="space-y-4 pt-2">
              <div className="space-y-1.5">
                <Label
                  htmlFor="delete-email-confirm"
                  className="text-xs font-semibold text-carix-text"
                >
                  탈퇴 확인을 위해 이메일 주소를 입력해 주세요:
                </Label>
                <Input
                  id="delete-email-confirm"
                  name="email"
                  type="email"
                  placeholder={currentEmail ?? "user@example.com"}
                  value={deleteEmailInput}
                  onChange={(e) => setDeleteEmailInput(e.target.value)}
                  disabled={isSubmitting}
                  required
                />
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="confirm-delete-checkbox"
                  checked={isDeleteConfirmed}
                  onChange={(e) => setIsDeleteConfirmed(e.target.checked)}
                  className="rounded border-carix-border text-destructive focus:ring-destructive"
                  disabled={isSubmitting}
                />
                <label
                  htmlFor="confirm-delete-checkbox"
                  className="text-xs text-carix-text/80 cursor-pointer select-none"
                >
                  안내 사항을 모두 확인하였으며, 데이터 영구 삭제에 동의합니다.
                </label>
              </div>

              <Button
                type="submit"
                variant="destructive"
                name="intent"
                value="delete-account"
                disabled={
                  isSubmitting || !isDeleteConfirmed || !deleteEmailInput.trim()
                }
              >
                {isSubmitting
                  ? "삭제 처리 중..."
                  : "회원 탈퇴 및 모든 데이터 삭제"}
              </Button>
            </Form>
          </CardContent>
        </Card>
      </div>
    </PageFrame>
  );
}

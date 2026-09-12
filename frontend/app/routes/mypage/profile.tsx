import { Form, redirect, useActionData, useNavigation } from "react-router";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { toUserMessage } from "../../lib/apiError";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetOnboarding } from "../../lib/onboardingApi";
import { LOGIN_PATH } from "../../lib/routeGuards";
import { requireSession, setSession } from "../../lib/session";

interface ActionData {
  error?: string;
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
      return { error: toUserMessage(error) } satisfies ActionData;
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

  return (
    <section className="mx-auto w-full max-w-3xl px-6 py-10">
      <div className="flex items-center gap-2">
        <span className="rounded bg-carix-info/10 px-2 py-0.5 text-xs font-semibold text-carix-info">
          S-13
        </span>
        <h1 className="font-display text-2xl font-bold text-carix-text sm:text-3xl">
          설정
        </h1>
      </div>
      <p className="mt-2 text-sm text-carix-text/70">
        세션을 관리하고 계정과 온보딩 데이터를 관리합니다.
      </p>

      <div className="mt-8 flex flex-col gap-6">
        <Card>
          <CardHeader>
            <CardTitle>온보딩 재설정</CardTitle>
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

            {actionData?.error && (
              <p role="alert" className="text-sm text-destructive">
                {actionData.error}
              </p>
            )}

            <Form method="post">
              <Button
                type="submit"
                name="intent"
                value="reset-onboarding"
                disabled={isSubmitting}
              >
                온보딩 처음부터 다시 시작
              </Button>
            </Form>
          </CardContent>
        </Card>

        <Card className="border-destructive/30">
          <CardHeader>
            <CardTitle className="text-destructive">
              계정 및 데이터 관리
            </CardTitle>
            <CardDescription>
              세션을 관리하고 계정과 개인 데이터를 전부 삭제합니다.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-carix-text/70">
              계정 삭제 및 데이터 초기화 기능은 향후 지원될 예정입니다.
            </p>
          </CardContent>
        </Card>
      </div>
    </section>
  );
}

import { useLoaderData, useNavigate, useSearchParams } from "react-router";

import { TextLink } from "@/components/ui/text-link";

import { AuthForm } from "../../components/auth/AuthForm";
import { SessionPending } from "../../components/layout/SessionPending";
import { register } from "../../lib/authApi";
import { safeNextPath, stripResumeParams, withNext } from "../../lib/nextPath";
import { LOGIN_PATH, stepPath } from "../../lib/routeGuards";
import { requireSession, setSession } from "../../lib/session";

/** 게스트 여부를 서버 조회로 정하는 이유는 로그인 라우트의 clientLoader 주석과 같다. */
export async function clientLoader() {
  const session = await requireSession();
  return { guest: session.status === "authenticated" && session.guest };
}

export function HydrateFallback() {
  return <SessionPending />;
}

export default function Register() {
  const navigate = useNavigate();
  const { guest } = useLoaderData<typeof clientLoader>();
  const [searchParams] = useSearchParams();
  const next = safeNextPath(searchParams.get("next"));
  const loginPath = next
    ? withNext(LOGIN_PATH, stripResumeParams(next))
    : LOGIN_PATH;

  async function handleRegister(email: string, password: string) {
    const response = await register(email, password);
    // 가입 화면은 protected 레이아웃 밖이라 세션 캐시가 아직 채워지지 않았다 — 모듈
    // 캐시를 직접 채운다.
    setSession({ status: "authenticated", ...response });
    void navigate(next ?? stepPath(response.onboardingStep), { replace: true });
  }

  return (
    <AuthForm
      screenId="S-03"
      title="회원가입"
      description={
        guest
          ? "가입하면 지금까지 입력한 내용이 그대로 남습니다."
          : "이메일과 비밀번호만 있으면 됩니다. 인증 메일은 보내지 않습니다."
      }
      submitLabel="계정 만들기"
      passwordAutoComplete="new-password"
      onSubmit={handleRegister}
      renderErrorHint={(error) =>
        guest && error.code === "EMAIL_TAKEN" ? (
          <>
            이미 계정이 있다면 로그인할 수 있지만, 체험 중 입력한 내용은
            이어지지 않습니다.{" "}
            <TextLink to={loginPath} direction="none" size="sm">
              로그인하기
            </TextLink>
          </>
        ) : null
      }
      footer={
        <>
          이미 계정이 있으신가요?{" "}
          <TextLink to={loginPath} direction="none" size="sm">
            로그인
          </TextLink>
        </>
      }
    />
  );
}

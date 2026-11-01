import { useLoaderData, useNavigate, useSearchParams } from "react-router";

import { TextLink } from "@/components/ui/text-link";

import { AuthForm } from "../../components/auth/AuthForm";
import { SessionPending } from "../../components/layout/SessionPending";
import { login } from "../../lib/authApi";
import { safeNextPath, withNext } from "../../lib/nextPath";
import { REGISTER_PATH, stepPath } from "../../lib/routeGuards";
import { requireSession, setSession } from "../../lib/session";

/**
 * 게스트 여부는 캐시가 아니라 서버 조회로 정한다. 이 화면은 protected 레이아웃 밖이라
 * 주소 직접 입력·새로고침이면 캐시가 비어 있다 — 그때 경고가 빠지면 로그인 한 번에
 * 체험 데이터가 조용히 사라진다. 조회 실패는 requireSession 이 비로그인으로 처리한다.
 */
export async function clientLoader() {
  const session = await requireSession();
  return { guest: session.status === "authenticated" && session.guest };
}

export function HydrateFallback() {
  return <SessionPending />;
}

export default function Login() {
  const navigate = useNavigate();
  const { guest } = useLoaderData<typeof clientLoader>();
  const [searchParams] = useSearchParams();
  const next = safeNextPath(searchParams.get("next"));

  async function handleLogin(email: string, password: string) {
    const response = await login(email, password);
    // 세션을 먼저 맞춘 뒤 이동한다. 순서를 바꾸면 목적지 가드가 아직 비로그인으로 보고 되튕긴다.
    // 로그인/가입 화면은 protected 레이아웃 밖이라 세션 캐시가 아직 채워지지 않았다 —
    // 모듈 캐시를 직접 채운다. 로그인 응답에 이미 세션 전체가 들어 있어 재조회는 필요 없다.
    setSession({ status: "authenticated", ...response });
    void navigate(next ?? stepPath(response.onboardingStep), { replace: true });
  }

  return (
    <AuthForm
      screenId="S-02"
      title="로그인"
      description="계정이 있으면 이어서 봅니다."
      submitLabel="로그인"
      passwordAutoComplete="current-password"
      onSubmit={handleLogin}
      banner={
        guest ? (
          // 경고를 본 뒤의 제출이 곧 확인이다 — 별도 다이얼로그는 두지 않는다.
          // 실패 문구와 같은 빨강이면 "로그인이 안 됐다"로 읽혀 주의 상자로 구분한다.
          <p className="rounded-md border border-carix-warning/40 bg-carix-warning/10 px-3 py-2 text-sm leading-relaxed text-carix-text">
            다른 계정으로 로그인하면 체험 중 입력한 내용은 이어지지 않습니다. 이
            내용을 남기려면{" "}
            <TextLink
              to={next ? withNext(REGISTER_PATH, next) : REGISTER_PATH}
              direction="none"
              size="sm"
            >
              가입해 주세요
            </TextLink>
            .
          </p>
        ) : null
      }
      footer={
        <>
          계정이 없으신가요?{" "}
          <TextLink
            to={next ? withNext(REGISTER_PATH, next) : REGISTER_PATH}
            direction="none"
            size="sm"
          >
            가입하기
          </TextLink>
          {/* 만료된 게스트는 쿠키가 없어 위 경고를 못 본다 — 새로 체험하는 출구는 늘 둔다.
              이름은 랜딩 버튼("가입 없이 시작하기")과 같은 말로 맞춘다. */}{" "}
          또는{" "}
          <TextLink to="/" direction="none" size="sm">
            가입 없이 체험하기
          </TextLink>
        </>
      }
      note="비밀번호 재설정은 제공하지 않습니다. 잊으셨다면 다른 이메일로 새 계정을 만들어 주세요."
    />
  );
}

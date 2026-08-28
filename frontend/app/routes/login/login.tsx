import { Link, useNavigate } from "react-router";
import { AuthForm } from "../../components/auth/AuthForm";
import { login } from "../../lib/authApi";
import { useRefreshSession } from "../../lib/SessionProvider";
import { stepPath } from "../../lib/routeGuards";

export default function Login() {
  const navigate = useNavigate();
  const refresh = useRefreshSession();

  async function handleLogin(email: string, password: string) {
    const session = await login(email, password);
    // 세션을 먼저 맞춘 뒤 이동한다. 순서를 바꾸면 목적지 가드가 아직 비로그인으로 보고 되튕긴다.
    await refresh();
    void navigate(stepPath(session.onboardingStep), { replace: true });
  }

  return (
    <AuthForm
      screenId="S-02"
      title="로그인"
      description="계정이 있으면 이어서 봅니다."
      submitLabel="로그인"
      passwordAutoComplete="current-password"
      onSubmit={handleLogin}
      footer={
        <>
          계정이 없으신가요?{" "}
          <Link
            to="/register"
            className="font-medium text-carix-info underline"
          >
            가입하기
          </Link>
        </>
      }
      note="비밀번호 재설정은 제공하지 않습니다. 잊으셨다면 다른 이메일로 새 계정을 만들어 주세요."
    />
  );
}

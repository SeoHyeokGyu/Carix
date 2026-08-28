import { Link, useNavigate } from "react-router";
import { AuthForm } from "../../components/auth/AuthForm";
import { register } from "../../lib/authApi";
import { useRefreshSession } from "../../lib/SessionProvider";
import { stepPath } from "../../lib/routeGuards";

export default function Register() {
  const navigate = useNavigate();
  const refresh = useRefreshSession();

  async function handleRegister(email: string, password: string) {
    const session = await register(email, password);
    await refresh();
    void navigate(stepPath(session.onboardingStep), { replace: true });
  }

  return (
    <AuthForm
      screenId="S-03"
      title="회원가입"
      description="이메일과 비밀번호만 있으면 됩니다. 인증 메일은 보내지 않습니다."
      submitLabel="계정 만들기"
      passwordAutoComplete="new-password"
      onSubmit={handleRegister}
      footer={
        <>
          이미 계정이 있으신가요?{" "}
          <Link to="/login" className="font-medium text-carix-info underline">
            로그인
          </Link>
        </>
      }
    />
  );
}

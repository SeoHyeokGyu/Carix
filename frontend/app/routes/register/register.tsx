import { useNavigate } from "react-router";
import { TextLink } from "@/components/ui/text-link";
import { AuthForm } from "../../components/auth/AuthForm";
import { register } from "../../lib/authApi";
import { stepPath } from "../../lib/routeGuards";
import { setSession } from "../../lib/session";

export default function Register() {
  const navigate = useNavigate();

  async function handleRegister(email: string, password: string) {
    const response = await register(email, password);
    // 가입 화면은 protected 레이아웃 밖이라 세션 캐시가 아직 채워지지 않았다 — 모듈
    // 캐시를 직접 채운다.
    setSession({ status: "authenticated", ...response });
    void navigate(stepPath(response.onboardingStep), { replace: true });
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
          <TextLink to="/login" direction="none" size="sm">
            로그인
          </TextLink>
        </>
      }
    />
  );
}

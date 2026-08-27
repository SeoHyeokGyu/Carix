import { PlaceholderScreen } from "../../components/layout/PlaceholderScreen";

export default function AuthVerify() {
  return (
    <PlaceholderScreen
      screenId="S-04"
      title="로그인 확인 중"
      description="링크의 토큰을 검증해 세션을 발급합니다. 만료되었거나 이미 쓴 링크면 재요청 화면으로 안내합니다."
    />
  );
}

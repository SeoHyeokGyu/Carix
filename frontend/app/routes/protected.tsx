import { Outlet } from "react-router";
import { SessionPending } from "../components/layout/SessionPending";
import { requireSession } from "../lib/session";

/**
 * 세션 필요 화면(온보딩·대시보드·마이페이지) 전체를 감싸는 pathless 레이아웃.
 *
 * 랜딩·로그인·가입은 이 레이아웃 밖에 있다 — 세션 조회를 기다릴 이유가 없는 화면들이라,
 * 여기 걸면 첫 페인트가 불필요하게 늦어지고 `/` 프리렌더 결과도 로딩 화면으로 바뀐다.
 */
export async function clientLoader() {
  // 반환값 자체는 아무도 읽지 않는다(SessionProvider 는 제거됐다 — 아래 컴포넌트
  // 참고). 이 loader 의 실제 역할은 부수효과다: requireSession() 이 세션
  // 캐시(session.ts 의 모듈 전역)를 채워, 병렬로 도는 하위 layout(온보딩·대시보드·
  // 마이페이지)의 가드가 콜드 캐시로 각자 fetchSession 을 다시 부르지 않게 한다.
  const session = await requireSession();
  return { session };
}

/** ssr:false 에서 이 loader 가 도는 동안 보여줄 화면. */
export function HydrateFallback() {
  return <SessionPending />;
}

// 세션은 module-level 캐시(session.ts)가 정본이다 — 각 하위 layout 이
// requireSession() 으로 직접 읽는다. React context 로 다시 감싸지 않는다(예전
// SessionProvider 는 useState 초기값을 마운트 시점에 고정해 이후 갱신을 반영하지
// 못했고, 실제 소비자도 없었다 — 최종 리뷰에서 제거).
export default function ProtectedLayout() {
  return <Outlet />;
}

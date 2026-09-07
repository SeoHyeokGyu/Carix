import { Outlet, useLoaderData } from "react-router";
import { SessionPending } from "../components/layout/SessionPending";
import { requireSession } from "../lib/session";
import { SessionProvider } from "../lib/SessionProvider";

/**
 * 세션 필요 화면(온보딩·대시보드·마이페이지) 전체를 감싸는 pathless 레이아웃.
 *
 * 랜딩·로그인·가입은 이 레이아웃 밖에 있다 — 세션 조회를 기다릴 이유가 없는 화면들이라,
 * 여기 걸면 첫 페인트가 불필요하게 늦어지고 `/` 프리렌더 결과도 로딩 화면으로 바뀐다.
 */
export async function clientLoader() {
  const session = await requireSession();
  return { session };
}

/** ssr:false 에서 이 loader 가 도는 동안 보여줄 화면. */
export function HydrateFallback() {
  return <SessionPending />;
}

export default function ProtectedLayout() {
  const { session } = useLoaderData<typeof clientLoader>();
  return (
    <SessionProvider initialSession={session}>
      <Outlet />
    </SessionProvider>
  );
}

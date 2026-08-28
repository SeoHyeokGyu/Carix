import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { fetchSession } from "./authApi";
import { ANONYMOUS, LOADING, type Session } from "./session";

interface SessionContextValue {
  session: Session;
  /** 가입·로그인·로그아웃 뒤에 부른다. 세션 상태를 서버 기준으로 다시 맞춘다. */
  refresh: () => Promise<void>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * 세션을 한 번만 조회해 트리 전체가 나눠 쓴다.
 *
 * 레이아웃마다 조회하면 화면 하나 여는 데 요청이 여러 번 나간다. 가드가 여러 레이아웃에
 * 걸려 있으므로 반드시 공유해야 한다.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>(LOADING);

  const refresh = useCallback(async () => {
    try {
      const response = await fetchSession();
      setSession(
        response ? { status: "authenticated", ...response } : ANONYMOUS,
      );
    } catch {
      // 서버에 닿지 못한 것과 비로그인을 구분하지 않는다. 어느 쪽이든 보호 화면은 못 연다.
      setSession(ANONYMOUS);
    }
  }, []);

  useEffect(() => {
    // 마운트 시 1회 세션을 읽는다. 규칙은 effect 안의 setState 를 막지만, 여기서는
    // await 뒤에 상태가 바뀌는 비동기 조회라 렌더 중 연쇄 갱신이 아니다.
    // 프레임워크식 대안은 root 의 clientLoader + useRevalidator 다. 가드의 pending 처리를
    // 다시 짜야 해서 이번 범위에서는 두지 않는다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [refresh]);

  const value = useMemo(() => ({ session, refresh }), [session, refresh]);
  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}

function useSessionContext(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error("SessionProvider 안에서만 쓸 수 있습니다");
  }
  return value;
}

export function useSession(): Session {
  return useSessionContext().session;
}

export function useRefreshSession(): () => Promise<void> {
  return useSessionContext().refresh;
}

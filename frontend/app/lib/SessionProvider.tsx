import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { fetchSession } from "./authApi";
import { ANONYMOUS, setSession as cacheSession, type Session } from "./session";

interface SessionContextValue {
  session: Session;
  /** 가입·로그인·로그아웃 뒤에 부른다. 세션 상태를 서버 기준으로 다시 맞춘다. */
  refresh: () => Promise<void>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * root clientLoader 가 이미 조회해 둔 세션을 초기값으로 받아 트리 전체가 나눠 쓴다.
 *
 * 레이아웃마다 조회하면 화면 하나 여는 데 요청이 여러 번 나간다. 가드가 여러 레이아웃에
 * 걸려 있으므로 반드시 공유해야 한다.
 */
export function SessionProvider({
  initialSession,
  children,
}: {
  initialSession: Session;
  children: ReactNode;
}) {
  const [session, setSession] = useState<Session>(initialSession);

  const refresh = useCallback(async () => {
    try {
      const response = await fetchSession();
      const next: Session = response
        ? { status: "authenticated", ...response }
        : ANONYMOUS;
      cacheSession(next);
      setSession(next);
    } catch {
      // 서버에 닿지 못한 것과 비로그인을 구분하지 않는다. 어느 쪽이든 보호 화면은 못 연다.
      cacheSession(ANONYMOUS);
      setSession(ANONYMOUS);
    }
  }, []);

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

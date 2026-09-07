import "@testing-library/jest-dom/vitest";

// jsdom 이 자체 AbortController/AbortSignal 폴리필을 깔아 두는데, Node 내장(undici 기반)
// Request 생성자는 그 신호를 자신의 내부 AbortSignal 클래스 인스턴스가 아니라며 거부한다
// ("Expected signal to be an instance of AbortSignal"). React Router 의 클라이언트
// 라우터는 loader 호출 시 실제 네트워크 요청 여부와 무관하게 내부적으로 Request 를 만들어
// 전달하므로, createRoutesStub 을 쓰는 라우팅 테스트에서 이 충돌이 그대로 터져 네비게이션이
// 멈춘다. 테스트에서는 요청 취소(abort) 동작을 검증하지 않으므로, 호환되지 않는 signal 은
// 생성자에 전달하기 전에 제거한다 — Request 는 signal 이 없으면 내부적으로 기본 신호를 만든다.
const NativeRequest = globalThis.Request;
if (NativeRequest) {
  class TestCompatibleRequest extends NativeRequest {
    constructor(input: RequestInfo | URL, init?: RequestInit) {
      if (init && "signal" in init) {
        const rest: RequestInit = { ...init };
        delete rest.signal;
        super(input, rest);
      } else {
        super(input, init);
      }
    }
  }
  globalThis.Request = TestCompatibleRequest as unknown as typeof Request;
}

if (typeof window !== "undefined" && !window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

// 이 jsdom 구성에서는 localStorage 가 제공되지 않아 ThemeToggle 렌더가 터진다.
// matchMedia 와 같은 이유·같은 방식으로 채운다.
if (typeof window !== "undefined" && !window.localStorage) {
  const store = new Map<string, string>();
  window.localStorage = {
    get length() {
      return store.size;
    },
    key: (index: number) => [...store.keys()][index] ?? null,
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, String(value)),
    removeItem: (key: string) => void store.delete(key),
    clear: () => store.clear(),
  } as Storage;
}

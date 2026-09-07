import "@testing-library/jest-dom/vitest";

// jsdom 이 자체 AbortController/AbortSignal 폴리필을 깔아 두는데, Node 내장(undici 기반)
// Request 생성자는 그 신호를 자신의 내부 AbortSignal 클래스 인스턴스가 아니라며 거부한다
// ("Expected signal to be an instance of AbortSignal"). React Router 의 클라이언트
// 라우터는 loader 호출 시 실제 네트워크 요청 여부와 무관하게 내부적으로 Request 를 만들어
// 전달하므로, createRoutesStub 을 쓰는 라우팅 테스트에서 이 충돌이 그대로 터져 네비게이션이
// 멈춘다. 테스트에서는 요청 취소(abort) 동작을 검증하지 않으므로, 호환되지 않는 signal 은
// 생성자에 전달하기 전에 제거한다 — Request 는 signal 이 없으면 내부적으로 기본 신호를 만든다.
//
// 주의 1: signal 을 무조건 제거하므로, 이후 실제 abort 동작을 검증하는 테스트를 추가하면
// 이 패치가 그 테스트를 막는다.
// 주의 2: 전역 Request 를 서브클래스로 바꿔치기하므로, 네이티브 undici Request 인스턴스가
// (이 패치 이후 생성된) `Request` 를 상대로 `instanceof` 검사를 받으면 false 가 나온다.
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

// jsdom 은 자신만의 FormData·URLSearchParams 구현을 `window` 에 심어 둔다. React Router 의
// `<Form>` 은 실제 제출 시 `new FormData(formElement)` 로 이 jsdom 클래스의 인스턴스를 만들고
// (그래야 jsdom 이 만든 `<form>` DOM 을 읽을 수 있다), 기본 encType
// (`application/x-www-form-urlencoded`)에서는 그 값을 다시 `new URLSearchParams(...)` 로
// 감싼다 — 이것도 jsdom 클래스다.
//
// Node 내장(undici 기반) Request 생성자는 body 가 FormData·URLSearchParams 인지 "자신의"
// 클래스 레퍼런스로만 판별한다. jsdom 인스턴스는 그 판별을 통과하지 못해 기본값인
// `text/plain` 으로 떨어지고, 그 결과 `request.formData()` 가 "Content-Type was not one of
// multipart/form-data or application/x-www-form-urlencoded" 로 터진다 — action 이 있는
// 화면에서 폼을 제출하는 모든 라우트 테스트가 이 문제를 겪는다.
//
// 클래스 판별 자체를 우회한다: body 가 (FormData 든 URLSearchParams 든) `.entries()` 를
// 가진 값이면 그 자리에서 문자열로 직렬화하고 Content-Type 을 직접 지정한다 — 이 프로젝트의
// 폼은 파일 입력이 없어 항상 `application/x-www-form-urlencoded` 이므로 안전하다. 파일
// 입력이 생겨 실제 멀티파트가 필요해지면 이 우회를 다시 봐야 한다.
//
// 위 AbortSignal 패치가 이미 `globalThis.Request` 를 `TestCompatibleRequest` 로 바꿔
// 두었다. 아래는 그 위에 다시 한 겹 씌우는 것이지 대체가 아니다 — `RequestAfterAbortSignalPatch`
// 가 가리키는 건 원본 undici Request 가 아니라 signal 을 제거해 주는 그 클래스이므로,
// signal 제거와 form-body 직렬화가 순서대로 함께 적용된다.
const RequestAfterAbortSignalPatch = globalThis.Request;

function hasEntries(value: unknown): value is Iterable<[string, unknown]> {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as { entries?: unknown }).entries === "function"
  );
}

class RequestWithUrlEncodedFormFix extends RequestAfterAbortSignalPatch {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    if (init && hasEntries(init.body)) {
      const params = new URLSearchParams();
      for (const [key, value] of init.body) {
        params.append(key, typeof value === "string" ? value : String(value));
      }
      const headers = new Headers(init.headers);
      headers.set("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
      super(input, { ...init, body: params.toString(), headers });
    } else {
      super(input, init);
    }
  }
}

globalThis.Request = RequestWithUrlEncodedFormFix as unknown as typeof Request;

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

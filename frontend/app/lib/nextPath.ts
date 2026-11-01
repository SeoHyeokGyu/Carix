// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f]/;
const PROBE_ORIGIN = "http://x.invalid";

/**
 * 가입 뒤 돌아갈 경로(`next` 쿼리)를 검증한다. 앱 내부 경로만 허용한다 —
 * `//host`·`/\host` 는 브라우저가 외부 주소로 해석하므로 오픈 리다이렉트가 된다.
 */
export function safeNextPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/")) return null;
  // WHATWG URL 파서는 탭·개행을 지우므로 `/\t/evil.com` 이 `//evil.com` 이 된다.
  if (CONTROL_CHARS.test(raw)) return null;
  if (raw.startsWith("//") || raw.startsWith("/\\")) return null;
  // 접두 검사가 놓친 변형은 실제 파서로 최종 확인한다.
  if (new URL(raw, PROBE_ORIGIN).origin !== PROBE_ORIGIN) return null;
  return raw;
}

export function withNext(path: string, next: string): string {
  return `${path}?next=${encodeURIComponent(next)}`;
}

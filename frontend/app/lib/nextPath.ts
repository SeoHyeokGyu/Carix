/**
 * 가입 뒤 돌아갈 경로(`next` 쿼리)를 검증한다. 앱 내부 경로만 허용한다 —
 * `//host`·`/\host` 는 브라우저가 외부 주소로 해석하므로 오픈 리다이렉트가 된다.
 */
export function safeNextPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/")) return null;
  if (raw.startsWith("//") || raw.startsWith("/\\")) return null;
  return raw;
}

export function withNext(path: string, next: string): string {
  return `${path}?next=${encodeURIComponent(next)}`;
}

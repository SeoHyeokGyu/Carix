/** 세션 조회 중에 보여줄 자리. 여기서 튕기면 로그인한 사용자도 랜딩을 한 번 본다. */
export function SessionPending() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-screen items-center justify-center bg-carix-bg text-carix-text/60"
    >
      불러오는 중…
    </div>
  );
}

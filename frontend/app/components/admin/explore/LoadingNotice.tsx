/** 수집 데이터 탭이 응답을 기다리는 동안 결과 자리에 두는 안내. */
export function LoadingNotice() {
  return (
    <p role="status" className="text-xs text-carix-text/60">
      불러오는 중입니다...
    </p>
  );
}

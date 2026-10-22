import { useEffect, useRef } from "react";

/**
 * 미반영 `넣기` 로 들어왔을 때 접힌 영역을 열고 `selector` 칸에 포커스한다. 열림을 속성으로 묶지 않아
 * 호출자가 포커스 상태를 비워도 사용자가 연 영역이 닫히지 않는다.
 */
export function useFillFocus(selector: string | undefined) {
  const detailsRef = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    const details = detailsRef.current;
    if (!selector || !details) return;
    details.open = true;
    details.querySelector<HTMLElement>(selector)?.focus();
  }, [selector]);
  return detailsRef;
}

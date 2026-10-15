import { useCallback, useState } from "react";

/**
 * 한 줄 알림(`InlineAlert`) 문장과 그 키. 같은 실패를 다시 내면 문장은 같아도 키가 바뀐다 —
 * `<InlineAlert key={key} />` 로 그리면 노드가 새로 생겨 스크린리더가 다시 읽는다.
 * 같은 노드에 같은 문장이 남으면 `role="alert"` 는 다시 읽히지 않는다.
 */
export function useAlertMessage() {
  const [state, setState] = useState<{ message: string | null; key: number }>({
    message: null,
    key: 0,
  });

  const show = useCallback((message: string | null) => {
    setState((prev) => ({ message, key: prev.key + 1 }));
  }, []);

  const clear = useCallback(() => show(null), [show]);

  return { message: state.message, key: state.key, show, clear };
}

import { useEffect, useState } from "react";

/**
 * 요소의 실제 폭(px). SVG 차트가 viewBox 를 이 폭에 맞추면 화면이 넓어져도 글자·높이가 부풀지 않는다.
 *
 * <p>재기 전(첫 렌더·jsdom)과 숨겨져 폭이 0 일 때는 `fallback` 을 돌려준다.
 */
export function useElementWidth<T extends HTMLElement>(fallback: number) {
  const [node, setNode] = useState<T | null>(null);
  const [width, setWidth] = useState(fallback);

  useEffect(() => {
    if (!node || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(([entry]) => {
      const measured = Math.round(entry.contentRect.width);
      if (measured > 0) setWidth(measured);
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [node]);

  return [setNode, width] as const;
}

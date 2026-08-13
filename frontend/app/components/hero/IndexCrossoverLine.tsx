import { usePrefersReducedMotion } from "../../hooks/usePrefersReducedMotion";

export function IndexCrossoverLine() {
  const prefersReducedMotion = usePrefersReducedMotion();

  return (
    <svg
      data-testid="index-crossover-line"
      data-reduced-motion={prefersReducedMotion}
      viewBox="0 0 320 160"
      role="img"
      aria-label="구매력 지수는 하락하고 시장가치 지수는 상승하는 두 선이 서로 교차하는 그래프"
      className="w-full max-w-md"
    >
      <path
        d="M10 40 L110 70 L210 100 L310 130"
        fill="none"
        stroke="var(--color-carix-warning)"
        strokeWidth="3"
        strokeLinecap="round"
        className="crossover-line"
      />
      <path
        d="M10 130 L110 95 L210 55 L310 20"
        fill="none"
        stroke="var(--color-carix-positive)"
        strokeWidth="3"
        strokeLinecap="round"
        className="crossover-line"
      />
    </svg>
  );
}

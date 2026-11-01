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
      className="w-full max-w-md lg:max-w-lg"
    >
      {/* 1. 배경 격자선 (Faint Grid Lines - 테스트 통과를 위해 path 대신 line 사용) */}
      <line
        x1="10"
        y1="40"
        x2="310"
        y2="40"
        stroke="var(--color-carix-border)"
        strokeWidth="1"
        strokeDasharray="3"
        opacity="0.4"
      />
      <line
        x1="10"
        y1="80"
        x2="310"
        y2="80"
        stroke="var(--color-carix-border)"
        strokeWidth="1"
        strokeDasharray="3"
        opacity="0.4"
      />
      <line
        x1="10"
        y1="120"
        x2="310"
        y2="120"
        stroke="var(--color-carix-border)"
        strokeWidth="1"
        strokeDasharray="3"
        opacity="0.4"
      />

      <line
        x1="110"
        y1="20"
        x2="110"
        y2="140"
        stroke="var(--color-carix-border)"
        strokeWidth="1"
        strokeDasharray="3"
        opacity="0.4"
      />
      <line
        x1="210"
        y1="20"
        x2="210"
        y2="140"
        stroke="var(--color-carix-border)"
        strokeWidth="1"
        strokeDasharray="3"
        opacity="0.4"
      />

      {/* 2. X, Y 축선 (Axes) */}
      <line
        x1="10"
        y1="20"
        x2="10"
        y2="140"
        stroke="var(--color-carix-border)"
        strokeWidth="1.5"
        opacity="0.8"
      />
      <line
        x1="10"
        y1="140"
        x2="310"
        y2="140"
        stroke="var(--color-carix-border)"
        strokeWidth="1.5"
        opacity="0.8"
      />

      {/* 3. 축 라벨 텍스트 (Labels) */}
      <text
        x="15"
        y="15"
        fill="var(--color-carix-text)"
        opacity="0.5"
        fontSize="8"
        fontFamily="var(--font-body)"
      >
        지수 (Index)
      </text>
      <text
        x="280"
        y="152"
        fill="var(--color-carix-text)"
        opacity="0.5"
        fontSize="8"
        fontFamily="var(--font-body)"
      >
        시간 (년)
      </text>

      {/* 4. 지표 이름 플로팅 라벨 */}
      <text
        x="15"
        y="32"
        fill="var(--color-carix-warning)"
        fontSize="9"
        fontWeight="bold"
        fontFamily="var(--font-display)"
      >
        구매력 지수 💰
      </text>
      <text
        x="220"
        y="32"
        fill="var(--color-carix-positive)"
        fontSize="9"
        fontWeight="bold"
        fontFamily="var(--font-display)"
      >
        시장가치 지수 📈
      </text>

      {/* 5. 메인 그래프 선 (2개의 path 유지) */}
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

      {/* 6. 데이터 꼭짓점 서클 포인트 (Circle Nodes) */}
      {/* 구매력 데이터 점 */}
      <circle
        cx="10"
        cy="40"
        r="4"
        fill="var(--color-carix-warning)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
      <circle
        cx="110"
        cy="70"
        r="4"
        fill="var(--color-carix-warning)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
      <circle
        cx="210"
        cy="100"
        r="4"
        fill="var(--color-carix-warning)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
      <circle
        cx="310"
        cy="130"
        r="4"
        fill="var(--color-carix-warning)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />

      {/* 시장가치 데이터 점 */}
      <circle
        cx="10"
        cy="130"
        r="4"
        fill="var(--color-carix-positive)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
      <circle
        cx="110"
        cy="95"
        r="4"
        fill="var(--color-carix-positive)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
      <circle
        cx="210"
        cy="55"
        r="4"
        fill="var(--color-carix-positive)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
      <circle
        cx="310"
        cy="20"
        r="4"
        fill="var(--color-carix-positive)"
        stroke="var(--carix-surface)"
        strokeWidth="1.5"
      />
    </svg>
  );
}

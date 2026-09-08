import { EXPENSE_CATEGORIES } from "../../lib/schemas/expense";

export interface ExpenseAmount {
  category: string;
  amount: number;
  /** 서버가 통계청 평균으로 보간한 값이면 true. 프로젝트 공통 규칙 — 추정·대체
   * 값은 실제 값과 항상 시각적으로 구분한다(색으로만 구분하지 않는다). */
  estimated?: boolean;
}

export interface ExpenseShare {
  category: string;
  share: number;
  estimated?: boolean;
}

/** 카테고리 순서에 고정된 팔레트. 등장 순서(필터 이후)가 아니라 카테고리 값
 * 자체에 색을 고정해, 어떤 카테고리가 비어 있든 나머지 색이 항상 그대로다.
 * 항목은 반드시 알파·중첩 함수가 없는 순수 `oklch(L C H)` 형태여야 한다 —
 * `colorFor`가 추정치 표시를 위해 마지막 ")" 앞에 `/ alpha`를 문자열로 끼워
 * 넣는다. `color-mix(...)`나 이미 알파가 있는 색을 넣으면 이 치환이 깨진
 * CSS 를 만들고, jsdom 은 gradient 문자열을 파싱하지 않으므로 테스트로도
 * 잡히지 않는다. */
const PALETTE = [
  "oklch(62% 0.17 255)", // HOUSING
  "oklch(72% 0.17 145)", // FOOD
  "oklch(75% 0.15 80)", // TRANSPORT
  "oklch(62% 0.20 25)", // MEDICAL
  "oklch(62% 0.15 300)", // EDUCATION
  "oklch(70% 0.02 90)", // LEISURE
] as const;

/** 추정 조각은 같은 카테고리 색을 옅게 써서 "덜 확실한 값"이라는 인상을 더한다
 * — 다만 이건 보조 신호일 뿐이다. 진짜 구분 수단은 범례의 점선 스와치와
 * "(추정)" 텍스트다(색만으로 전달하지 않는다). */
const ESTIMATED_ALPHA = 0.5;

const CATEGORY_LABELS: Record<string, string> = Object.fromEntries(
  EXPENSE_CATEGORIES.map((category) => [category.value, category.label]),
);

const CATEGORY_COLORS: Record<string, string> = Object.fromEntries(
  EXPENSE_CATEGORIES.map((category, index) => [category.value, PALETTE[index % PALETTE.length]]),
);

function colorFor(category: string, estimated: boolean | undefined): string {
  const color = CATEGORY_COLORS[category] ?? "oklch(80% 0 0)";
  return estimated ? color.replace(")", ` / ${ESTIMATED_ALPHA})`) : color;
}

/**
 * 금액 → 비중(%). 차트 라이브러리를 쓰지 않으므로(conic-gradient 로 직접 그린다)
 * 이 계산이 순수 함수여야 테스트로 검증할 수 있다. 합계가 0 이면 0 으로 나누지
 * 않도록 빈 배열을 돌려준다 — 이 경우 호출부가 "계산할 수 없음" 상태를 그린다.
 * 금액이 0 인 카테고리는 조각이 없으므로 결과에서 뺀다. `estimated` 플래그는
 * 그대로 결과에 옮긴다(계산에는 관여하지 않는다).
 */
export function toShares(items: readonly ExpenseAmount[]): ExpenseShare[] {
  const total = items.reduce((sum, item) => sum + item.amount, 0);
  if (total <= 0) return [];

  return items
    .filter((item) => item.amount > 0)
    .map((item) => ({
      category: item.category,
      share: Math.round((item.amount / total) * 100),
      estimated: item.estimated,
    }));
}

function toConicGradient(shares: readonly ExpenseShare[]): string {
  let cumulative = 0;
  const stops = shares.map((share) => {
    const from = cumulative;
    cumulative += share.share;
    return `${colorFor(share.category, share.estimated)} ${from}% ${cumulative}%`;
  });
  return `conic-gradient(${stops.join(", ")})`;
}

/**
 * 소비 비중 도넛. `conic-gradient` 로 직접 그리고(차트 라이브러리 미사용), 같은
 * 값을 목록으로도 함께 낸다 — 색만으로 정보를 전달하지 않는다(비중을 텍스트로도
 * 읽을 수 있어야 한다). `estimated` 항목(서버가 통계청 평균으로 보간한 값)은
 * 범례에서 점선 스와치 + "(추정)" 문구로 실제 입력값과 구분한다 — 색 하나로만
 * 구분하지 않는다(프로젝트 공통 규칙).
 */
export function ExpenseDonut({ items }: { items: readonly ExpenseAmount[] }) {
  const shares = toShares(items);

  if (shares.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        입력한 금액이 없어 비중을 계산할 수 없습니다.
      </p>
    );
  }

  return (
    <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
      <div
        role="img"
        aria-label="카테고리별 소비 비중 도넛 차트. 옅은 조각은 통계청 평균으로 보간된 추정치입니다."
        className="h-28 w-28 shrink-0 rounded-full"
        style={{ background: toConicGradient(shares) }}
      />
      <ul className="flex flex-col gap-1 text-sm">
        {shares.map((share) => (
          <li key={share.category} className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className={
                share.estimated
                  ? "h-2.5 w-2.5 shrink-0 rounded-full border-2 border-dashed bg-transparent"
                  : "h-2.5 w-2.5 shrink-0 rounded-full"
              }
              style={{
                borderColor: share.estimated ? CATEGORY_COLORS[share.category] : undefined,
                background: share.estimated ? undefined : CATEGORY_COLORS[share.category],
              }}
            />
            {CATEGORY_LABELS[share.category]} {share.share}%
            {share.estimated ? " (추정)" : ""}
          </li>
        ))}
      </ul>
    </div>
  );
}

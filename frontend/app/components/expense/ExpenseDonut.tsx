import { EXPENSE_CATEGORIES } from "../../lib/schemas/expense";

export interface ExpenseAmount {
  category: string;
  amount: number;
}

export interface ExpenseShare {
  category: string;
  share: number;
}

/** 카테고리 순서에 고정된 팔레트. 등장 순서(필터 이후)가 아니라 카테고리 값
 * 자체에 색을 고정해, 어떤 카테고리가 비어 있든 나머지 색이 항상 그대로다. */
const PALETTE = [
  "oklch(62% 0.17 255)", // HOUSING
  "oklch(72% 0.17 145)", // FOOD
  "oklch(75% 0.15 80)", // TRANSPORT
  "oklch(62% 0.20 25)", // MEDICAL
  "oklch(62% 0.15 300)", // EDUCATION
  "oklch(70% 0.02 90)", // LEISURE
] as const;

const CATEGORY_LABELS: Record<string, string> = Object.fromEntries(
  EXPENSE_CATEGORIES.map((category) => [category.value, category.label]),
);

const CATEGORY_COLORS: Record<string, string> = Object.fromEntries(
  EXPENSE_CATEGORIES.map((category, index) => [category.value, PALETTE[index % PALETTE.length]]),
);

/**
 * 금액 → 비중(%). 차트 라이브러리를 쓰지 않으므로(conic-gradient 로 직접 그린다)
 * 이 계산이 순수 함수여야 테스트로 검증할 수 있다. 합계가 0 이면 0 으로 나누지
 * 않도록 빈 배열을 돌려준다 — 이 경우 호출부가 "계산할 수 없음" 상태를 그린다.
 * 금액이 0 인 카테고리는 조각이 없으므로 결과에서 뺀다.
 */
export function toShares(items: readonly ExpenseAmount[]): ExpenseShare[] {
  const total = items.reduce((sum, item) => sum + item.amount, 0);
  if (total <= 0) return [];

  return items
    .filter((item) => item.amount > 0)
    .map((item) => ({
      category: item.category,
      share: Math.round((item.amount / total) * 100),
    }));
}

function toConicGradient(shares: readonly ExpenseShare[]): string {
  let cumulative = 0;
  const stops = shares.map((share) => {
    const from = cumulative;
    cumulative += share.share;
    const color = CATEGORY_COLORS[share.category] ?? "oklch(80% 0 0)";
    return `${color} ${from}% ${cumulative}%`;
  });
  return `conic-gradient(${stops.join(", ")})`;
}

/**
 * 소비 비중 도넛. `conic-gradient` 로 직접 그리고(차트 라이브러리 미사용), 같은
 * 값을 목록으로도 함께 낸다 — 색만으로 정보를 전달하지 않는다(비중을 텍스트로도
 * 읽을 수 있어야 한다).
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
        aria-label="카테고리별 소비 비중 도넛 차트"
        className="h-28 w-28 shrink-0 rounded-full"
        style={{ background: toConicGradient(shares) }}
      />
      <ul className="flex flex-col gap-1 text-sm">
        {shares.map((share) => (
          <li key={share.category} className="flex items-center gap-2">
            <span
              aria-hidden="true"
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ background: CATEGORY_COLORS[share.category] }}
            />
            {CATEGORY_LABELS[share.category]} {share.share}%
          </li>
        ))}
      </ul>
    </div>
  );
}

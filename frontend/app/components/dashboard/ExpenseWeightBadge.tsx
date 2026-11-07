import { type Tone, ToneBadge } from "@/components/ui/tone-badge";

const BADGES: Record<string, { label: string; tone: Tone }> = {
  USER_DEFINED: { label: "내 지출 기준", tone: "positive" },
  PARTIALLY_ESTIMATED: { label: "일부 추정 포함", tone: "info" },
  PROPAGATED: { label: "입력한 지출 기준", tone: "info" },
  DEFAULT_CPI: { label: "통계청 평균 기준", tone: "muted" },
};

/**
 * 구매력 카드의 소비 가중치 출처 뱃지 (기능 명세 US-D1, 도메인 정의서 §1.2). 그 달 구매력 지수를
 * 만든 가중치 상태라 숫자와 같은 것을 말한다. 상태가 없거나(도입 전 스냅샷) 모르는 코드면 그리지 않는다.
 */
export function ExpenseWeightBadge({
  status,
}: {
  status: string | null | undefined;
}) {
  const badge = status ? BADGES[status] : undefined;
  if (!badge) return null;
  return (
    <ToneBadge tone={badge.tone} className="self-start">
      {badge.label}
    </ToneBadge>
  );
}

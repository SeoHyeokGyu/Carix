import { ToneBadge } from "@/components/ui/tone-badge";

import type { TopPriceDriver } from "../../lib/api/dashboard";

/**
 * 구매력 카드 하단의 물가 기여도 1위 뱃지 (기능 명세 US-D1). 값이 없으면 줄 자체가 없다.
 * 가중치를 평균 소비로 채웠으면 무엇을 추정했는지 밝힌다 — 「· 추정」 만으로는 1위가 추정인지 금액이 추정인지 읽히지 않았다.
 */
export function PriceDriverBadge({
  driver,
}: {
  driver: TopPriceDriver | null | undefined;
}) {
  if (!driver) return null;
  return (
    <ToneBadge tone="warning" className="self-start">
      {driver.estimated ? `${driver.label} (평균 소비로 추정)` : driver.label}
    </ToneBadge>
  );
}

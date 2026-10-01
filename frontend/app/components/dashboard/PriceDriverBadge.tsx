import { ToneBadge } from "@/components/ui/tone-badge";
import type { TopPriceDriver } from "../../lib/api/dashboard";

/** 구매력 카드 하단의 물가 기여도 1위 뱃지 (기능 명세 US-D1). 값이 없으면 줄 자체가 없다. */
export function PriceDriverBadge({
  driver,
}: {
  driver: TopPriceDriver | null | undefined;
}) {
  if (!driver) return null;
  return (
    <ToneBadge tone="warning" className="self-start">
      {driver.estimated ? `${driver.label} · 추정` : driver.label}
    </ToneBadge>
  );
}

import { resolveDecisionSignal } from "../../lib/decisionSignal";
import { ToneBadge } from "@/components/ui/tone-badge";

interface SignalBadgeProps {
  code: string;
  /** 프런트 테이블에 없는 코드일 때만 쓰는 서버 문구. */
  serverLabel?: string | null;
  className?: string;
}

/** 결정 신호 뱃지. 두 화면(`S-10`·`S-11`)이 같은 것을 쓴다. */
export function SignalBadge({
  code,
  serverLabel,
  className,
}: SignalBadgeProps) {
  const { label, tone } = resolveDecisionSignal(code, serverLabel);

  return (
    <ToneBadge tone={tone} data-testid="signal-badge" className={className}>
      {label}
    </ToneBadge>
  );
}

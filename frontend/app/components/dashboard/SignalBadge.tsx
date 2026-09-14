import {
  resolveDecisionSignal,
  SIGNAL_TONE_CLASS,
} from "../../lib/decisionSignal";

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
    <span
      data-testid="signal-badge"
      data-tone={tone}
      className={[
        "inline-flex items-center rounded px-2 py-0.5 text-xs font-semibold",
        SIGNAL_TONE_CLASS[tone],
        className ?? "",
      ].join(" ")}
    >
      {label}
    </span>
  );
}

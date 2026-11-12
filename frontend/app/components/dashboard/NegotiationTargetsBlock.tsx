import type {
  NegotiationTarget,
  NegotiationTargets,
} from "../../lib/api/dashboard";
import { wageBasisLabel, wageSourceLabel } from "../../lib/sourceLabel";

/** 협상 목표가 붙는 신호. 그 외 신호는 블록이 없다 (US-D7). */
const NEGOTIATION_SIGNALS = new Set(["NEGOTIATE", "SEEK_JOB"]);

interface NegotiationTargetsBlockProps {
  signalCode: string;
  targets: NegotiationTargets | null | undefined;
  wageBasis?: string | null;
}

interface Row {
  label: string;
  note?: string;
  target: NegotiationTarget;
}

/**
 * `S-10` 판정 히어로의 목표 인상률 셋 (기능 명세 US-D7·§4.8). 비율만 보인다 — 원 단위 목표 금액은 응답에도 없다.
 * 이직 탐색은 「희망 연봉」으로 라벨만 바꾸고 값은 같다.
 */
export function NegotiationTargetsBlock({
  signalCode,
  targets,
  wageBasis,
}: NegotiationTargetsBlockProps) {
  if (!targets || !NEGOTIATION_SIGNALS.has(signalCode)) return null;

  const isSeekJob = signalCode === "SEEK_JOB";
  const rows: Row[] = [];
  if (targets.toWageStatMedian) {
    rows.push({
      label: isSeekJob ? "임금통계 중앙값 수준" : "임금통계 중앙값까지",
      // 이 줄은 KOSIS 기준선일 때만 온다(§4.8)
      note:
        wageBasis === "AGE_BAND_PROXY"
          ? `${wageBasisLabel(wageBasis)} · ${wageSourceLabel("KOSIS")}`
          : wageSourceLabel("KOSIS"),
      target: targets.toWageStatMedian,
    });
  }
  if (targets.toPeerP75) {
    rows.push({
      label: isSeekJob ? "동료집단 상위 25% 수준" : "동료집단 상위 25%까지",
      target: targets.toPeerP75,
    });
  }
  const recovery = targets.toPurchasingPowerRecovery;
  if (recovery) {
    const since = formatYearMonth(recovery.sinceYearMonth);
    rows.push({
      label: isSeekJob
        ? `${since} 이후 물가 상승분 반영`
        : `${since} 이후 물가 상승분`,
      target: recovery,
    });
  }
  // BELOW 인데 값이 없으면 계약 위반이다 — 「+0.0%」를 지어내지 않고 그 줄을 뺀다
  const visible = rows.filter(
    (row) =>
      row.target.status !== "BELOW" || row.target.raiseRatePercent !== null,
  );
  if (visible.length === 0) return null;

  // 판정 카드 왼쪽 열이 남는 폭을 다 쓰므로 폭을 묶는다 — 항목과 값이 양끝 정렬이라 넓으면 서로 멀어진다.
  return (
    <section
      aria-label="협상 목표"
      className="mt-1 flex max-w-xl flex-col gap-2 rounded-lg border border-carix-border bg-carix-surface/60 p-3.5"
    >
      <h3 className="text-xs font-semibold text-carix-text/80">
        {isSeekJob ? "희망 연봉 목표" : "협상 목표 인상률"}
      </h3>
      <dl className="flex flex-col gap-1.5">
        {visible.map((row) => (
          <div
            key={row.label}
            className="flex items-baseline justify-between gap-3"
          >
            <dt className="flex flex-col text-sm text-carix-text">
              <span>{row.label}</span>
              {row.note && (
                <span className="text-xs text-carix-text/70">{row.note}</span>
              )}
            </dt>
            <dd
              className={
                row.target.status === "BELOW"
                  ? "font-display text-base font-bold tabular-nums text-carix-text"
                  : "text-sm text-carix-text/70"
              }
            >
              {formatTarget(row.target)}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function formatTarget(target: NegotiationTarget): string {
  if (target.status === "ABOVE") return "이미 상회";
  if (target.status === "NONE") return "물가 상승분 없음";
  return `+${target.raiseRatePercent?.toFixed(1)}%`;
}

/** `202503` → `2025년 3월` */
function formatYearMonth(yearMonth: string): string {
  return `${yearMonth.slice(0, 4)}년 ${Number(yearMonth.slice(4, 6))}월`;
}

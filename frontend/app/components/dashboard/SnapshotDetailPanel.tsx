import { Link } from "react-router";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { HEADING } from "@/components/ui/heading";

import type {
  SnapshotPoint,
  SnapshotRationale,
} from "../../lib/dashboardMarketApi";
import { isUserActionable } from "../../lib/insufficientDataCause";
import { SignalBadge } from "./SignalBadge";

const SEOUL_TIME = new Intl.DateTimeFormat("sv-SE", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

function formatRecordedAt(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "-" : SEOUL_TIME.format(date);
}

function formatYearMonth(ym: string): string {
  const clean = ym.replace("-", "");
  return clean.length === 6 ? `${clean.slice(0, 4)}-${clean.slice(4, 6)}` : ym;
}

/** 현재 규칙 값 − 당시 값. 둘 중 하나라도 없으면 차이를 만들지 않는다. */
function formatDiff(
  current: number | null | undefined,
  recorded: number | null | undefined,
): string | null {
  if (current == null || recorded == null) return null;
  const diff = current - recorded;
  return `${diff > 0 ? "+" : ""}${diff.toFixed(1)}`;
}

function formatIndex(value: number | null | undefined): string {
  return value != null ? value.toFixed(1) : "데이터 없음";
}

interface IndexRowProps {
  name: string;
  value: number | null | undefined;
  diff?: string | null;
}

function IndexRow({ name, value, diff }: IndexRowProps) {
  return (
    <div className="flex items-baseline justify-between gap-2">
      <span className="text-carix-text/60">{name}</span>
      <span className="font-medium text-carix-text">
        {formatIndex(value)}
        {diff && <span className="ml-2 text-carix-text/60">{diff}</span>}
      </span>
    </div>
  );
}

interface RationaleView {
  dataVersion: string;
  ruleVersion: string;
  rationale: SnapshotRationale | null;
}

/** 한 칸의 근거: 적용 규칙, 제외 스킬, 기준일과 버전. */
function RationaleBlock({ view }: { view: RationaleView }) {
  const rules = view.rationale?.rulesApplied ?? [];
  const excluded = view.rationale?.excluded ?? [];
  return (
    <div className="space-y-3 border-t border-carix-border/50 pt-3">
      <div>
        <span className="font-semibold text-carix-text">
          적용된 규칙 (Rules Applied)
        </span>
        <div className="mt-2 space-y-1">
          {rules.length > 0 ? (
            rules.map((rule, idx) => (
              <div
                key={idx}
                className="rounded bg-carix-bg p-1.5 font-mono text-[11px] text-carix-text/80"
              >
                • {rule}
              </div>
            ))
          ) : (
            <p className="text-carix-text/50">기록된 적용 규칙 없음</p>
          )}
        </div>
      </div>
      <div>
        <span className="font-semibold text-carix-text">
          제외된 스킬 (Excluded Skills)
        </span>
        <div className="mt-2 space-y-1">
          {excluded.length > 0 ? (
            excluded.map((skill, idx) => (
              <div
                key={idx}
                className="rounded bg-carix-bg p-1.5 font-mono text-[11px] text-carix-warning"
              >
                ✕ {skill}
              </div>
            ))
          ) : (
            <p className="text-carix-text/50">제외된 스킬 없음 (전체 반영됨)</p>
          )}
        </div>
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-carix-text/50">
        <span>데이터 버전: {view.dataVersion || "-"}</span>
        <span>규칙 버전: {view.ruleVersion || "-"}</span>
        <span>기준일: {view.rationale?.baseDate || "-"}</span>
      </div>
    </div>
  );
}

interface SnapshotDetailPanelProps {
  point: SnapshotPoint;
  onClose: () => void;
}

export function SnapshotDetailPanel({
  point,
  onClose,
}: SnapshotDetailPanelProps) {
  const { recorded } = point;
  // 헤더는 사용자가 그 달에 본 판정을 따른다. 소급 계산한 달만 현재 규칙 값이다.
  const shown = recorded ?? point;

  return (
    <div
      data-testid="snapshot-detail-panel"
      className="border-t border-carix-border bg-carix-bg/30 p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-carix-border/80 pb-3">
        <div className="flex items-center gap-2">
          <h2 className={HEADING.section}>
            {formatYearMonth(point.baseYearMonth)} 스냅샷 산출 근거
          </h2>
          <SignalBadge code={shown.decisionSignal} serverLabel={shown.label} />
        </div>
        <Button type="button" variant="ghost" size="xs" onClick={onClose}>
          닫기 ✕
        </Button>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 text-xs sm:grid-cols-2">
        <Card data-testid="snapshot-column-recorded" className="gap-0 p-4.5">
          <span className="font-semibold text-carix-text">당시</span>
          {recorded ? (
            <div className="mt-2 space-y-1.5">
              <SignalBadge
                code={recorded.decisionSignal}
                serverLabel={recorded.label}
              />
              <IndexRow
                name="구매력 지수"
                value={recorded.purchasingPowerIndex}
              />
              <IndexRow
                name="시장가치 지수"
                value={recorded.marketValueIndex}
              />
              <IndexRow
                name="채용 활발도"
                value={recorded.openingWindowIndex}
              />
              <p className="pt-1 text-[11px] text-carix-text/60">
                기록 시각 {formatRecordedAt(recorded.recordedAt)} (서울) · 월
                경계는 UTC 기준입니다
              </p>
              <RationaleBlock view={recorded} />
            </div>
          ) : (
            <p className="mt-2 text-carix-text/50">
              당시 판정 없음 — 이 달이 끝난 뒤 계산됨
            </p>
          )}
        </Card>

        <Card data-testid="snapshot-column-current" className="gap-0 p-4.5">
          <span className="font-semibold text-carix-text">현재 규칙</span>
          <div className="mt-2 space-y-1.5">
            <SignalBadge
              code={point.decisionSignal}
              serverLabel={point.label}
            />
            <IndexRow
              name="구매력 지수"
              value={point.purchasingPowerIndex}
              diff={formatDiff(
                point.purchasingPowerIndex,
                recorded?.purchasingPowerIndex,
              )}
            />
            <IndexRow
              name="시장가치 지수"
              value={point.marketValueIndex}
              diff={formatDiff(
                point.marketValueIndex,
                recorded?.marketValueIndex,
              )}
            />
            <IndexRow
              name="채용 활발도"
              value={point.openingWindowIndex}
              diff={formatDiff(
                point.openingWindowIndex,
                recorded?.openingWindowIndex,
              )}
            />
            <RationaleBlock view={point} />
          </div>
        </Card>
      </div>

      {/* 데이터 부족 시 입력 화면 유도 (US-D4). 입력은 현재 재계산에만 반영되므로 당시가 아니라
          현재 판정을 따른다. 수집 공백이 원인이면 유도하지 않는다 — 무엇을 입력해도 그 달은 달라지지 않는다. */}
      {point.decisionSignal === "INSUFFICIENT_DATA" &&
        !isUserActionable(point.rationale?.rulesApplied) && (
          <div className="mt-4 rounded-lg border border-carix-warning/30 bg-carix-warning/5 p-4 text-xs">
            <div className="font-bold text-carix-warning">
              ⚠️ 이 달은 시장 집계가 수집되지 않아 지수를 낼 수 없습니다.
            </div>
            <p className="mt-1 text-carix-text/70">
              보유 스킬이나 소득 정보와 무관한 구간입니다. 해당 월 집계가
              채워지면 다시 계산됩니다.
            </p>
          </div>
        )}

      {point.decisionSignal === "INSUFFICIENT_DATA" &&
        isUserActionable(point.rationale?.rulesApplied) && (
          <div className="mt-4 rounded-lg border border-carix-danger/30 bg-carix-danger/5 p-4 text-xs">
            <div className="font-bold text-carix-danger">
              ⚠️ 입력 데이터가 부족하여 지수를 정상 판정하지 못했습니다.
            </div>
            <p className="mt-1 text-carix-text/70">
              소득 내역 또는 보유 스킬 정보를 보강하시면 다음 스냅샷에서
              정상적인 지표를 확인하실 수 있습니다.
            </p>
            <div className="mt-3 flex gap-2">
              <Link
                to="/onboarding/income"
                className="rounded border border-carix-danger/40 bg-carix-surface px-3 py-1.5 font-medium text-carix-danger hover:bg-carix-danger/10"
              >
                소득 정보 입력하기
              </Link>
              <Link
                to="/onboarding/skills"
                className="rounded border border-carix-danger/40 bg-carix-surface px-3 py-1.5 font-medium text-carix-danger hover:bg-carix-danger/10"
              >
                보유 스킬 입력하기
              </Link>
            </div>
          </div>
        )}
    </div>
  );
}

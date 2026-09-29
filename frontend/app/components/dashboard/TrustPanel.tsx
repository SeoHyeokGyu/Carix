import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { DashboardMetadata } from "../../lib/api/dashboard";
import type { PeerConditionView } from "../../lib/peerConditions";
import { toSampleCoverage } from "../../lib/sampleCoverage";

const BODY_ID = "trust-panel-body";

interface TrustPanelProps {
  metadata: DashboardMetadata;
  rationale: string | null;
  peerConditions: PeerConditionView | null;
  isNarrow: boolean;
}

/**
 * `S-10` 3층. 새 API 없이 응답에 이미 있던 값으로 판정의 재료를 보인다(설계 §4).
 * 데스크톱은 항상 펼치고 모바일에서만 접는다(D5).
 */
export function TrustPanel({
  metadata,
  rationale,
  peerConditions,
  isNarrow,
}: TrustPanelProps) {
  const [isOpen, setIsOpen] = useState(false);
  const isCollapsed = isNarrow && !isOpen;
  const coverage = toSampleCoverage(
    metadata.sampleSize,
    metadata.salaryMissingRate,
  );

  return (
    <section aria-labelledby="trust-title">
      <Card className="gap-4">
        <div className="flex items-center justify-between gap-2">
          <h3
            id="trust-title"
            className="text-sm font-semibold text-carix-text/80"
          >
            이 판정은 이렇게 나왔습니다
          </h3>
          {isNarrow && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-expanded={isOpen}
              aria-controls={BODY_ID}
              onClick={() => setIsOpen((open) => !open)}
            >
              {isOpen ? "근거 접기" : "근거 펼치기"}
            </Button>
          )}
        </div>
        <dl
          id={BODY_ID}
          hidden={isCollapsed}
          className="grid gap-3 text-sm md:grid-cols-[10rem_1fr]"
        >
          {coverage && (
            <CoverageRow
              label="수집한 채용공고"
              percent={100}
              value={`${coverage.sampleSize.toLocaleString("ko-KR")}건`}
            />
          )}
          {coverage &&
            coverage.withSalary !== null &&
            coverage.withSalaryPercent !== null && (
              <CoverageRow
                label="그중 연봉이 적힌 것"
                percent={coverage.withSalaryPercent}
                value={`${coverage.withSalary.toLocaleString("ko-KR")}건 · ${coverage.withSalaryPercent}%`}
              />
            )}
          {peerConditions && (
            <>
              <dt className="text-carix-text/60">나와 비교한 사람들</dt>
              <dd>
                <PeerConditions view={peerConditions} />
              </dd>
            </>
          )}
          {rationale && (
            <>
              <dt className="text-carix-text/60">판정 근거</dt>
              <dd className="text-carix-text">{rationale}</dd>
            </>
          )}
          <dt className="text-carix-text/60">기준</dt>
          <dd className="text-carix-text/70">
            {[
              `기준일 ${metadata.baseDate}`,
              metadata.wageStat?.statVintage &&
                `임금통계 ${metadata.wageStat.statVintage}`,
              metadata.ruleVersion && `규칙 버전 ${metadata.ruleVersion}`,
            ]
              .filter(Boolean)
              .join(" · ")}
          </dd>
        </dl>
      </Card>
    </section>
  );
}

function CoverageRow({
  label,
  percent,
  value,
}: {
  label: string;
  percent: number;
  value: string;
}) {
  return (
    <>
      <dt className="text-carix-text/60">{label}</dt>
      <dd className="flex items-center gap-3">
        <span
          aria-hidden="true"
          className="h-2 w-40 overflow-hidden rounded-full bg-carix-border"
        >
          <span
            className="block h-full rounded-full bg-carix-info"
            style={{ width: `${percent}%` }}
          />
        </span>
        <span className="text-carix-text">{value}</span>
      </dd>
    </>
  );
}

function PeerConditions({ view }: { view: PeerConditionView }) {
  if (view.kind === "chips") {
    return (
      <ul aria-label="비교 조건" className="flex flex-wrap gap-1.5">
        {view.chips.map((chip) => (
          <li key={chip}>
            <Badge variant="secondary">{chip}</Badge>
          </li>
        ))}
      </ul>
    );
  }
  return (
    <div className="flex flex-col gap-1">
      <span className="text-carix-text">{view.text}</span>
      {view.widened && (
        <span className="text-xs text-carix-warning">
          조건을 넓혀 비교했습니다
        </span>
      )}
    </div>
  );
}

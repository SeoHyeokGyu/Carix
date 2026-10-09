import { useEffect, useRef, useState } from "react";
import { LoadingNotice } from "./LoadingNotice";
import {
  fetchCpi,
  fetchWage,
  type CpiTable,
  type WageTable,
  type WageBaseline,
  type WageValue,
} from "../../../lib/collectorApi";
import {
  CPI_CATEGORY_NAMES,
  formatManwon,
  isSharpCpiChange,
  type ExploreTab,
} from "../../../lib/dataExplore";
import { Button } from "@/components/ui/button";

type PanelTab = Extract<ExploreTab, "cpi" | "wage">;

type Loaded =
  { kind: "cpi"; data: CpiTable } | { kind: "wage"; data: WageTable };

const EMPTY = (
  <p className="text-xs text-carix-text/60">해당 조건의 데이터가 없습니다</p>
);
const MISSING_CELL = "bg-carix-warning/10 text-carix-warning";
// 급변은 원인 미확인이라 오류가 아니다 — 경고색 대신 중립 배경.
const SHARP_CELL = "bg-carix-info/10";
const SHARP_TITLE = "값 확인 — 전월 대비 5% 넘게 변함";

function CpiView({ data }: { data: CpiTable }) {
  if (data.months.length === 0) return EMPTY;
  const ascending = [...data.months].sort((a, b) =>
    a.month.localeCompare(b.month),
  );
  const prevOf = new Map(
    ascending.map((m, i) => [m.month, ascending[i - 1]] as const),
  );
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr className="text-carix-text/60">
            <th className="px-2 py-1 text-left">월</th>
            {data.codes.map((c) => (
              <th key={c} className="px-2 py-1 text-right">
                {CPI_CATEGORY_NAMES[c] ? `${c} ${CPI_CATEGORY_NAMES[c]}` : c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.months.map((m) => (
            <tr key={m.month} className="border-t border-carix-border">
              <td className="px-2 py-1">{m.month}</td>
              {data.codes.map((c) => {
                const v = m.values[c];
                const sharp = isSharpCpiChange(
                  prevOf.get(m.month)?.values[c],
                  v,
                );
                return (
                  <td
                    key={c}
                    data-testid={`cpi-${m.month}-${c}`}
                    data-missing={String(v === undefined)}
                    data-sharp={String(sharp)}
                    title={sharp ? SHARP_TITLE : undefined}
                    className={`px-2 py-1 text-right ${v === undefined ? MISSING_CELL : sharp ? SHARP_CELL : ""}`}
                  >
                    {v === undefined ? "—" : v.toFixed(2)}
                    {sharp && (
                      <>
                        <sup
                          aria-hidden="true"
                          className="ml-0.5 font-semibold"
                        >
                          !
                        </sup>
                        <span className="sr-only">{SHARP_TITLE}</span>
                      </>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const GROSS = "ANNUAL_GROSS_MEDIAN";
const BASE = "ANNUAL_BASE_MEDIAN";

/** 세전 총액 중위값(엔진 기본 기준선)을 크게, 정액급여 중위값과 출처·회차를 작게. */
function WageCellBody({ values }: { values: WageValue[] }) {
  const gross = values.find((v) => v.wageDefinition === GROSS);
  const base = values.find((v) => v.wageDefinition === BASE);
  const origins = [
    ...new Set(values.map((v) => `${v.source} ${v.statVintage}`)),
  ];
  return (
    <div className="space-y-0.5">
      <p className="font-semibold">
        {gross ? formatManwon(gross.wageMedian) : "—"}
      </p>
      {base && (
        <p className="text-xs text-carix-text/60">
          정액 {formatManwon(base.wageMedian)}
        </p>
      )}
      <p className="text-xs text-carix-text/50">{origins.join(" · ")}</p>
    </div>
  );
}

/** 엔진 기준선이 최신 기준 연도 칸의 세전 총액 값과 같은 행인지. 다르면 화면 값과 엔진이 쓰는 값이 갈린다. */
function engineMatches(
  baseline: WageBaseline,
  basePeriod: string,
  values: WageValue[],
): boolean {
  return (
    baseline.basePeriod === basePeriod &&
    values.some(
      (v) =>
        v.wageDefinition === baseline.wageDefinition &&
        v.source === baseline.source &&
        v.statVintage === baseline.statVintage &&
        v.wageMedian === baseline.wageMedian,
    )
  );
}

function EngineBaselineNote({ baseline }: { baseline: WageBaseline }) {
  const definition = baseline.wageDefinition === BASE ? " 정액" : "";
  return (
    <p className="text-[10px] font-semibold text-carix-warning">
      엔진 기준선 {formatManwon(baseline.wageMedian)}
      {definition} ({baseline.basePeriod} {baseline.source})
    </p>
  );
}

function WageView({ data }: { data: WageTable }) {
  if (!data.basePeriod) return EMPTY;
  const basePeriod = data.basePeriod;
  const cell = (f: string, b: string) =>
    data.cells.find((c) => c.jobFamily === f && c.experienceBracket === b);
  const engine = (f: string, b: string) =>
    data.engineBaselines.find(
      (e) => e.jobFamily === f && e.experienceBracket === b,
    );
  return (
    <div className="space-y-2">
      <p className="text-xs text-carix-text/60">
        KOSIS 연령대 기준선 — 직무 구분 없음. 직무 보정은 원티드 연봉 통계 도입
        후
      </p>
      <p className="text-xs text-carix-text/60">기준 연도 {data.basePeriod}</p>
      <table className="w-full text-xs">
        <thead>
          <tr className="text-carix-text/60">
            <th className="px-2 py-1 text-left">직무</th>
            {data.brackets.map((b) => (
              <th key={b} className="px-2 py-1">
                {b}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.families.map((f) => (
            <tr key={f} className="border-t border-carix-border">
              <td className="px-2 py-1">{f}</td>
              {data.brackets.map((b) => {
                const c = cell(f, b);
                const e = engine(f, b);
                const differs =
                  e !== undefined &&
                  !engineMatches(e, basePeriod, c?.values ?? []);
                return (
                  <td
                    key={b}
                    data-testid={`wage-${f}-${b}`}
                    data-missing={String(!c)}
                    data-engine-differs={String(differs)}
                    className={`px-2 py-1 text-center ${!c ? MISSING_CELL : ""}`}
                  >
                    {c ? <WageCellBody values={c.values} /> : "빈 칸"}
                    {differs && e && <EngineBaselineNote baseline={e} />}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** CPI 월 × 품목 표, 임금통계 직무 × 경력 중위값·출처 표. */
export function PurchasingPowerPanel({
  tab,
  from,
  to,
  reloadKey: externalReloadKey = 0,
  onForbidden,
}: {
  tab: PanelTab;
  from?: string;
  to?: string;
  reloadKey?: number;
  onForbidden?: () => void;
}) {
  const onForbiddenRef = useRef(onForbidden);
  useEffect(() => {
    onForbiddenRef.current = onForbidden;
  });
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failed, setFailed] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      setFailed(false);
      setLoaded(null);
      setIsLoading(true);
      try {
        const next: Loaded =
          tab === "cpi"
            ? { kind: "cpi", data: await fetchCpi(from, to) }
            : { kind: "wage", data: await fetchWage() };
        if (cancelled) return;
        setLoaded(next);
        setIsLoading(false);
      } catch (err) {
        if (cancelled) return;
        setIsLoading(false);
        if ((err as { status?: number }).status === 403) {
          onForbiddenRef.current?.();
          return;
        }
        setFailed(true);
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [tab, from, to, retryKey, externalReloadKey]);

  if (failed) {
    return (
      <div
        role="alert"
        className="rounded-lg border border-carix-border p-4 text-xs space-y-2"
      >
        <p>불러오지 못했습니다</p>
        <Button
          variant="link"
          size="sm"
          onClick={() => setRetryKey((k) => k + 1)}
        >
          다시 시도
        </Button>
      </div>
    );
  }
  if (isLoading) return <LoadingNotice />;
  if (loaded?.kind === "cpi" && tab === "cpi") {
    return <CpiView data={loaded.data} />;
  }
  if (loaded?.kind === "wage" && tab === "wage") {
    return <WageView data={loaded.data} />;
  }
  return null;
}

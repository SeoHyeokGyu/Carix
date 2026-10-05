import { useEffect, useState } from "react";
import {
  fetchCpi,
  fetchWage,
  type CpiTable,
  type WageTable,
} from "../../../lib/collectorApi";
import type { ExploreTab } from "../../../lib/dataExplore";
import { Button } from "@/components/ui/button";

type PanelTab = Extract<ExploreTab, "cpi" | "wage">;

type Loaded =
  { kind: "cpi"; data: CpiTable } | { kind: "wage"; data: WageTable };

const EMPTY = (
  <p className="text-xs text-carix-text/60">해당 조건의 데이터가 없습니다</p>
);
const MISSING_CELL = "bg-carix-warning/10 text-carix-warning";

function CpiView({ data }: { data: CpiTable }) {
  if (data.months.length === 0) return EMPTY;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-xs">
        <thead>
          <tr className="text-carix-text/60">
            <th className="px-2 py-1 text-left">월</th>
            {data.codes.map((c) => (
              <th key={c} className="px-2 py-1 text-right">
                {c}
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
                return (
                  <td
                    key={c}
                    data-testid={`cpi-${m.month}-${c}`}
                    data-missing={String(v === undefined)}
                    className={`px-2 py-1 text-right ${v === undefined ? MISSING_CELL : ""}`}
                  >
                    {v === undefined ? "—" : v}
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

function WageView({ data }: { data: WageTable }) {
  if (!data.basePeriod) return EMPTY;
  const cell = (f: string, b: string) =>
    data.cells.find((c) => c.jobFamily === f && c.experienceBracket === b);
  return (
    <div className="space-y-2">
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
                return (
                  <td
                    key={b}
                    data-testid={`wage-${f}-${b}`}
                    data-missing={String(!c)}
                    className={`px-2 py-1 text-center ${!c ? MISSING_CELL : ""}`}
                  >
                    {c ? c.sources.join("·") : "빈 칸"}
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

/** CPI 월 × 품목 표, 임금통계 직무 × 경력 출처 표. 금액은 보이지 않는다. */
export function PurchasingPowerPanel({ tab }: { tab: PanelTab }) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failed, setFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const run = async () => {
      setFailed(false);
      setLoaded(null);
      try {
        const next: Loaded =
          tab === "cpi"
            ? { kind: "cpi", data: await fetchCpi() }
            : { kind: "wage", data: await fetchWage() };
        if (!cancelled) setLoaded(next);
      } catch {
        // NOTE: 403 은 키 모달을 이 패널이 열 수 없어 실패 상태로 둔다
        if (!cancelled) setFailed(true);
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [tab, reloadKey]);

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
          onClick={() => setReloadKey((k) => k + 1)}
        >
          다시 시도
        </Button>
      </div>
    );
  }
  if (loaded?.kind === "cpi" && tab === "cpi") {
    return <CpiView data={loaded.data} />;
  }
  if (loaded?.kind === "wage" && tab === "wage") {
    return <WageView data={loaded.data} />;
  }
  return null;
}

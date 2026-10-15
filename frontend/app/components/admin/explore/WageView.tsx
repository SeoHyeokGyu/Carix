import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import type {
  WageBaseline,
  WageTable,
  WageValue,
} from "../../../lib/collectorApi";
import {
  formatManwon,
  isWageUniformAcrossFamilies,
} from "../../../lib/dataExplore";
import { bracketLabel, familyLabel } from "../../../lib/sourceLabel";
import { CELL, HEAD, META, NUM } from "./exploreStyles";

const GROSS = "ANNUAL_GROSS_MEDIAN";
const BASE = "ANNUAL_BASE_MEDIAN";
const MISSING_CELL = "bg-carix-warning/10 text-carix-warning";

/** 세전 총액 중위값(엔진 기본 기준선)을 크게, 정액급여 중위값과 출처·회차를 작게. */
function WageCellBody({ values }: { values: WageValue[] }) {
  const gross = values.find((v) => v.wageDefinition === GROSS);
  const base = values.find((v) => v.wageDefinition === BASE);
  const origins = [
    ...new Set(values.map((v) => `${v.source} ${v.statVintage}`)),
  ];
  return (
    <div className="space-y-0.5">
      <p className="font-semibold text-carix-text">
        {gross ? formatManwon(gross.wageMedian) : "—"}
      </p>
      {base && (
        <p className="text-[11px] text-carix-text/60">
          정액 {formatManwon(base.wageMedian)}
        </p>
      )}
      <p className="text-[11px] text-carix-text/50">{origins.join(" · ")}</p>
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
    <p className="text-[11px] font-semibold text-carix-warning">
      엔진 기준선 {formatManwon(baseline.wageMedian)}
      {definition} ({baseline.basePeriod} {baseline.source})
    </p>
  );
}

/**
 * 임금통계 직무 × 경력 구간 표. KOSIS 기준선은 직무 구분이 없어 모든 직무가 같은 값이면
 * 「전 직무 공통」 한 줄로 접는다(`isWageUniformAcrossFamilies`).
 */
export function WageView({ data }: { data: WageTable }) {
  if (!data.basePeriod) {
    return (
      <p className="text-xs text-carix-text/60">
        해당 조건의 데이터가 없습니다
      </p>
    );
  }
  const basePeriod = data.basePeriod;
  const cell = (f: string, b: string) =>
    data.cells.find((c) => c.jobFamily === f && c.experienceBracket === b);
  const engine = (f: string, b: string) =>
    data.engineBaselines.find(
      (e) => e.jobFamily === f && e.experienceBracket === b,
    );
  const collapsed = isWageUniformAcrossFamilies(data);
  // 접으면 첫 직무 값으로 그린다 — 모든 직무가 같다는 것을 판정했다.
  const rows = collapsed
    ? [
        {
          key: "ALL",
          family: data.families[0],
          label: "전 직무 공통",
          note: data.families.map(familyLabel).join(", "),
        },
      ]
    : data.families.map((f) => ({
        key: f,
        family: f,
        label: familyLabel(f),
        note: null,
      }));
  return (
    <div className="max-w-4xl space-y-2">
      <p className={META}>
        <span>기준 연도 {basePeriod}</span>
        <span>
          KOSIS 연령대 기준선 — 직무 구분 없음. 직무 보정은 원티드 연봉 통계
          도입 후
        </span>
      </p>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className={HEAD}>직무</TableHead>
            {data.brackets.map((b) => {
              const { label, age } = bracketLabel(b);
              return (
                <TableHead key={b} className={`${HEAD} ${NUM}`}>
                  {label}
                  {age && (
                    <span className="block font-normal text-carix-text/50">
                      {age}
                    </span>
                  )}
                </TableHead>
              );
            })}
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={row.key}>
              <th
                scope="row"
                className={`${CELL} text-left align-top font-medium`}
              >
                {row.label}
                {row.note && (
                  <span className="block text-[11px] font-normal text-carix-text/50">
                    {row.note}
                  </span>
                )}
              </th>
              {data.brackets.map((b) => {
                const c = cell(row.family, b);
                const e = engine(row.family, b);
                const differs =
                  e !== undefined &&
                  !engineMatches(e, basePeriod, c?.values ?? []);
                return (
                  <TableCell
                    key={b}
                    data-testid={`wage-${row.key}-${b}`}
                    data-missing={String(!c)}
                    data-engine-differs={String(differs)}
                    className={`${CELL} ${NUM} align-top ${!c ? MISSING_CELL : ""}`}
                  >
                    {c ? <WageCellBody values={c.values} /> : "빈 칸"}
                    {differs && e && <EngineBaselineNote baseline={e} />}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

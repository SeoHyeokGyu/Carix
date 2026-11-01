import type { EvaluationResponse } from "../../lib/api/scenario";
import {
  cumulativeGap,
  equalizingLine,
  splitAssumptions,
} from "../../lib/simulatorCompare";
import { moveConclusion, signedMan } from "../../lib/simulatorCopy";
import { amountLabel } from "./chartScale";
import { CumulativeChart } from "./CumulativeChart";
import { FirstYearBars } from "./FirstYearBars";

type MoveView = NonNullable<EvaluationResponse["move"]>;

interface CompareResultProps {
  result: EvaluationResponse & { move: MoveView };
  /** 지금 회사에서 예상하는 인상률(%). */
  expectedRaisePercent: number;
}

function StatCard({
  label,
  value,
  sub,
}: {
  label: string;
  value: string;
  sub: string | null;
}) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-carix-border p-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-xl font-semibold">{value}</span>
      {sub && <span className="text-xs text-muted-foreground">{sub}</span>}
    </div>
  );
}

function ChartBlock({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3">
      <h3 className="text-base font-semibold">{title}</h3>
      {children}
    </section>
  );
}

/** 카드 아래 비교 결과. */
export function CompareResult({
  result,
  expectedRaisePercent,
}: CompareResultProps) {
  const { move } = result;
  const { stock, expectedRange } = move;
  const equalizing = equalizingLine(
    move.equalizingRaisePercent,
    expectedRaisePercent,
  );
  const gapAtEnd =
    cumulativeGap(move.stayCumulative, move.moveCumulative).at(-1) ?? 0;
  const { warnings } = splitAssumptions(result.assumptions);
  const forfeited = stock?.forfeitedUnvested ?? 0;

  return (
    <div className="flex flex-col gap-6">
      {/* 바뀐 결론은 라우트의 알림 자리가 읽는다. 첫해 차이·따라잡는 달은 아래 카드가 보인다. */}
      <h2 className="text-2xl font-semibold">
        {moveConclusion(move.crossoverMonth)}
      </h2>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatCard
          label="따라잡는 시점"
          value={
            move.crossoverMonth === null
              ? "4년 안에 없음"
              : `${move.crossoverMonth}개월째`
          }
          sub={null}
        />
        <StatCard
          label="4년 누적 차이"
          value={amountLabel(gapAtEnd)}
          sub={`첫해만 보면 ${amountLabel(move.firstYear.total)}`}
        />
        <StatCard
          label="4년 누적이 같아지는 인상률"
          value={equalizing.value}
          sub={equalizing.note}
        />
      </div>

      {(warnings.length > 0 || forfeited > 0) && (
        <div
          role="note"
          className="flex flex-col gap-1 text-sm font-medium text-carix-warning"
        >
          {warnings.map((text, i) => (
            <p key={i}>{text}</p>
          ))}
          {forfeited > 0 && (
            <p>
              옮기면 아직 못 받은 주식{" "}
              {amountLabel(forfeited).replace(/^[+-]/, "")}원을 포기합니다.
              합계에는 넣지 않았습니다.
            </p>
          )}
        </div>
      )}

      <div className="grid gap-6 sm:grid-cols-2">
        <ChartBlock title="새 회사로 옮겼을 때 누적 차이">
          <CumulativeChart
            stay={move.stayCumulative}
            move={move.moveCumulative}
            crossoverMonth={move.crossoverMonth}
          />
        </ChartBlock>
        <ChartBlock title="첫해 내역">
          <FirstYearBars firstYear={move.firstYear} />
        </ChartBlock>
      </div>

      {(expectedRange.min !== 0 || expectedRange.max !== 0) && (
        <p className="text-sm text-muted-foreground">
          성과급·사이닝 같은 기대 보상은 연 {signedMan(expectedRange.min)} ~{" "}
          {signedMan(expectedRange.max)} 범위로 더 받을 수 있지만 합계에는 넣지
          않았습니다.
        </p>
      )}
      {stock && (
        <p className="text-sm text-muted-foreground">
          1년 안에 받을 주식(합계에 넣지 않음): 지금 회사{" "}
          {signedMan(stock.stayVesting12m ?? 0)}, 새 회사{" "}
          {signedMan(stock.moveVesting12m ?? 0)}
        </p>
      )}
    </div>
  );
}

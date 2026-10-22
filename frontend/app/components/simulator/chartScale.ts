/** 눈금 간격을 1·2·5 × 10^n 으로 맞춘다. */
function niceStep(span: number, targetTicks: number): number {
  const raw = span / targetTicks;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const normalized = raw / magnitude;
  const factor =
    normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10;
  return factor * magnitude;
}

/** `min`~`max` 안의 눈금 값들. 0 이 범위 안이면 눈금에 반드시 들어간다. */
export function niceTicks(min: number, max: number, targetTicks = 4): number[] {
  const step = niceStep(max - min || 1, targetTicks);
  const first = Math.ceil(min / step) * step;
  const ticks: number[] = [];
  for (let value = first; value <= max + step * 1e-9; value += step) {
    ticks.push(Math.abs(value) < step * 1e-9 ? 0 : value);
  }
  return ticks;
}

const MAN_WON = 10_000;
const EOK_IN_MAN = 10_000;

/** 부호 붙인 금액 라벨. 1억(10,000만) 부터 억 단위 — `lib/koreanSalary.ts` 와 같은 경계. */
export function amountLabel(won: number): string {
  const man = Math.round(won / MAN_WON);
  const sign = man > 0 ? "+" : man < 0 ? "-" : "";
  const abs = Math.abs(man);
  if (abs >= EOK_IN_MAN) {
    const eok = Math.round((abs / EOK_IN_MAN) * 100) / 100;
    return `${sign}${eok.toLocaleString("ko-KR")}억`;
  }
  return `${sign}${abs.toLocaleString("ko-KR")}만`;
}

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

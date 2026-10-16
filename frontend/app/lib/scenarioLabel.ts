/**
 * 서버가 이름 없는 계산에 채워 넣던 기본명이다. 지금은 이름 없이(null) 저장하지만
 * 그 전에 쌓인 행이 남아 있어 이름 없음으로 읽는다 — 데이터를 고치지 않고 화면에서 맞춘다.
 */
const LEGACY_DEFAULT_NAME = "기본 시나리오";

const SEOUL_TIME = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

export function scenarioDisplayName(name: string | null): string | null {
  const trimmed = name?.trim();
  if (!trimmed || trimmed === LEGACY_DEFAULT_NAME) return null;
  return trimmed;
}

function formatCalculatedAt(iso: string): string {
  const parts = SEOUL_TIME.formatToParts(new Date(iso));
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "";
  return `${get("year")}. ${get("month")}. ${get("day")}. ${get("hour")}:${get("minute")}`;
}

/**
 * 저장 시나리오 선택지 라벨(기능 명세 US-S1). 계산할 때마다 행이 새로 쌓여 같은 이름이
 * 여러 줄 생기므로 이름이 있어도 계산 시각을 붙인다. 이름이 없으면 시각이 라벨이다.
 */
export function scenarioOptionLabel(scenario: {
  scenarioName: string | null;
  calculatedAt: string;
}): string {
  const time = formatCalculatedAt(scenario.calculatedAt);
  const name = scenarioDisplayName(scenario.scenarioName);
  return name ? `${name} · ${time}` : `${time} 계산`;
}

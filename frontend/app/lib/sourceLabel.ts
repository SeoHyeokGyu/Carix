const LABELS: Record<string, string> = {
  JOBKOREA: "잡코리아",
  SARAMIN: "사람인",
  WANTED: "원티드",
  JUMPIT: "점핏",
};

/** 공고 원천 코드를 사용자 화면 표시명으로 바꾼다. */
export function sourceLabel(code: string): string {
  return LABELS[code] ?? code;
}

const FAMILY_LABELS: Record<string, string> = {
  DEVELOPER: "개발",
  DATA: "데이터",
  AI: "AI",
  OTHER: "기타",
};

/** 직무 코드를 사용자 화면 표시명으로 바꾼다. */
export function familyLabel(code: string): string {
  return FAMILY_LABELS[code] ?? code;
}

/** 경력 구간 표시명과 근거 연령대. 연령대는 collector WageStatIngestService 의 KOSIS 연령 코드 매핑과 같아야 한다. */
const BRACKET_LABELS: Record<string, { label: string; age: string }> = {
  ENTRY: { label: "신입", age: "20~24세" },
  JUNIOR: { label: "주니어", age: "25~29세" },
  MID: { label: "미드", age: "30~34세" },
  SENIOR: { label: "시니어", age: "35~39세" },
  LEAD: { label: "리드", age: "40~44세" },
};

/** 경력 구간 코드를 표시명과 KOSIS 연령대로 바꾼다. 모르는 코드는 연령대 없이 그대로 둔다. */
export function bracketLabel(code: string): {
  label: string;
  age: string | null;
} {
  return BRACKET_LABELS[code] ?? { label: code, age: null };
}

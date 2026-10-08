const LABELS: Record<string, string> = {
  JOBKOREA: "잡코리아",
  SARAMIN: "사람인",
  WANTED: "원티드",
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

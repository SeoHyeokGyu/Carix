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
  MID: { label: "미들", age: "30~34세" },
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

const CONDITION_TOKEN_LABELS: Record<string, string> = {
  ENTRY: BRACKET_LABELS.ENTRY.label,
  JUNIOR: BRACKET_LABELS.JUNIOR.label,
  MID: BRACKET_LABELS.MID.label,
  SENIOR: BRACKET_LABELS.SENIOR.label,
  LEAD: BRACKET_LABELS.LEAD.label,
  LARGE: "대기업",
  MEDIUM: "중견·중소",
  SME: "중소기업",
  STARTUP: "스타트업",
  FULL_TIME: "정규직",
  CONTRACT: "계약직",
  FREELANCE: "프리랜서",
  INTERN: "인턴",
};

const CONDITION_TOKEN_PATTERN = new RegExp(
  `\\b(${Object.keys(CONDITION_TOKEN_LABELS).join("|")})\\b`,
  "g",
);

/**
 * 백엔드 `peerGroup.appliedConditions` 문자열에 포함된 영문 코드(경력 구간·회사 규모·고용 형태)를
 * 사용자 화면용 한국어 라벨로 변환한다.
 */
export function formatAppliedConditions(raw: string): string {
  return raw.replace(
    CONDITION_TOKEN_PATTERN,
    (match) => CONDITION_TOKEN_LABELS[match] ?? match,
  );
}

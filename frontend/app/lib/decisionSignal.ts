/**
 * 결정 신호의 화면 표현 — 문구·톤·설명이 여기 한 곳에만 있다.
 *
 * 전에는 `S-10` 이 서버가 준 `label` 을, `S-11` 이 자기 파일 안의 `getSignalBadge` 를 따로 썼다.
 * 서버 문구가 바뀌면 한쪽만 따라오는 상태였다. 문구의 캐노니컬 표기는 MVP 요구사항 §193-196 이다.
 *
 * 서버 `label` 은 여기 없는 코드가 내려왔을 때만 쓴다 — 신호가 늘어도 프런트 배포 전까지 빈 뱃지가
 * 나가지 않게 한다.
 */
export type SignalTone =
  "positive" | "warning" | "danger" | "info" | "action" | "muted";

export interface DecisionSignalView {
  label: string;
  tone: SignalTone;
  description: string | null;
}

const SIGNALS: Record<
  string,
  { label: string; tone: SignalTone; description: string }
> = {
  SEEK_JOB: {
    label: "이직 탐색",
    tone: "warning",
    description:
      "연봉 격차가 크고 스킬 수요도 올라 있습니다. 적극적인 이직 탐색 구간입니다.",
  },
  NEGOTIATE: {
    label: "협상 검토",
    tone: "action",
    description: "연봉 격차가 협상 가능 구간이고 스킬 가치는 상승세입니다.",
  },
  UPSKILL: {
    label: "스킬 보강 우선",
    tone: "info",
    description: "시장가치와 구매력이 모두 낮습니다. 스킬 보강이 먼저입니다.",
  },
  STAY: {
    label: "현 상태 유지",
    tone: "positive",
    description: "지표가 시장 수준 안에 있습니다. 당장 움직일 이유가 없습니다.",
  },
  INSUFFICIENT_DATA: {
    label: "데이터 부족",
    tone: "danger",
    description: "판정에 필요한 입력이나 시장 표본이 모자랍니다.",
  },
};

/**
 * 톤 → 클래스.
 *
 * NOTE: (cjkang) `action` 만 토큰 밖 팔레트를 쓴다. 의미 색상 토큰이 넷인데 신호가 다섯이라
 * 다섯 번째가 없기 때문이다. UI 통일 계획 P1 에서 `--carix-action` 이 생기면 이 한 줄만 바꾼다.
 */
export const SIGNAL_TONE_CLASS: Record<SignalTone, string> = {
  positive:
    "bg-carix-positive/15 text-carix-positive border border-carix-positive/30",
  warning:
    "bg-carix-warning/15 text-carix-warning border border-carix-warning/30",
  danger: "bg-carix-danger/15 text-carix-danger border border-carix-danger/30",
  info: "bg-carix-info/15 text-carix-info border border-carix-info/30",
  action:
    "bg-purple-500/15 text-purple-600 dark:text-purple-400 border border-purple-500/30",
  muted: "bg-carix-border/40 text-carix-text/70 border border-carix-border",
};

export function resolveDecisionSignal(
  code: string,
  serverLabel?: string | null,
): DecisionSignalView {
  const known = SIGNALS[code];
  if (known) {
    return {
      label: known.label,
      tone: known.tone,
      description: known.description,
    };
  }
  return {
    label: serverLabel || code || "판정 대기",
    tone: "muted",
    description: null,
  };
}

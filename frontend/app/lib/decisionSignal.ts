/**
 * 결정 신호의 화면 표현 — 문구·톤·설명이 여기 한 곳에만 있다.
 *
 * 전에는 `S-10` 이 서버가 준 `label` 을, `S-11` 이 자기 파일 안의 `getSignalBadge` 를 따로 썼다.
 * 서버 문구가 바뀌면 한쪽만 따라오는 상태였다. 문구의 캐노니컬 표기는 MVP 요구사항 §193-196 이다.
 *
 * 서버 `label` 은 여기 없는 코드가 내려왔을 때만 쓴다 — 신호가 늘어도 프런트 배포 전까지 빈 뱃지가
 * 나가지 않게 한다.
 */
import type { Tone } from "@/components/ui/tone-badge";

/** 톤 이름은 `ToneBadge` 소관이다. 기존 import 를 깨지 않으려고 여기서 다시 내보낸다. */
export type SignalTone = Tone;

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
    // 스킬 추세는 단정하지 않는다 — 시장가치 없이 연봉 격차만으로 낸 부분 판정(규칙 1.1.0)도 이 신호다.
    // 추세는 근거 칩(「스킬 가치 상승세」·「시장가치 미반영」·「시장가치 불확실」)이 말한다.
    description: "연봉 격차가 협상을 검토할 만한 구간입니다.",
  },
  UPSKILL: {
    label: "스킬 보강 우선",
    tone: "info",
    description: "보유 스킬 수요가 줄고 있습니다. 스킬 보강이 먼저입니다.",
  },
  STAY: {
    label: "현 상태 유지",
    tone: "positive",
    description: "지금 바로 움직일 신호는 없습니다.",
  },
  INSUFFICIENT_DATA: {
    label: "데이터 부족",
    tone: "danger",
    description: "판정에 필요한 입력이나 시장 표본이 모자랍니다.",
  },
};

/**
 * `SIGNALS` 표의 키에서 파생한 결정 신호 어휘 전체.
 * 손으로 다시 나열하지 않는다: 표가 늘면 여기도 같이 는다.
 */
export const DECISION_SIGNAL_CODES = Object.keys(SIGNALS);

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

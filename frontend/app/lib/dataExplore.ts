import type { PostingItem } from "./collectorApi";

export type ExploreTab = "monthly" | "postings" | "cpi" | "wage";

export const TABS: { id: ExploreTab; label: string }[] = [
  { id: "monthly", label: "월별 현황" },
  { id: "postings", label: "공고" },
  { id: "cpi", label: "CPI" },
  { id: "wage", label: "임금통계" },
];

/** 표본 목표 250 의 80%. 설계 §8 강조 규칙 — collector 점검 기준과 같다. */
export const SAMPLE_WARN_BELOW = 200;
/** 시장가치 동료집단 최소 표본. */
export const FAMILY_MIN_SAMPLE = 10;

export function parseTab(value: string | null): ExploreTab {
  return TABS.some((t) => t.id === value) ? (value as ExploreTab) : "monthly";
}

export const isThinSample = (n: number) => n < SAMPLE_WARN_BELOW;
export const isThinFamily = (n: number) => n < FAMILY_MIN_SAMPLE;

const WON_PER_MANWON = 10_000;

const WON_PER_EOK = 100_000_000;

/** 원 단위 → 「6,123만원」, 1억 이상은 「1억원」·「1억 2,000만원」. */
export function formatManwon(won: number): string {
  if (won >= WON_PER_EOK) {
    const eok = Math.floor(won / WON_PER_EOK);
    const man = Math.floor((won % WON_PER_EOK) / WON_PER_MANWON);
    return man === 0
      ? `${eok}억원`
      : `${eok}억 ${man.toLocaleString("ko-KR")}만원`;
  }
  return `${Math.floor(won / WON_PER_MANWON).toLocaleString("ko-KR")}만원`;
}

/** 공고 제시 연봉 범위. 있는 쪽만 보이고 둘 다 없으면 「미공개」. */
export function formatSalaryRange(
  min: number | null,
  max: number | null,
): string {
  if (min == null && max == null) return "미공개";
  if (min != null && max != null) {
    return min === max
      ? formatManwon(min)
      : `${formatManwon(min)}~${formatManwon(max)}`;
  }
  return min != null
    ? `${formatManwon(min)}~`
    : `~${formatManwon(max as number)}`;
}

type SalaryFields = Pick<
  PostingItem,
  | "salaryMin"
  | "salaryMax"
  | "salaryBandMin"
  | "salaryBandMax"
  | "salaryDisclosure"
>;

/** 공고 표 「제시 연봉」 칸. 공개 연봉만 진하게, 비공개 구간·없음·판정 전은 흐리게. */
export function formatSalaryCell(p: SalaryFields): {
  text: string;
  muted: boolean;
} {
  if (p.salaryDisclosure === "PUBLIC") {
    return { text: formatSalaryRange(p.salaryMin, p.salaryMax), muted: false };
  }
  if (p.salaryDisclosure === "BAND") {
    return {
      text: `비공개 (${formatSalaryRange(p.salaryBandMin, p.salaryBandMax)})`,
      muted: true,
    };
  }
  return {
    text: p.salaryDisclosure === "NONE" ? "미공개" : "판정 전",
    muted: true,
  };
}

import type { JobStatusResponse, PostingItem } from "./collectorApi";

export type ExploreTab = "monthly" | "postings" | "cpi" | "wage";

export const TABS: { id: ExploreTab; label: string }[] = [
  { id: "monthly", label: "월별 현황" },
  { id: "postings", label: "공고" },
  { id: "cpi", label: "CPI" },
  { id: "wage", label: "임금통계" },
];

/** 표본 목표 250 의 80%. 설계 §8 강조 규칙 — collector 점검 기준과 같다. */
export const SAMPLE_WARN_BELOW = 200;
/** 상한 적용 = 같은 회사 월 3건까지. 백엔드 MarketAggregateService.SAMPLE_COMPANY_CAP_PER_MONTH 와 같아야 한다. */
export const SAMPLE_COMPANY_CAP = 3;
/** 시장가치 동료집단 최소 표본. */
export const FAMILY_MIN_SAMPLE = 10;

export function parseTab(value: string | null): ExploreTab {
  return TABS.some((t) => t.id === value) ? (value as ExploreTab) : "monthly";
}

/** 직무별 표본 원천 파라미터 정규화. 공백·대소문자를 무시하고 모르면 잡코리아. */
export function normalizeSampleSource(raw: string | null | undefined) {
  return raw?.trim().toUpperCase() === "WANTED" ? "WANTED" : "JOBKOREA";
}

export const isThinSample = (n: number) => n < SAMPLE_WARN_BELOW;
export const isThinFamily = (n: number) => n < FAMILY_MIN_SAMPLE;

/** URL 의 yyyyMM → `<input type="month">` 의 yyyy-MM. 형식이 다르면 빈 문자열. */
export function toMonthInput(yyyyMM: string | undefined): string {
  return yyyyMM && /^\d{6}$/.test(yyyyMM)
    ? `${yyyyMM.slice(0, 4)}-${yyyyMM.slice(4)}`
    : "";
}

/** `<input type="month">` 의 yyyy-MM → URL 의 yyyyMM. 비었거나 형식이 다르면 undefined. */
export function fromMonthInput(value: string): string | undefined {
  return /^\d{4}-\d{2}$/.test(value) ? value.replace("-", "") : undefined;
}

/** 공고 탭 기본 월 = 지난달. 수집기 관측월이 UTC 기준이라 UTC 로 구한다(설계 §2.5). */
export function defaultPostingMonth(now: Date): string {
  const prev = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1),
  );
  return `${prev.getUTCFullYear()}${String(prev.getUTCMonth() + 1).padStart(2, "0")}`;
}

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
  if (p.salaryDisclosure === "NONE") return { text: "미공개", muted: true };
  // 판정 전이어도 저장된 값(기존 공고의 연봉 또는 구간)이 있으면 함께 보인다.
  const stored =
    p.salaryMin != null || p.salaryMax != null
      ? formatSalaryRange(p.salaryMin, p.salaryMax)
      : p.salaryBandMin != null || p.salaryBandMax != null
        ? formatSalaryRange(p.salaryBandMin, p.salaryBandMax)
        : null;
  return { text: stored ? `판정 전 (${stored})` : "판정 전", muted: true };
}

export type PostingSort = "recent" | "source";

export const POSTING_SORTS: { id: PostingSort; label: string }[] = [
  { id: "recent", label: "최근 수집순" },
  { id: "source", label: "원천·번호순" },
];

export function parsePostingSort(value: string | null): PostingSort {
  return value === "source" ? "source" : "recent";
}

const SEOUL = "Asia/Seoul";

function zonedParts(
  iso: string,
  timeZone: string,
): { y: string; m: string; d: string } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date(iso));
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
  return { y: get("year"), m: get("month"), d: get("day") };
}

/** 수집 시각(UTC instant) → 서울 기준 yyyy-MM-dd. */
export function formatCollectedDate(iso: string): string {
  const { y, m, d } = zonedParts(iso, SEOUL);
  return `${y}-${m}-${d}`;
}

/** 크롤링 포털 → 공고 탭 원천 필터. 점핏 공고는 사람인 원천으로 저장된다. */
const PORTAL_SOURCE: Record<string, string> = {
  JOBKOREA: "JOBKOREA",
  SARAMIN: "SARAMIN",
  JUMPIT: "SARAMIN",
  WANTED: "WANTED",
};

/**
 * 성공한 크롤링 작업 → 수집 데이터 공고 탭(최근 수집순) 링크. 해당 없으면 null.
 * 관측월은 UTC 로 도는 collector 의 `YearMonth.now()` 기준이라 월도 UTC 로 구한다.
 */
export function crawlJobDataHref(
  job: Pick<JobStatusResponse, "jobType" | "status" | "startedAt" | "details">,
): string | null {
  if (job.jobType !== "CRAWL" || job.status !== "SUCCESS") return null;
  const { y, m } = zonedParts(job.startedAt, "UTC");
  const sp = new URLSearchParams({
    tab: "postings",
    month: `${y}${m}`,
    sort: "recent",
  });
  const source = PORTAL_SOURCE[String(job.details?.portal ?? "")];
  if (source) sp.set("source", source);
  return `/admin/data?${sp.toString()}`;
}

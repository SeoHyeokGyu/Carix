import type {
  JobStatusResponse,
  PostingItem,
  WageTable,
  WageValue,
} from "./collectorApi";

export type ExploreTab = "monthly" | "postings" | "cpi" | "wage";

export const TABS: { id: ExploreTab; label: string }[] = [
  { id: "monthly", label: "월별 현황" },
  { id: "postings", label: "공고" },
  { id: "cpi", label: "CPI" },
  { id: "wage", label: "임금통계" },
];

/** 월별 현황에서 상한 적용 표본이 이보다 적으면 강조한다(설계 §8). */
export const SAMPLE_WARN_BELOW = 200;
/** 상한 적용 = 같은 회사 월 3건까지. 백엔드 MarketAggregateService.SAMPLE_COMPANY_CAP_PER_MONTH 와 같아야 한다. */
export const SAMPLE_COMPANY_CAP = 3;
/** 시장가치 동료집단 최소 표본. */
export const FAMILY_MIN_SAMPLE = 10;

export function parseTab(value: string | null): ExploreTab {
  return TABS.some((t) => t.id === value) ? (value as ExploreTab) : "monthly";
}

/** 탭을 바꿀 때의 URL. 월별 현황·CPI 가 같은 기간을 쓰므로 from·to 만 남기고 나머지 조건은 지운다. */
export function tabSwitchParams(
  tab: ExploreTab,
  current: URLSearchParams,
): URLSearchParams {
  const next = new URLSearchParams({ tab });
  for (const key of ["from", "to"]) {
    const value = current.get(key);
    if (value) next.set(key, value);
  }
  return next;
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
  PROGRAMMERS: "PROGRAMMERS",
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

/** KOSIS 소비자물가 지출목적별 12대 분류(2020 기준). 코드는 수집기 KosisCpiClient 의 A~L 순서와 같다. */
export const CPI_CATEGORY_NAMES: Record<string, string> = {
  A: "식료품·비주류음료",
  B: "주류·담배",
  C: "의류·신발",
  D: "주거·수도·광열",
  E: "가정용품·가사서비스",
  F: "보건",
  G: "교통",
  H: "정보통신",
  I: "오락·문화",
  J: "교육",
  K: "음식·숙박",
  L: "기타 상품·서비스",
};

const CPI_SHARP_RATIO = 0.05;

export function isSharpCpiChange(
  prev: number | undefined,
  curr: number | undefined,
): boolean {
  if (prev === undefined || curr === undefined || prev === 0) return false;
  return Math.abs(curr / prev - 1) > CPI_SHARP_RATIO;
}

/** 월별 현황·CPI 기간 입력의 최대 길이(시작·끝 월 포함). */
export const MAX_PERIOD_MONTHS = 36;

/** 기간 입력이 맞지 않으면 화면 문구를, 맞거나 한쪽이 비었으면 null 을 돌린다. 둘 다 yyyyMM. */
export function periodError(
  from: string | undefined,
  to: string | undefined,
): string | null {
  if (!from || !to || !/^\d{6}$/.test(from) || !/^\d{6}$/.test(to)) {
    return null;
  }
  const index = (m: string) => Number(m.slice(0, 4)) * 12 + Number(m.slice(4));
  const span = index(to) - index(from) + 1;
  if (span < 1) return "시작 월이 끝 월보다 늦습니다";
  if (span > MAX_PERIOD_MONTHS) {
    return `기간은 최대 ${MAX_PERIOD_MONTHS}개월입니다`;
  }
  return null;
}

/** 응답 월들의 처음·끝(yyyyMM). 기간 입력의 기본값으로 쓴다. 응답 순서는 믿지 않는다. */
export function periodRangeOf(
  months: { month: string }[],
): { from: string; to: string } | undefined {
  if (months.length === 0) return undefined;
  const sorted = months.map((m) => m.month).sort();
  return { from: sorted[0], to: sorted[sorted.length - 1] };
}

function valuesKey(values: WageValue[]): string {
  return values
    .map(
      (v) => `${v.wageDefinition}|${v.source}|${v.statVintage}|${v.wageMedian}`,
    )
    .sort()
    .join(";");
}

/**
 * 모든 직무가 경력 구간마다 같은 값·같은 엔진 기준선을 갖는지. KOSIS 기준선은 직무 구분이 없어 보통 참이고,
 * 참이면 임금통계 표를 한 줄로 접는다. 엔진 기준선까지 보는 것은 직무별 엔진 차이를 숨기지 않기 위해서다.
 */
export function isWageUniformAcrossFamilies(data: WageTable): boolean {
  if (data.families.length < 2) return false;
  const signature = (family: string) =>
    data.brackets
      .map((b) => {
        const cell = data.cells.find(
          (c) => c.jobFamily === family && c.experienceBracket === b,
        );
        const engine = data.engineBaselines.find(
          (e) => e.jobFamily === family && e.experienceBracket === b,
        );
        const engineKey = engine
          ? `${engine.basePeriod}|${valuesKey([engine])}`
          : "-";
        return `${b}=${cell ? valuesKey(cell.values) : "-"}/${engineKey}`;
      })
      .join(",");
  const first = signature(data.families[0]);
  return data.families.every((f) => signature(f) === first);
}

import {
  type ApiError,
  coreGet,
  coreSend,
  UnauthorizedError,
} from "../coreApi";

/**
 * `com.carix.index.enums.IndexStatus` 와 같은 값이다 (API 규격 §5.1).
 * `OK`·`INSUFFICIENT_HISTORY` 가 아니면 `value`·`changeFromLastMonth`·`status` 는 항상 `null` 이다 —
 * 값이 없는 이유를 이 값이 담는다. 0 이나 100 으로 채우지 않는다.
 * `INSUFFICIENT_HISTORY` 는 값이 있고 비교 기간(기준월부터 12개월)이 짧다는 뜻이다.
 */
export type IndexStatus =
  | "OK"
  | "INSUFFICIENT_SAMPLE"
  | "UPDATE_DELAYED"
  | "INSUFFICIENT_HISTORY"
  | "NOT_AVAILABLE";

export interface IndexValue {
  value: number | null;
  changeFromLastMonth: number | null;
  status: string | null;
  indexStatus: IndexStatus;
  estimateLabel?: string;
}

export interface DecisionSignal {
  code: string;
  label: string;
  rulesApplied: string[];
  rationale: string;
}

export interface PeerGroupMetadata {
  fallbackLevel: number | null;
  appliedConditions: string;
  excluded?: { skill: string; reason: string }[];
}

export interface OpeningWindowMetadata {
  /** 최근 3개월 동료집단 채용 비중(%). */
  recentSharePercent: number | null;
  /** 이전 12개월 동료집단 채용 비중(%). */
  baselineSharePercent: number | null;
  peerSampleSize: number | null;
}

/**
 * 그 사용자의 물가 상승에 가장 크게 기여한 카테고리 (API 규격 §5.1). 서버가 조립한 라벨을 그대로 쓴다.
 * `estimated` 는 그 카테고리 가중치가 기본값으로 채워졌는지다.
 */
export interface TopPriceDriver {
  category: string;
  label: string;
  estimated: boolean;
  /** 사용한 CPI 기준월 `YYYYMM`. */
  cpiMonth: string;
}

export interface DashboardMetadata {
  baseDate: string;
  dataVersion?: string;
  ruleVersion?: string;
  updateDelayed: boolean;
  sources?: { source: string; sampleSize: number }[];
  sampleSize?: number;
  salaryMissingRate?: number | null;
  peerGroup?: PeerGroupMetadata | null;
  wageStat?: {
    wageDefinition?: string | null;
    statVintage?: string | null;
    basis?: string | null;
    source?: string | null;
  } | null;
  expenseWeightStatus?: string | null;
  openingWindow?: OpeningWindowMetadata | null;
  topPriceDriver?: TopPriceDriver | null;
}

/** `ABOVE`·`NONE` 이면 `raiseRatePercent` 는 null. `NONE` 은 구매력 복구선 전용(상승분 없음). */
export type NegotiationTargetStatus = "BELOW" | "ABOVE" | "NONE";

export interface NegotiationTarget {
  raiseRatePercent: number | null;
  status: NegotiationTargetStatus;
}

export interface RecoveryTarget extends NegotiationTarget {
  /** 누적을 시작한 달(YYYYMM) — 마지막 연봉 변경 달, CPI 수집 전이면 CPI 첫 달. */
  sinceYearMonth: string;
}

/** 협상 목표 인상률 셋 (기능 명세 US-D7·§4.8). 비율만 — 목표 금액은 싣지 않는다. */
export interface NegotiationTargets {
  toWageStatMedian: NegotiationTarget | null;
  toPeerP75: NegotiationTarget | null;
  toPurchasingPowerRecovery: RecoveryTarget | null;
}

/**
 * 최근 12개월과 직전 12개월의 소득(월할 연봉·고정상여 + 지급 성과급) 비교.
 * 성과급 기록이 없으면 필드 자체가 `null` 이고, `realChangePercent` 는 직전 12개월 소득이나
 * 개인물가가 없으면 `null` 이다. 지수에는 섞이지 않는다.
 */
export interface BonusInclusiveIncome {
  recentAnnual: number;
  previousAnnual: number;
  realChangePercent: number | null;
  /** 최근 12개월에 지급된 성과급 건수 */
  bonusCount: number;
}

export interface DashboardSummary {
  purchasingPowerIndex: IndexValue;
  marketValueIndex: IndexValue;
  openingWindowIndex: IndexValue;
  /** `NEGOTIATE`·`SEEK_JOB` 의 가장 최근 달에만. */
  negotiationTargets?: NegotiationTargets | null;
  decisionSignal: DecisionSignal | null;
  bonusInclusiveIncome?: BonusInclusiveIncome | null;
  metadata: DashboardMetadata;
}

export type FetchSummaryResult =
  { ok: true; data: DashboardSummary } | { ok: false; error: ApiError };

export interface DashboardRecalculateResponse {
  recalculatedMonths: number;
  fromYearMonth: string;
  summary: DashboardSummary;
}

export type RecalculateResult =
  | { ok: true; data: DashboardRecalculateResponse }
  | { ok: false; error: ApiError };

/**
 * 열화가 정상 응답이다 (설계 §10) — 던지지 않는다. `UnauthorizedError` 만
 * 다시 던진다(가드가 로그인으로 보낸다). 그 밖의 실패는 `{ok:false, error}` 로
 * 감싸 loader 가 마지막 성공 데이터를 잃지 않고 재시도 UI 를 그릴 수 있게 한다.
 */
export async function fetchSummary(): Promise<FetchSummaryResult> {
  try {
    const data = await coreGet<DashboardSummary>("/v1/dashboard/summary");
    return { ok: true, data };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw error;
    return { ok: false, error: error as ApiError };
  }
}

/**
 * 스냅샷 재계산을 트리거한다.
 * 과거 데이터에 새로운 지수(OWI 등)를 소급 계산하거나 스킬·소득 변경 사항을 즉시 반영할 때 사용한다.
 */
export async function recalculateDashboard(
  fromYearMonth?: string,
): Promise<RecalculateResult> {
  try {
    const query = fromYearMonth
      ? `?fromYearMonth=${encodeURIComponent(fromYearMonth)}`
      : "";
    const data = await coreSend<DashboardRecalculateResponse>(
      "POST",
      `/v1/dashboard/recalculate${query}`,
    );
    return { ok: true, data };
  } catch (error) {
    if (error instanceof UnauthorizedError) throw error;
    return { ok: false, error: error as ApiError };
  }
}

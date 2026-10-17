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

export interface DashboardSummary {
  purchasingPowerIndex: IndexValue;
  marketValueIndex: IndexValue;
  openingWindowIndex: IndexValue;
  decisionSignal: DecisionSignal | null;
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

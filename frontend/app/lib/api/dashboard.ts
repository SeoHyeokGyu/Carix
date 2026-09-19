import { coreGet, UnauthorizedError, type ApiError } from "../coreApi";

/**
 * `com.carix.index.enums.IndexStatus` 와 같은 값이다 (API 규격 §5.1).
 * `OK` 가 아니면 `value`·`changeFromLastMonth`·`status` 는 항상 `null` 이다 —
 * 값이 없는 이유를 이 값이 담는다. 0 이나 100 으로 채우지 않는다.
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
  medianWindowDays: number | null;
  /** 상시채용 비율(%). 비율이 아니라 퍼센트다. */
  openEndedRate: number | null;
  deadlineSampleSize: number | null;
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
  wageStat?: { wageDefinition: string; statVintage: string } | null;
  expenseWeightStatus?: string | null;
  openingWindow?: OpeningWindowMetadata | null;
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

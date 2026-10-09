/**
 * Collector 파이프라인 관리자 API 클라이언트.
 */

const STORAGE_KEY_ADMIN = "carix_collector_admin_key";
const BASE_PATH = "/api/v1/admin/collector";

export interface PipelineStatsResponse {
  totalRawIngest: number;
  totalJobPostings: number;
  totalSkills: number;
  totalWageStats: number;
  postingsBySource: Record<string, number>;
}

export interface JobStatusResponse {
  jobId: string;
  jobType: string;
  status: "RUNNING" | "SUCCESS" | "FAILED" | "ACCEPTED" | string;
  startedAt: string;
  completedAt?: string | null;
  details?: Record<string, unknown> | null;
  errorMessage?: string | null;
}

export interface JobExecutionResponse {
  jobId: string;
  jobType: string;
  status: string;
  message: string;
  startedAt: string;
}

export interface CrawlRequest {
  portal?: "JOBKOREA" | "SARAMIN" | "JUMPIT" | "WANTED" | "ALL";
  maxPages?: number;
  delayMs?: number;
}

export interface WageStatRequest {
  startYear?: string;
  endYear?: string;
}

export interface CpiRequest {
  startYearMonth?: string;
  endYearMonth?: string;
}

export interface MarketAggregateRequest {
  month?: string;
  year?: string;
  startYear?: string;
  endYear?: string;
  all?: boolean;
}

export interface SeedLoadRequest {
  dataDir?: string;
}

export interface CollectorApiError {
  code: string;
  message: string;
  status?: number;
}

export function getAdminKey(): string {
  if (typeof window === "undefined") return "";
  return localStorage.getItem(STORAGE_KEY_ADMIN) ?? "";
}

export function setAdminKey(key: string): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(STORAGE_KEY_ADMIN, key.trim());
}

export function clearAdminKey(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(STORAGE_KEY_ADMIN);
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const adminKey = getAdminKey();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };

  if (adminKey) {
    headers["X-Admin-Key"] = adminKey;
  }

  const response = await fetch(`${BASE_PATH}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorData: Partial<CollectorApiError> = {};
    try {
      errorData = (await response.json()) as Partial<CollectorApiError>;
    } catch {
      // JSON 파싱 실패 시 fallback
    }

    if (response.status === 403) {
      throw {
        code: "FORBIDDEN",
        message:
          errorData.message ??
          "관리자 시크릿 키가 누락되었거나 일치하지 않습니다. 상단에서 관리자 키를 확인해 주세요.",
        status: 403,
      };
    }

    if (response.status === 409) {
      throw {
        code: "CONFLICT",
        message:
          errorData.message ??
          "다른 파이프라인 작업이 이미 진행 중입니다. 잠시 후 다시 시도해 주세요.",
        status: 409,
      };
    }

    throw {
      code: errorData.code ?? "UNKNOWN_ERROR",
      message:
        errorData.message ??
        `요청에 실패했습니다. (상태 코드: ${response.status})`,
      status: response.status,
    };
  }

  return (await response.json()) as T;
}

export function fetchPipelineStats(): Promise<PipelineStatsResponse> {
  return request<PipelineStatsResponse>("/stats", { method: "GET" });
}

export function fetchRecentJobs(): Promise<JobStatusResponse[]> {
  return request<JobStatusResponse[]>("/jobs", { method: "GET" });
}

export function fetchJobStatus(jobId: string): Promise<JobStatusResponse> {
  return request<JobStatusResponse>(`/jobs/${encodeURIComponent(jobId)}`, {
    method: "GET",
  });
}

export function triggerCrawl(
  req?: CrawlRequest,
): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/crawl", {
    method: "POST",
    body: JSON.stringify(req ?? {}),
  });
}

export function triggerSkillRefine(): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/refine-skills", {
    method: "POST",
  });
}

export function triggerWageStat(
  req?: WageStatRequest,
): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/wage-stat", {
    method: "POST",
    body: JSON.stringify(req ?? {}),
  });
}

export function triggerCpi(req?: CpiRequest): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/cpi", {
    method: "POST",
    body: JSON.stringify(req ?? {}),
  });
}

export function triggerDeduplication(): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/dedup", {
    method: "POST",
  });
}

export function triggerMarketAggregate(
  req?: MarketAggregateRequest,
): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/aggregate", {
    method: "POST",
    body: JSON.stringify(req ?? {}),
  });
}

export function triggerSeedLoad(
  req?: SeedLoadRequest,
): Promise<JobExecutionResponse> {
  return request<JobExecutionResponse>("/seed-load", {
    method: "POST",
    body: JSON.stringify(req ?? {}),
  });
}

export type PriorityGrade = "CRITICAL" | "HIGH" | "MEDIUM" | "SUFFICIENT";

export interface CollectionPriorityItem {
  jobFamily: string;
  region: string;
  experienceBracket: string;
  currentSampleCount: number;
  requiredSampleSize: number;
  sampleDeficit: number;
  priorityGrade: PriorityGrade;
  priorityReason: string;
  searchKeywordHint: string;
}

export interface CollectionPriorityResponse {
  baseYearMonth: string;
  source: string;
  minSampleSize: number;
  totalEvaluatedCombinations: number;
  underSampledCount: number;
  priorities: CollectionPriorityItem[];
}

export interface CollectionPriorityQuery {
  month?: string;
  source?: string;
  minSampleSize?: number;
  underSampledOnly?: boolean;
}

export function fetchCollectionPriorities(
  query: CollectionPriorityQuery = {},
): Promise<CollectionPriorityResponse> {
  const params = new URLSearchParams();
  if (query.month) params.set("month", query.month);
  if (query.source) params.set("source", query.source);
  if (query.minSampleSize != null)
    params.set("minSampleSize", String(query.minSampleSize));
  if (query.underSampledOnly != null)
    params.set("underSampledOnly", String(query.underSampledOnly));

  const qs = params.toString();
  return request<CollectionPriorityResponse>(
    `/priorities${qs ? `?${qs}` : ""}`,
    {
      method: "GET",
    },
  );
}

export type HealthVerdict = "OK" | "WARN" | "ERROR" | "UNKNOWN";

export interface HealthCheck {
  id: string;
  title: string;
  verdict: HealthVerdict;
  message: string;
  impactUsers: number | null;
  action: string | null;
  actionLink: string | null;
  drillDown: string | null;
  evidence: Record<string, unknown>;
}

export interface DataHealthReport {
  evaluatedAt: string;
  referenceMonth: string;
  previousMonth: string;
  engineRuleVersion: string | null;
  /** 데이터를 읽지 못했으면 null — 사용자 수를 0 으로 지어내지 않는다. */
  summary: {
    users: number;
    fullyAnswered: number;
    gaps: { answer: string; reason: string; users: number }[];
  } | null;
  checks: HealthCheck[];
}

export function fetchDataHealth(): Promise<DataHealthReport> {
  return request<DataHealthReport>("/health", { method: "GET" });
}

export interface MonthlyOverview {
  from: string;
  to: string;
  months: {
    month: string;
    jobkoreaSampled: number;
    jobkoreaSampledCapped: number;
    jobkoreaCrawled: number;
    saraminCrawled: number;
    sampledByFamily: Record<string, number>;
    wantedSampled: number;
    wantedSampledCapped: number;
    wantedCrawled: number;
    current: boolean;
  }[];
}

export interface PostingItem {
  source: string;
  externalId: string;
  url: string | null;
  title: string;
  companyName: string | null;
  jobFamily: string | null;
  experienceMin: number | null;
  experienceMax: number | null;
  employmentType: string | null;
  region: string | null;
  postedStartDate: string | null;
  postedEndDate: string | null;
  firstSeenMonth: string | null;
  sampledMonth: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryBandMin: number | null;
  salaryBandMax: number | null;
  salaryDisclosure: "PUBLIC" | "BAND" | "NONE" | "UNKNOWN";
  skills: string[];
  collectedVia: string | null;
  sourceJobTags: string | null;
  collectedAt: string;
}

export interface PostingPage {
  total: number;
  page: number;
  size: number;
  items: PostingItem[];
}

export interface PostingQuery {
  month: string;
  source?: string;
  sampled?: boolean;
  family?: string;
  company?: string;
  skill?: string;
  disclosure?: string;
  sort?: "recent" | "source";
  page: number;
}

function qs(
  params: Record<string, string | number | boolean | undefined>,
): string {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== "") sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : "";
}

export function fetchMonthlyOverview(
  from?: string,
  to?: string,
  sampleSource?: string,
): Promise<MonthlyOverview> {
  return request<MonthlyOverview>(
    `/explore/monthly${qs({ from, to, sampleSource })}`,
    {
      method: "GET",
    },
  );
}

export function fetchPostings(q: PostingQuery): Promise<PostingPage> {
  return request<PostingPage>(`/explore/postings${qs({ ...q })}`, {
    method: "GET",
  });
}

export interface CpiTable {
  codes: string[];
  months: { month: string; values: Record<string, number> }[];
  missing: { month: string; codes: string[] }[];
}

/** 정의·출처별 최신 회차 중위값(연간, 원). */
export interface WageValue {
  wageDefinition: string;
  source: string;
  statVintage: string;
  wageMedian: number;
}

/** 엔진(core-api)이 전국 경로에서 실제로 고르는 기준선. 연도를 가리지 않는다. */
export interface WageBaseline {
  jobFamily: string;
  experienceBracket: string;
  region: string;
  basePeriod: string;
  wageDefinition: string;
  source: string;
  statVintage: string;
  wageMedian: number;
}

export interface WageTable {
  basePeriod: string | null;
  families: string[];
  brackets: string[];
  cells: {
    jobFamily: string;
    experienceBracket: string;
    sources: string[];
    values: WageValue[];
  }[];
  missing: string[];
  engineBaselines: WageBaseline[];
}

export function fetchCpi(from?: string, to?: string): Promise<CpiTable> {
  return request<CpiTable>(`/explore/cpi${qs({ from, to })}`, {
    method: "GET",
  });
}

export function fetchWage(): Promise<WageTable> {
  return request<WageTable>("/explore/wage", { method: "GET" });
}

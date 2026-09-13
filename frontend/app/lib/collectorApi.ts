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

export interface RecentPostingResponse {
  id: string;
  source: string;
  externalId: string;
  title: string;
  companyName?: string;
  jobFamily?: string;
  salaryMin?: number;
  salaryMax?: number;
  region?: string;
  experienceMin?: number;
  experienceMax?: number;
  skills: string[];
  collectedAt: string;
}

export interface CrawlRequest {
  portal?: "JOBKOREA" | "SARAMIN" | "ALL";
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

export function fetchRecentPostings(
  limit = 20,
): Promise<RecentPostingResponse[]> {
  return request<RecentPostingResponse[]>(`/recent-postings?limit=${limit}`, {
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

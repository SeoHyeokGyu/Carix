import { useState } from "react";
import {
  triggerCrawl,
  triggerSkillRefine,
  triggerWageStat,
  triggerCpi,
  triggerDeduplication,
  triggerMarketAggregate,
  triggerSeedLoad,
  type JobExecutionResponse,
} from "../../lib/collectorApi";

interface PipelineControlPanelProps {
  isJobRunning: boolean;
  onJobTriggered: (res: JobExecutionResponse) => void;
  onError: (err: { message: string }) => void;
}

type TabType =
  "crawl" | "refine" | "wage" | "cpi" | "dedup" | "aggregate" | "seed";

export function PipelineControlPanel({
  isJobRunning,
  onJobTriggered,
  onError,
}: PipelineControlPanelProps) {
  const [activeTab, setActiveTab] = useState<TabType>("crawl");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 크롤링 폼 상태
  const [portal, setPortal] = useState<
    "ALL" | "JOBKOREA" | "SARAMIN" | "JUMPIT"
  >("ALL");
  const [maxPages, setMaxPages] = useState<number>(5);
  const [delayMs, setDelayMs] = useState<number>(500);

  // 임금 통계 폼 상태
  const [wageMode, setWageMode] = useState<"latest" | "custom">("latest");
  const [startYear, setStartYear] = useState<string>("2020");
  const [endYear, setEndYear] = useState<string>("2024");

  // CPI 폼 상태
  const [cpiMode, setCpiMode] = useState<"latest" | "custom">("latest");
  const [cpiStartYearMonth, setCpiStartYearMonth] = useState<string>("202401");
  const [cpiEndYearMonth, setCpiEndYearMonth] = useState<string>("202412");

  // 시장 사전 집계 폼 상태
  const [aggMode, setAggMode] = useState<"all" | "month" | "year" | "range">(
    "all",
  );
  const [aggMonth, setAggMonth] = useState<string>("2024-05");
  const [aggYear, setAggYear] = useState<string>("2024");
  const [aggStartYear, setAggStartYear] = useState<string>("2020");
  const [aggEndYear, setAggEndYear] = useState<string>("2024");

  // 시드 적재 폼 상태
  const [seedDataDir, setSeedDataDir] = useState<string>("");

  const handleExecute = async () => {
    if (isJobRunning || isSubmitting) return;

    setIsSubmitting(true);
    try {
      let res: JobExecutionResponse;

      switch (activeTab) {
        case "crawl":
          res = await triggerCrawl({ portal, maxPages, delayMs });
          break;
        case "refine":
          res = await triggerSkillRefine();
          break;
        case "wage":
          res = await triggerWageStat(
            wageMode === "latest" ? {} : { startYear, endYear },
          );
          break;
        case "cpi":
          res = await triggerCpi(
            cpiMode === "latest"
              ? {}
              : {
                  startYearMonth: cpiStartYearMonth,
                  endYearMonth: cpiEndYearMonth,
                },
          );
          break;
        case "dedup":
          res = await triggerDeduplication();
          break;
        case "aggregate":
          if (aggMode === "all") {
            res = await triggerMarketAggregate({ all: true });
          } else if (aggMode === "month") {
            res = await triggerMarketAggregate({ month: aggMonth });
          } else if (aggMode === "year") {
            res = await triggerMarketAggregate({ year: aggYear });
          } else {
            res = await triggerMarketAggregate({
              startYear: aggStartYear,
              endYear: aggEndYear,
            });
          }
          break;
        case "seed":
          res = await triggerSeedLoad(
            seedDataDir ? { dataDir: seedDataDir } : {},
          );
          break;
      }

      onJobTriggered(res);
    } catch (err: unknown) {
      const error = err as { message?: string };
      onError({ message: error.message ?? "작업 실행에 실패했습니다." });
    } finally {
      setIsSubmitting(false);
    }
  };

  const tabs: { id: TabType; label: string; icon: string }[] = [
    { id: "crawl", label: "채용공고 크롤링", icon: "🕷️" },
    { id: "refine", label: "스킬 일괄 재정제", icon: "✨" },
    { id: "wage", label: "KOSIS 공식 임금 수집", icon: "📊" },
    { id: "cpi", label: "KOSIS 소비자물가지수 수집", icon: "💰" },
    { id: "dedup", label: "공고 중복제거 (Dedup)", icon: "🔗" },
    { id: "aggregate", label: "시장 데이터 사전 집계", icon: "📈" },
    { id: "seed", label: "파일 시드 적재", icon: "💾" },
  ];

  return (
    <div className="rounded-xl border border-carix-border bg-carix-surface shadow-xs overflow-hidden">
      <div className="border-b border-carix-border bg-carix-bg/50 px-4 pt-3 flex flex-wrap gap-1">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-1.5 rounded-t-lg px-3.5 py-2 text-xs font-medium transition-colors border-b-2 -mb-px ${
              activeTab === tab.id
                ? "border-carix-info bg-carix-surface text-carix-info shadow-xs"
                : "border-transparent text-carix-text/70 hover:text-carix-text hover:bg-carix-border/30"
            }`}
          >
            <span>{tab.icon}</span>
            <span>{tab.label}</span>
          </button>
        ))}
      </div>

      <div className="p-5">
        {/* 1. 채용공고 크롤링 탭 */}
        {activeTab === "crawl" && (
          <div className="space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                외부 채용 포털 실시간 스크래핑 및 정규화
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                잡코리아, 사람인의 IT/개발 채용공고를 실시간 수집하여 raw_ingest
                및 job_posting에 적재합니다.
              </p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-2">
              <div>
                <label className="block text-xs font-medium text-carix-text mb-1">
                  대상 포털 (Portal)
                </label>
                <select
                  value={portal}
                  onChange={(e) =>
                    setPortal(
                      e.target.value as
                        "ALL" | "JOBKOREA" | "SARAMIN" | "JUMPIT",
                    )
                  }
                  className="w-full rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                >
                  <option value="ALL">전체 포털 (잡코리아 + 사람인)</option>
                  <option value="JOBKOREA">잡코리아 (JobKorea)</option>
                  <option value="SARAMIN">사람인 (Saramin)</option>
                  <option value="JUMPIT">점핏 (Jumpit)</option>
                </select>
              </div>

              {portal === "JUMPIT" ? (
                <p className="sm:col-span-2 self-end text-xs text-carix-text/60">
                  점핏은 목록 전체를 순회합니다(약 30분, 그동안 다른 작업은
                  대기).
                </p>
              ) : (
                <>
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      수집 페이지 수 (Max Pages)
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={50}
                      value={maxPages}
                      onChange={(e) => setMaxPages(Number(e.target.value))}
                      className="w-full rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      요청 지연 간격 (Delay ms)
                    </label>
                    <input
                      type="number"
                      min={100}
                      max={5000}
                      step={100}
                      value={delayMs}
                      onChange={(e) => setDelayMs(Number(e.target.value))}
                      className="w-full rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {/* 2. 스킬 일괄 재정제 탭 */}
        {activeTab === "refine" && (
          <div className="space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                채용공고 스킬 데이터 일괄 재정제 (Skill Refinement)
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                DB에 크롤링된 전체 채용공고의 제목과 원천 태그를 최신 스킬
                사전을 기반으로 재분석하여 스킬 매핑을 최신화합니다.
              </p>
            </div>

            <div className="rounded-lg border border-carix-border/70 bg-carix-bg/50 p-3.5 space-y-2 text-xs text-carix-text/80">
              <div className="flex items-start gap-2">
                <span className="text-carix-info font-bold">✓</span>
                <span>
                  <strong>제목 기반 자동 추출:</strong> 태그가 누락된 공고라도
                  제목에 포함된 기술 스택(Java, Spring, React, Python 등)을 단어
                  경계를 지켜 고속 자동 추출합니다.
                </span>
              </div>
              <div className="flex items-start gap-2">
                <span className="text-carix-info font-bold">✓</span>
                <span>
                  <strong>노이즈 및 가짜 스킬 제거:</strong> 1글자 알파벳이나
                  사전 미등록 한글 일반 명사(전자, 성남, 신규 등) 같은 오염
                  데이터를 깨끗하게 정제합니다.
                </span>
              </div>
              <div className="flex items-start gap-2">
                <span className="text-carix-info font-bold">✓</span>
                <span>
                  <strong>사전 캐시 자동 갱신:</strong> 실행 시 DB의 core.skill
                  및 core.skill_alias 최신 사전을 먼저 메모리에 리로드한 후 전체
                  공고를 처리합니다.
                </span>
              </div>
            </div>
          </div>
        )}

        {/* 3. KOSIS 임금 통계 탭 */}
        {activeTab === "wage" && (
          <div className="space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                KOSIS 국가 공식 임금 통계 수집 및 보간
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                통계청 OpenAPI(고용형태별근로실태조사)로부터 24개 구간별 임금
                분포를 수집하고 P50 중위연봉을 보간 산출합니다.
              </p>
            </div>

            <div className="pt-2 space-y-3">
              <div className="flex items-center gap-4">
                <label className="flex items-center gap-1.5 text-xs text-carix-text cursor-pointer">
                  <input
                    type="radio"
                    name="wageMode"
                    checked={wageMode === "latest"}
                    onChange={() => setWageMode("latest")}
                    className="text-carix-info"
                  />
                  <span>최신 1개년 수집</span>
                </label>
                <label className="flex items-center gap-1.5 text-xs text-carix-text cursor-pointer">
                  <input
                    type="radio"
                    name="wageMode"
                    checked={wageMode === "custom"}
                    onChange={() => setWageMode("custom")}
                    className="text-carix-info"
                  />
                  <span>연도 구간 지정 수집 (Backfill)</span>
                </label>
              </div>

              {wageMode === "custom" && (
                <div className="flex items-center gap-3 pt-1">
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      시작 연도
                    </label>
                    <input
                      type="text"
                      placeholder="2020"
                      value={startYear}
                      onChange={(e) => setStartYear(e.target.value)}
                      className="w-28 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                  <span className="text-carix-text/50 pt-5">~</span>
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      종료 연도
                    </label>
                    <input
                      type="text"
                      placeholder="2024"
                      value={endYear}
                      onChange={(e) => setEndYear(e.target.value)}
                      className="w-28 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* 3-2. KOSIS 소비자물가지수 탭 */}
        {activeTab === "cpi" && (
          <div className="space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                KOSIS 지출목적별 소비자물가지수 수집
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                통계청 OpenAPI(지출목적별 소비자물가지수, DT_1J22001)로부터 12대
                분류(A~L) CPI를 수집하여 core.cpi_observation에 적재합니다.
              </p>
            </div>

            <div className="pt-2 space-y-3">
              <div className="flex items-center gap-4">
                <label className="flex items-center gap-1.5 text-xs text-carix-text cursor-pointer">
                  <input
                    type="radio"
                    name="cpiMode"
                    checked={cpiMode === "latest"}
                    onChange={() => setCpiMode("latest")}
                    className="text-carix-info"
                  />
                  <span>최신 1개월 수집</span>
                </label>
                <label className="flex items-center gap-1.5 text-xs text-carix-text cursor-pointer">
                  <input
                    type="radio"
                    name="cpiMode"
                    checked={cpiMode === "custom"}
                    onChange={() => setCpiMode("custom")}
                    className="text-carix-info"
                  />
                  <span>연월 구간 지정 수집 (Backfill)</span>
                </label>
              </div>

              {cpiMode === "custom" && (
                <div className="flex items-center gap-3 pt-1">
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      시작 연월 (YYYYMM)
                    </label>
                    <input
                      type="text"
                      placeholder="202401"
                      value={cpiStartYearMonth}
                      onChange={(e) => setCpiStartYearMonth(e.target.value)}
                      className="w-32 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                  <span className="text-carix-text/50 pt-5">~</span>
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      종료 연월 (YYYYMM)
                    </label>
                    <input
                      type="text"
                      placeholder="202412"
                      value={cpiEndYearMonth}
                      onChange={(e) => setCpiEndYearMonth(e.target.value)}
                      className="w-32 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* 3. 공고 중복제거 탭 */}
        {activeTab === "dedup" && (
          <div className="space-y-3">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                원천 간(잡코리아-사람인) 채용공고 중복제거 배치
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                전체 공고를 대상으로 회사명 정규화 + 제목 키워드 Jaccard 유사도
                매칭을 수행하고 Union-Find 클러스터링으로 동일 공고 그룹에
                동일한 dedup_group_id를 부여합니다.
              </p>
            </div>
            <div className="rounded-lg bg-carix-info/5 border border-carix-info/20 p-3 text-xs text-carix-info leading-relaxed">
              💡 중복제거 배치는 전체 적재된 공고를 상호 비교하므로 공고 건수에
              따라 수십 초 정도 소요될 수 있습니다.
            </div>
          </div>
        )}

        {/* 4. 시장 사전 집계 탭 */}
        {activeTab === "aggregate" && (
          <div className="space-y-4">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                채용 시장 데이터 다차원 사전 집계 (Pre-aggregation)
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                직군, 경력구간, 지역, 회사규모 차원별 채용 공고 수와 스킬
                수요율을 사전 계산하여 core.market_aggregate에 저장합니다.
              </p>
            </div>

            <div className="space-y-3 pt-2">
              <div className="flex flex-wrap items-center gap-4 text-xs text-carix-text">
                <label className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="radio"
                    name="aggMode"
                    checked={aggMode === "all"}
                    onChange={() => setAggMode("all")}
                  />
                  <span>전체 공고 기준월 일괄 집계</span>
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="radio"
                    name="aggMode"
                    checked={aggMode === "month"}
                    onChange={() => setAggMode("month")}
                  />
                  <span>특정 년월 (YYYY-MM)</span>
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="radio"
                    name="aggMode"
                    checked={aggMode === "year"}
                    onChange={() => setAggMode("year")}
                  />
                  <span>특정 연도 (1~12월)</span>
                </label>
                <label className="flex items-center gap-1.5 cursor-pointer">
                  <input
                    type="radio"
                    name="aggMode"
                    checked={aggMode === "range"}
                    onChange={() => setAggMode("range")}
                  />
                  <span>연도 구간 지정</span>
                </label>
              </div>

              {aggMode === "month" && (
                <div>
                  <label className="block text-xs font-medium text-carix-text mb-1">
                    대상 년월 (YYYY-MM)
                  </label>
                  <input
                    type="text"
                    value={aggMonth}
                    onChange={(e) => setAggMonth(e.target.value)}
                    placeholder="2024-05"
                    className="w-36 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                  />
                </div>
              )}

              {aggMode === "year" && (
                <div>
                  <label className="block text-xs font-medium text-carix-text mb-1">
                    대상 연도 (YYYY)
                  </label>
                  <input
                    type="text"
                    value={aggYear}
                    onChange={(e) => setAggYear(e.target.value)}
                    placeholder="2024"
                    className="w-36 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                  />
                </div>
              )}

              {aggMode === "range" && (
                <div className="flex items-center gap-3">
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      시작 연도
                    </label>
                    <input
                      type="text"
                      value={aggStartYear}
                      onChange={(e) => setAggStartYear(e.target.value)}
                      placeholder="2020"
                      className="w-28 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                  <span className="text-carix-text/50 pt-5">~</span>
                  <div>
                    <label className="block text-xs font-medium text-carix-text mb-1">
                      종료 연도
                    </label>
                    <input
                      type="text"
                      value={aggEndYear}
                      onChange={(e) => setAggEndYear(e.target.value)}
                      placeholder="2024"
                      className="w-28 rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
                    />
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* 5. 시드 데이터 적재 탭 */}
        {activeTab === "seed" && (
          <div className="space-y-3">
            <div>
              <h3 className="text-sm font-semibold text-carix-text">
                로컬/서버 파일시스템 스냅샷 시드 데이터 일괄 적재
              </h3>
              <p className="text-xs text-carix-text/60 mt-0.5">
                보존된 원문 JSON 파일들로부터 core.raw_ingest와
                core.job_posting을 초기 구축합니다.
              </p>
            </div>

            <div>
              <label className="block text-xs font-medium text-carix-text mb-1">
                데이터 디렉토리 경로 (미입력 시 기본 경로 자동 탐색)
              </label>
              <input
                type="text"
                placeholder="예: data/output 또는 backend/collector/data/output"
                value={seedDataDir}
                onChange={(e) => setSeedDataDir(e.target.value)}
                className="w-full max-w-lg rounded-md border border-carix-border bg-carix-bg px-3 py-1.5 text-xs text-carix-text"
              />
            </div>
          </div>
        )}

        {/* 실행 액션 바 */}
        <div className="mt-6 flex items-center justify-between border-t border-carix-border pt-4">
          <span className="text-xs text-carix-text/50">
            {isJobRunning
              ? "⚠️ 현재 다른 파이프라인 작업이 진행 중입니다."
              : "버튼을 클릭하면 비동기 백그라운드 작업이 시작됩니다."}
          </span>

          <button
            type="button"
            onClick={handleExecute}
            disabled={isJobRunning || isSubmitting}
            className={`flex items-center gap-2 rounded-lg px-5 py-2 text-xs font-semibold text-white transition-all shadow-xs ${
              isJobRunning || isSubmitting
                ? "bg-carix-text/30 cursor-not-allowed"
                : "bg-carix-info hover:opacity-90 active:scale-95"
            }`}
          >
            {isSubmitting ? (
              <>
                <span className="animate-spin inline-block">⏳</span>
                <span>요청 전송 중...</span>
              </>
            ) : (
              <>
                <span>🚀</span>
                <span>작업 시작하기</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

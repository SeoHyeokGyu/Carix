import { useState, useEffect, useCallback } from "react";
import {
  fetchSalaryDistribution,
  fetchSkillDemands,
  type SalaryDistributionResponse,
  type SkillDemandResponse,
} from "../../lib/dashboardMarketApi";
import { SalaryDistributionChart } from "../../components/dashboard/SalaryDistributionChart";
import { SkillDemandSection } from "../../components/dashboard/SkillDemandSection";

type TrendsTab = "INCOME_VS_PRICE" | "SKILL_DEMAND" | "SALARY_DISTRIBUTION";

export function meta() {
  return [
    { title: "추이 분석 | Carix" },
    {
      name: "description",
      content: "소득 대 개인물가, 스킬 수요, 동료집단 연봉 분포 추이 분석",
    },
  ];
}

export default function DashboardTrends() {
  const [activeTab, setActiveTab] = useState<TrendsTab>("SKILL_DEMAND");

  // 데이터 상태
  const [salaryData, setSalaryData] =
    useState<SalaryDistributionResponse | null>(null);
  const [skillData, setSkillData] = useState<SkillDemandResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [salaryRes, skillRes] = await Promise.allSettled([
        fetchSalaryDistribution(),
        fetchSkillDemands(),
      ]);

      if (salaryRes.status === "fulfilled") {
        setSalaryData(salaryRes.value);
      }
      if (skillRes.status === "fulfilled") {
        setSkillData(skillRes.value);
      }

      if (salaryRes.status === "rejected" && skillRes.status === "rejected") {
        setErrorMessage("시장 분석 데이터를 불러오는 데 실패했습니다.");
      }
    } catch {
      setErrorMessage("데이터 조회 중 예기치 못한 오류가 발생했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
      {/* 1. 헤더 영역 */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-2 border-b border-carix-border pb-4">
        <div>
          <span className="font-display text-xs font-semibold tracking-widest text-carix-info">
            S-14
          </span>
          <h1 className="mt-1 font-display text-2xl font-bold text-carix-text sm:text-3xl">
            추이 분석
          </h1>
          <p className="mt-1 text-sm text-carix-text/70">
            소득 대 개인물가, 스킬 수요, 동료집단 연봉 분포를 다각도로 비교
            분석합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void loadData()}
          disabled={isLoading}
          className="rounded-lg border border-carix-border bg-carix-surface px-3 py-1.5 text-xs text-carix-text hover:bg-carix-bg disabled:opacity-50"
        >
          {isLoading ? "새로고침 중..." : "새로고침 ↻"}
        </button>
      </div>

      {/* 2. 탭 네비게이션 (와이어프레임 기준 3탭) */}
      <div className="mb-6 flex gap-2 border-b border-carix-border">
        <button
          type="button"
          onClick={() => setActiveTab("SKILL_DEMAND")}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === "SKILL_DEMAND"
              ? "border-carix-info text-carix-info font-bold"
              : "border-transparent text-carix-text/60 hover:text-carix-text"
          }`}
        >
          스킬 수요
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("SALARY_DISTRIBUTION")}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === "SALARY_DISTRIBUTION"
              ? "border-carix-info text-carix-info font-bold"
              : "border-transparent text-carix-text/60 hover:text-carix-text"
          }`}
        >
          연봉 분포
        </button>
        <button
          type="button"
          onClick={() => setActiveTab("INCOME_VS_PRICE")}
          className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
            activeTab === "INCOME_VS_PRICE"
              ? "border-carix-info text-carix-info font-bold"
              : "border-transparent text-carix-text/60 hover:text-carix-text"
          }`}
        >
          소득 vs 물가
        </button>
      </div>

      {/* 3. 에러 알림 */}
      {errorMessage && (
        <div
          role="alert"
          className="mb-6 rounded-lg border border-carix-danger/30 bg-carix-danger/10 px-4 py-3 text-sm text-carix-danger flex items-center justify-between"
        >
          <span>{errorMessage}</span>
          <button
            type="button"
            onClick={() => void loadData()}
            className="text-xs font-bold underline"
          >
            다시 시도
          </button>
        </div>
      )}

      {/* 4. 탭 콘텐츠 영역 */}
      {isLoading ? (
        <div className="rounded-xl border border-carix-border bg-carix-surface p-12 text-center text-sm text-carix-text/60">
          시장 분석 데이터를 집계 및 분석하고 있습니다...
        </div>
      ) : (
        <div>
          {/* 탭 1: 스킬 수요 */}
          {activeTab === "SKILL_DEMAND" && (
            <div>
              {skillData ? (
                <SkillDemandSection data={skillData} />
              ) : (
                <div className="rounded-xl border border-carix-border bg-carix-surface p-8 text-center text-sm text-carix-text/60">
                  스킬 수요 데이터를 불러오지 못했습니다.
                </div>
              )}
            </div>
          )}

          {/* 탭 2: 연봉 분포 */}
          {activeTab === "SALARY_DISTRIBUTION" && (
            <div>
              {salaryData ? (
                <SalaryDistributionChart data={salaryData} />
              ) : (
                <div className="rounded-xl border border-carix-border bg-carix-surface p-8 text-center text-sm text-carix-text/60">
                  연봉 분포 데이터를 불러오지 못했습니다.
                </div>
              )}
            </div>
          )}

          {/* 탭 3: 소득 vs 물가 */}
          {activeTab === "INCOME_VS_PRICE" && (
            <div className="rounded-xl border border-dashed border-carix-border bg-carix-surface p-8 text-center">
              <h3 className="text-base font-semibold text-carix-text">
                소득 대 개인물가 지수 추이
              </h3>
              <p className="mt-2 text-xs text-carix-text/60">
                기준월(100) 대비 소득 지수선과 개인물가 지수선 및 구매력 손실
                음영 차트입니다.
              </p>
              <div className="mt-6 rounded bg-carix-bg p-4 text-xs text-carix-text/50">
                [소득 vs 개인물가 지수 시계열 차트 연동 예정 영역]
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

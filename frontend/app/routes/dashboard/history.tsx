import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router";
import {
  fetchSnapshotHistory,
  type SnapshotHistoryResponse,
  type SnapshotPoint,
} from "../../lib/dashboardMarketApi";

export function meta() {
  return [
    { title: "월별 스냅샷 이력 | Carix" },
    {
      name: "description",
      content: "과거 월별 구매력 지수, 시장가치 지수 및 결정 신호 스냅샷 이력",
    },
  ];
}

/**
 * YYYYMM 또는 YYYY-MM 형식을 "YYYY-MM"으로 정규화
 */
function formatYearMonth(ym: string): string {
  const clean = ym.replace("-", "");
  if (clean.length === 6) {
    return `${clean.slice(0, 4)}-${clean.slice(4, 6)}`;
  }
  return ym;
}

/**
 * 결정 신호 한글명 및 스타일 반환
 */
function getSignalBadge(signal: string) {
  switch (signal) {
    case "SEEK_JOB":
      return {
        label: "이직 탐색",
        className:
          "bg-carix-warning/15 text-carix-warning border border-carix-warning/30",
      };
    case "NEGOTIATE":
      return {
        label: "협상 검토",
        className:
          "bg-purple-500/15 text-purple-400 border border-purple-500/30",
      };
    case "UPSKILL":
      return {
        label: "스킬 보강 우선",
        className:
          "bg-carix-info/15 text-carix-info border border-carix-info/30",
      };
    case "STAY":
      return {
        label: "현 상태 유지",
        className:
          "bg-carix-positive/15 text-carix-positive border border-carix-positive/30",
      };
    case "INSUFFICIENT_DATA":
      return {
        label: "데이터 부족",
        className:
          "bg-carix-danger/15 text-carix-danger border border-carix-danger/30",
      };
    default:
      return {
        label: signal || "판정 대기",
        className:
          "bg-carix-border/40 text-carix-text/70 border border-carix-border",
      };
  }
}

export default function DashboardHistory() {
  const [historyData, setHistoryData] =
    useState<SnapshotHistoryResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedSnapshot, setSelectedSnapshot] =
    useState<SnapshotPoint | null>(null);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await fetchSnapshotHistory();
      setHistoryData(data);
    } catch {
      setErrorMessage("월별 스냅샷 이력을 불러오는 데 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  const snapshots = historyData?.snapshots ?? [];
  const isSingleOrEmpty = snapshots.length <= 1;

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
      {/* 1. 헤더 영역 */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-2 border-b border-carix-border pb-4">
        <div>
          <span className="font-display text-xs font-semibold tracking-widest text-carix-info">
            S-11
          </span>
          <h1 className="mt-1 font-display text-2xl font-bold text-carix-text sm:text-3xl">
            월별 스냅샷 이력
          </h1>
          <p className="mt-1 text-sm text-carix-text/70">
            과거 월별 구매력 지수, 시장가치 지수 및 커리어 의사결정 신호
            기록입니다.
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

      {/* 2. 에러 알림 */}
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

      {/* 3. 콘텐츠 영역 */}
      {isLoading ? (
        <div className="rounded-xl border border-carix-border bg-carix-surface p-12 text-center text-sm text-carix-text/60 shadow-xs">
          스냅샷 이력을 조회하고 있습니다...
        </div>
      ) : snapshots.length === 0 ? (
        /* 스냅샷 0개 비어있음 상태 */
        <div
          data-testid="history-empty-state"
          className="rounded-xl border border-dashed border-carix-border bg-carix-surface p-12 text-center shadow-xs"
        >
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-carix-info/10 text-xl">
            📋
          </div>
          <h4 className="text-base font-bold text-carix-text">
            기록된 월별 스냅샷이 없습니다.
          </h4>
          <p className="mt-2 text-sm text-carix-text/70">
            다음 달부터 변화를 볼 수 있습니다.
          </p>
          <div className="mt-6">
            <Link
              to="/onboarding/profile"
              className="inline-flex items-center rounded-lg bg-carix-info px-4 py-2 text-xs font-semibold text-white shadow hover:opacity-90"
            >
              온보딩 정보 확인하기
            </Link>
          </div>
        </div>
      ) : (
        <div className="space-y-6">
          {/* 스냅샷 1개 이하 시 안내 카드 (와이어프레임 준수) */}
          {isSingleOrEmpty && (
            <div
              data-testid="history-single-notice"
              className="rounded-xl border border-dashed border-carix-border bg-carix-surface p-6 text-center shadow-xs"
            >
              <h4 className="text-sm font-semibold text-carix-text">
                초기 스냅샷이 생성되었습니다
              </h4>
              <p className="mt-1 text-xs text-carix-text/60">
                다음 달부터 변화를 볼 수 있습니다.
              </p>
            </div>
          )}

          {/* 스냅샷 이력 테이블 */}
          <div className="overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-carix-border bg-carix-bg/60 text-xs font-semibold text-carix-text/70">
                  <tr>
                    <th className="px-4 py-3.5 sm:px-6">기준월</th>
                    <th className="px-4 py-3.5">구매력 지수</th>
                    <th className="px-4 py-3.5">시장가치 지수</th>
                    <th className="px-4 py-3.5">결정 신호</th>
                    <th className="hidden px-4 py-3.5 sm:table-cell">
                      데이터 기준일
                    </th>
                    <th className="px-4 py-3.5 text-right">상세</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-carix-border">
                  {snapshots.map((item) => {
                    const signalInfo = getSignalBadge(item.decisionSignal);
                    const isSelected =
                      selectedSnapshot?.baseYearMonth === item.baseYearMonth;

                    return (
                      <tr
                        key={item.baseYearMonth}
                        onClick={() =>
                          setSelectedSnapshot(isSelected ? null : item)
                        }
                        className={`cursor-pointer transition-colors hover:bg-carix-bg/40 ${
                          isSelected ? "bg-carix-info/5" : ""
                        }`}
                      >
                        {/* 기준월 */}
                        <td className="px-4 py-3.5 sm:px-6 font-semibold text-carix-text">
                          {formatYearMonth(item.baseYearMonth)}
                        </td>

                        {/* 구매력 지수 */}
                        <td className="px-4 py-3.5">
                          {item.purchasingPowerIndex != null ? (
                            <span className="font-medium text-carix-text">
                              {item.purchasingPowerIndex.toFixed(1)}
                            </span>
                          ) : (
                            <span className="inline-block rounded bg-carix-border/30 px-2 py-0.5 text-[11px] text-carix-text/60">
                              데이터 없음
                            </span>
                          )}
                        </td>

                        {/* 시장가치 지수 */}
                        <td className="px-4 py-3.5">
                          {item.marketValueIndex != null ? (
                            <span className="font-medium text-carix-text">
                              {item.marketValueIndex.toFixed(1)}
                            </span>
                          ) : (
                            <span className="inline-block rounded bg-carix-border/30 px-2 py-0.5 text-[11px] text-carix-text/60">
                              데이터 없음
                            </span>
                          )}
                        </td>

                        {/* 결정 신호 */}
                        <td className="px-4 py-3.5">
                          <span
                            className={`inline-block rounded px-2.5 py-1 text-xs font-semibold ${signalInfo.className}`}
                          >
                            {signalInfo.label}
                          </span>
                        </td>

                        {/* 데이터 기준일 */}
                        <td className="hidden px-4 py-3.5 text-xs text-carix-text/60 sm:table-cell">
                          {item.rationale?.baseDate ?? "-"}
                        </td>

                        {/* 액션 */}
                        <td className="px-4 py-3.5 text-right text-xs">
                          <span className="text-carix-info hover:underline">
                            {isSelected ? "닫기 ▲" : "보기 ▼"}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* 선택된 행 상세 아코디언 (US-D4 근거, 데이터 버전, 사유 표시) */}
            {selectedSnapshot && (
              <div
                data-testid="snapshot-detail-panel"
                className="border-t border-carix-border bg-carix-bg/30 p-6"
              >
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-carix-border/80 pb-3">
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-carix-text">
                      {formatYearMonth(selectedSnapshot.baseYearMonth)} 스냅샷
                      산출 근거
                    </h3>
                    <span
                      className={`rounded px-2 py-0.5 text-xs font-semibold ${
                        getSignalBadge(selectedSnapshot.decisionSignal)
                          .className
                      }`}
                    >
                      {getSignalBadge(selectedSnapshot.decisionSignal).label}
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setSelectedSnapshot(null)}
                    className="text-xs text-carix-text/60 hover:text-carix-text"
                  >
                    닫기 ✕
                  </button>
                </div>

                <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 text-xs">
                  {/* 적용된 판정 규칙 */}
                  <div className="rounded-xl border border-carix-border bg-carix-surface p-4.5 shadow-xs">
                    <span className="font-semibold text-carix-text">
                      적용된 규칙 (Rules Applied)
                    </span>
                    <div className="mt-2 space-y-1">
                      {selectedSnapshot.rationale?.rulesApplied &&
                      selectedSnapshot.rationale.rulesApplied.length > 0 ? (
                        selectedSnapshot.rationale.rulesApplied.map(
                          (rule, idx) => (
                            <div
                              key={idx}
                              className="rounded bg-carix-bg p-1.5 text-carix-text/80 font-mono text-[11px]"
                            >
                              • {rule}
                            </div>
                          ),
                        )
                      ) : (
                        <p className="text-carix-text/50">
                          기록된 적용 규칙 없음
                        </p>
                      )}
                    </div>
                  </div>

                  {/* 제외된 스킬 및 사유 */}
                  <div className="rounded-xl border border-carix-border bg-carix-surface p-4.5 shadow-xs">
                    <span className="font-semibold text-carix-text">
                      제외된 스킬 (Excluded Skills)
                    </span>
                    <div className="mt-2 space-y-1">
                      {selectedSnapshot.rationale?.excluded &&
                      selectedSnapshot.rationale.excluded.length > 0 ? (
                        selectedSnapshot.rationale.excluded.map(
                          (skill, idx) => (
                            <div
                              key={idx}
                              className="rounded bg-carix-bg p-1.5 text-carix-warning font-mono text-[11px]"
                            >
                              ✕ {skill}
                            </div>
                          ),
                        )
                      ) : (
                        <p className="text-carix-text/50">
                          제외된 스킬 없음 (전체 반영됨)
                        </p>
                      )}
                    </div>
                  </div>
                </div>

                {/* 데이터 부족 시 입력 화면 유도 (US-D4) */}
                {selectedSnapshot.decisionSignal === "INSUFFICIENT_DATA" && (
                  <div className="mt-4 rounded-lg border border-carix-danger/30 bg-carix-danger/5 p-4 text-xs">
                    <div className="font-bold text-carix-danger">
                      ⚠️ 입력 데이터가 부족하여 지수를 정상 판정하지 못했습니다.
                    </div>
                    <p className="mt-1 text-carix-text/70">
                      소득 내역 또는 보유 스킬 정보를 보강하시면 다음 스냅샷에서
                      정상적인 지표를 확인하실 수 있습니다.
                    </p>
                    <div className="mt-3 flex gap-2">
                      <Link
                        to="/onboarding/income"
                        className="rounded border border-carix-danger/40 bg-carix-surface px-3 py-1.5 font-medium text-carix-danger hover:bg-carix-danger/10"
                      >
                        소득 정보 입력하기
                      </Link>
                      <Link
                        to="/onboarding/skills"
                        className="rounded border border-carix-danger/40 bg-carix-surface px-3 py-1.5 font-medium text-carix-danger hover:bg-carix-danger/10"
                      >
                        보유 스킬 입력하기
                      </Link>
                    </div>
                  </div>
                )}

                {/* 버전 메타데이터 */}
                <div className="mt-4 flex flex-wrap gap-4 text-[11px] text-carix-text/50 border-t border-carix-border/50 pt-3">
                  <span>
                    데이터 버전: {selectedSnapshot.dataVersion || "-"}
                  </span>
                  <span>규칙 버전: {selectedSnapshot.ruleVersion || "-"}</span>
                  <span>
                    기준일: {selectedSnapshot.rationale?.baseDate || "-"}
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* 4. 하단 각주 및 안내 */}
          <div className="rounded-lg bg-carix-bg/40 p-4 text-xs text-carix-text/70 border border-carix-border/60">
            <p className="flex items-center gap-1.5">
              <span>📌</span>
              <span>
                각 스냅샷은 계산 당시의 데이터·규칙 버전을 함께 보존합니다 (조회
                전용, 과거 스냅샷 직접 수정 불가).
              </span>
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

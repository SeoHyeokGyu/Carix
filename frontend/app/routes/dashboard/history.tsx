import { useState, useEffect, useCallback, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { SnapshotDetailPanel } from "../../components/dashboard/SnapshotDetailPanel";
import { SignalBadge } from "../../components/dashboard/SignalBadge";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";
import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import {
  fetchSnapshotHistory,
  type SnapshotHistoryResponse,
  type SnapshotPoint,
} from "../../lib/dashboardMarketApi";
import { UnauthorizedError } from "../../lib/coreApi";
import { LOGIN_PATH } from "../../lib/routeGuards";

export function meta() {
  return [
    { title: "지난 판정 | Carix" },
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

/** 지수 셀. 값이 없는 달은 0 이나 100 으로 채우지 않고 「데이터 없음」이다. */
function IndexCell({
  value,
  isRetroactive = false,
  badge,
}: {
  value: number | null | undefined;
  /** 당시 판정이 없어 현재 규칙으로 소급 계산한 값이면 흐리게 보인다. */
  isRetroactive?: boolean;
  badge?: ReactNode;
}) {
  return (
    <td className="px-4 py-3.5">
      {value != null ? (
        <span
          className={`font-medium ${
            isRetroactive ? "text-carix-text/50" : "text-carix-text"
          }`}
        >
          {value.toFixed(1)}
        </span>
      ) : (
        <span className="inline-block rounded bg-carix-border/30 px-2 py-0.5 text-[11px] text-carix-text/60">
          데이터 없음
        </span>
      )}
      {badge}
    </td>
  );
}

export default function DashboardHistory() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const showAll = params.get("show") === "all";
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
    } catch (error) {
      // 세션이 없으면 다시 시도해도 401 이다. 문자열 오류로 접으면 로그인으로 돌아갈 길이 없다.
      if (error instanceof UnauthorizedError) {
        void navigate(LOGIN_PATH, { replace: true });
        return;
      }
      setErrorMessage("월별 스냅샷 이력을 불러오는 데 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadData();
  }, [loadData]);

  const snapshots = historyData?.snapshots ?? [];
  const recordedCount = snapshots.filter((s) => s.recorded).length;
  const noneRecorded = recordedCount === 0;
  const visible =
    showAll || noneRecorded ? snapshots : snapshots.filter((s) => s.recorded);
  const isSingleOrEmpty = visible.length <= 1;

  const toggleShowAll = (checked: boolean) => {
    const next = new URLSearchParams(params);
    if (checked) next.set("show", "all");
    else next.delete("show");
    setParams(next, { replace: true });
    // 숨겨지는 소급 계산 달의 상세가 열린 채 남지 않게 닫는다.
    if (!checked && selectedSnapshot && !selectedSnapshot.recorded) {
      setSelectedSnapshot(null);
    }
  };

  return (
    <PageFrame>
      <PageHeader
        back={{ to: "/dashboard", label: "판정" }}
        title="지난 판정"
        description="그 달에 Carix 가 보여 준 판정의 기록입니다. 지금 규칙으로 다시 계산한 값과 함께 봅니다."
        divider
      />

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
        <LoadingState message="스냅샷 이력을 조회하고 있습니다..." />
      ) : snapshots.length === 0 ? (
        /* 스냅샷 0개 비어있음 상태 */
        <EmptyState
          icon="📋"
          title="기록된 월별 스냅샷이 없습니다."
          description="다음 달부터 변화를 볼 수 있습니다."
        >
          <Link
            to="/onboarding/profile"
            className="inline-flex items-center rounded-lg bg-carix-info px-4 py-2 text-xs font-semibold text-white shadow hover:opacity-90"
          >
            온보딩 정보 확인하기
          </Link>
        </EmptyState>
      ) : (
        <div className="space-y-6">
          {/* 스냅샷 1개 이하 시 안내 카드 (와이어프레임 준수) */}
          {isSingleOrEmpty && (
            <div
              data-testid="history-single-notice"
              className="rounded-xl border border-dashed border-carix-border bg-carix-surface p-6 text-center shadow-xs"
            >
              <p className="text-sm font-semibold text-carix-text">
                초기 스냅샷이 생성되었습니다
              </p>
              <p className="mt-1 text-xs text-carix-text/60">
                다음 달부터 변화를 볼 수 있습니다.
              </p>
            </div>
          )}

          <div className="space-y-1">
            <label className="inline-flex items-center gap-2 text-xs text-carix-text">
              <input
                type="checkbox"
                checked={showAll || noneRecorded}
                disabled={noneRecorded}
                onChange={(e) => toggleShowAll(e.target.checked)}
              />
              소급 계산한 달도 보기
            </label>
            {noneRecorded && (
              <p className="text-xs text-carix-text/60">
                아직 그 달에 기록된 판정이 없어 지금 규칙으로 계산한 값을 보여
                줍니다.
              </p>
            )}
          </div>

          {/* 스냅샷 이력 테이블 */}
          <div className="overflow-hidden rounded-xl border border-carix-border bg-carix-surface shadow-xs">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-carix-border bg-carix-bg/60 text-xs font-semibold text-carix-text/70">
                  <tr>
                    <th className="px-4 py-3.5 sm:px-6">기준월</th>
                    <th className="px-4 py-3.5">구매력 지수</th>
                    <th className="px-4 py-3.5">시장가치 지수</th>
                    <th className="px-4 py-3.5">개방창 지수</th>
                    <th className="px-4 py-3.5">결정 신호</th>
                    <th className="hidden px-4 py-3.5 sm:table-cell">
                      데이터 기준일
                    </th>
                    <th className="px-4 py-3.5 text-right">상세</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-carix-border">
                  {visible.map((item) => {
                    const shown = item.recorded ?? item;
                    const isRetroactive = item.recorded === null;
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

                        <IndexCell
                          value={shown.purchasingPowerIndex}
                          isRetroactive={isRetroactive}
                          badge={
                            isRetroactive && (
                              <span className="ml-1.5 rounded bg-carix-border/30 px-1.5 py-0.5 text-[11px] text-carix-text/60">
                                소급 계산
                              </span>
                            )
                          }
                        />
                        <IndexCell
                          value={shown.marketValueIndex}
                          isRetroactive={isRetroactive}
                        />
                        <IndexCell
                          value={shown.openingWindowIndex}
                          isRetroactive={isRetroactive}
                        />

                        {/* 결정 신호 */}
                        <td className="px-4 py-3.5">
                          <SignalBadge
                            code={shown.decisionSignal}
                            serverLabel={shown.label}
                            className="px-2.5 py-1"
                          />
                          {item.changed && (
                            <span className="ml-1 rounded bg-carix-warning/10 px-1.5 py-0.5 text-[11px]">
                              지금 다시 계산하면: {item.label}
                            </span>
                          )}
                        </td>

                        {/* 데이터 기준일 */}
                        <td className="hidden px-4 py-3.5 text-xs text-carix-text/60 sm:table-cell">
                          {shown.rationale?.baseDate ?? "-"}
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

            {selectedSnapshot && (
              <SnapshotDetailPanel
                point={selectedSnapshot}
                onClose={() => setSelectedSnapshot(null)}
              />
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
    </PageFrame>
  );
}

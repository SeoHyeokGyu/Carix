import { type ReactNode, useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";

import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ToneBadge } from "@/components/ui/tone-badge";

import { SignalBadge } from "../../components/dashboard/SignalBadge";
import { SnapshotDetailPanel } from "../../components/dashboard/SnapshotDetailPanel";
import { UnauthorizedError } from "../../lib/coreApi";
import {
  fetchSnapshotHistory,
  type SnapshotHistoryResponse,
  type SnapshotPoint,
} from "../../lib/dashboardMarketApi";
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
    <TableCell>
      {value != null ? (
        <span
          className={`font-medium ${
            isRetroactive ? "text-carix-text/50" : "text-carix-text"
          }`}
        >
          {value.toFixed(1)}
        </span>
      ) : (
        <Badge variant="secondary">데이터 없음</Badge>
      )}
      {badge}
    </TableCell>
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
      setErrorMessage("월별 스냅샷 이력을 불러오지 못했습니다");
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
        back={{ to: "/dashboard", label: "대시보드" }}
        title="지난 판정"
        description="그 달에 Carix 가 보여 준 판정의 기록입니다. 지금 규칙으로 다시 계산한 값과 함께 봅니다."
      />

      {/* 3. 콘텐츠 영역 */}
      {isLoading ? (
        <LoadingState message="스냅샷 이력을 조회하고 있습니다..." />
      ) : errorMessage ? (
        <EmptyState
          tone="danger"
          icon="⚠️"
          title={errorMessage}
          description="잠시 후 다시 시도해 주세요."
        >
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => void loadData()}
          >
            다시 시도
          </Button>
        </EmptyState>
      ) : snapshots.length === 0 ? (
        /* 스냅샷 0개 비어있음 상태 */
        <EmptyState
          icon="📋"
          title="기록된 월별 스냅샷이 없습니다."
          description="다음 달부터 변화를 볼 수 있습니다."
        >
          <Link to="/onboarding/profile" className={buttonVariants()}>
            온보딩 정보 확인하기
          </Link>
        </EmptyState>
      ) : (
        <div className="space-y-6">
          {/* 스냅샷이 하나뿐이면 아래 표에 그 한 행이 있다 — 빈 상태 박스가 아니라 표 위 한 줄로 알린다. */}
          {isSingleOrEmpty && (
            <p
              data-testid="history-single-notice"
              className="text-sm text-muted-foreground"
            >
              첫 판정이 기록됐습니다. 다음 달부터 변화를 볼 수 있습니다.
            </p>
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
          {/* 상세 패널이 표 밑에 같은 틀로 붙어야 해서 틀은 Table 이 아니라 Card 가 진다. */}
          <Card className="gap-0 overflow-hidden p-0">
            <Table variant="plain">
              <TableHeader>
                <TableRow>
                  <TableHead className="sm:px-6">판정한 달</TableHead>
                  <TableHead>구매력 지수</TableHead>
                  <TableHead>시장가치 지수</TableHead>
                  <TableHead>채용 활발도</TableHead>
                  <TableHead>결정 신호</TableHead>
                  <TableHead className="hidden sm:table-cell">
                    데이터 기준일
                  </TableHead>
                  <TableHead className="text-right">상세</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {visible.map((item) => {
                  const shown = item.recorded ?? item;
                  const isRetroactive = item.recorded === null;
                  const isSelected =
                    selectedSnapshot?.baseYearMonth === item.baseYearMonth;

                  return (
                    <TableRow
                      key={item.baseYearMonth}
                      onClick={() =>
                        setSelectedSnapshot(isSelected ? null : item)
                      }
                      className={`cursor-pointer transition-colors hover:bg-carix-bg/40 ${
                        isSelected ? "bg-carix-info/5" : ""
                      }`}
                    >
                      {/* 판정한 달 */}
                      <TableCell className="font-semibold sm:px-6">
                        {formatYearMonth(item.baseYearMonth)}
                      </TableCell>

                      <IndexCell
                        value={shown.purchasingPowerIndex}
                        isRetroactive={isRetroactive}
                        badge={
                          isRetroactive && (
                            <Badge variant="secondary" className="ml-1.5">
                              소급 계산
                            </Badge>
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
                      <TableCell>
                        <SignalBadge
                          code={shown.decisionSignal}
                          serverLabel={shown.label}
                          className="px-2.5 py-1"
                        />
                        {item.changed && (
                          <ToneBadge tone="warning" className="ml-1">
                            지금 다시 계산하면: {item.label}
                          </ToneBadge>
                        )}
                      </TableCell>

                      {/* 데이터 기준일 */}
                      <TableCell className="hidden text-xs text-carix-text/60 sm:table-cell">
                        {shown.rationale?.baseDate ?? "-"}
                      </TableCell>

                      {/* 액션 */}
                      <TableCell className="text-right text-xs">
                        <span className="text-carix-info hover:underline">
                          {isSelected ? "닫기 ▲" : "보기 ▼"}
                        </span>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>

            {selectedSnapshot && (
              <SnapshotDetailPanel
                point={selectedSnapshot}
                onClose={() => setSelectedSnapshot(null)}
              />
            )}
          </Card>

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

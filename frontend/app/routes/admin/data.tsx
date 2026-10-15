import { EmptyState } from "@/components/ui/empty-state";
import { PageFrame } from "@/components/layout/PageFrame";
import { PageHeader } from "@/components/layout/PageHeader";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";
import { ThemeToggle } from "../../components/ThemeToggle";
import { AdminHeader } from "../../components/admin/AdminHeader";
import { DataHealthStrip } from "../../components/admin/DataHealthStrip";
import { AdminKeyModal } from "../../components/admin/AdminKeyModal";
import { MonthlyOverviewTable } from "../../components/admin/explore/MonthlyOverviewTable";
import { LoadingNotice } from "../../components/admin/explore/LoadingNotice";
import { PostingsExplorer } from "../../components/admin/explore/PostingsExplorer";
import { PostingsFilters } from "../../components/admin/explore/PostingsFilters";
import { PeriodFilter } from "../../components/admin/explore/PeriodFilter";
import { CpiView } from "../../components/admin/explore/CpiView";
import { WageView } from "../../components/admin/explore/WageView";
import { CONTROL, FIELD } from "../../components/admin/explore/exploreStyles";
import { Button } from "@/components/ui/button";
import {
  fetchCpi,
  fetchMonthlyOverview,
  fetchPostings,
  fetchWage,
  type CpiTable,
  type MonthlyOverview,
  type PostingPage,
  type WageTable,
} from "../../lib/collectorApi";
import {
  normalizeSampleSource,
  parsePostingSort,
  parseTab,
  periodRangeOf,
  tabSwitchParams,
  TABS,
  type ExploreTab,
} from "../../lib/dataExplore";

export function meta() {
  return [{ title: "수집 데이터 | Carix 관리자" }];
}

type Loaded =
  | { kind: "monthly"; data: MonthlyOverview }
  | { kind: "postings"; data: PostingPage }
  | { kind: "cpi"; data: CpiTable }
  | { kind: "wage"; data: WageTable };

export default function DataExplorePage() {
  const [params, setParams] = useSearchParams();
  const tab = parseTab(params.get("tab"));
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failed, setFailed] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [healthKey, setHealthKey] = useState(0);

  const month = params.get("month") ?? "";
  const sampledParam = params.get("sampled");
  const sampled =
    sampledParam === "true"
      ? true
      : sampledParam === "false"
        ? false
        : undefined;
  const rawSampleSource = params.get("sampleSource") ?? undefined;
  const sampleSource = normalizeSampleSource(rawSampleSource);
  const page = Math.max(0, Math.trunc(Number(params.get("page") ?? "0") || 0));

  const from = params.get("from") ?? undefined;
  const to = params.get("to") ?? undefined;
  const isMonthValid = /^\d{6}$/.test(month);

  const paramsKey = params.toString();
  // 서버가 정한 월을 URL 에 채우면 effect 가 다시 도는데, 이미 받은 결과이므로 다시 조회하지 않는다.
  const loadedKeyRef = useRef<string | null>(null);

  useEffect(() => {
    const fetchKey = `${paramsKey}|${reloadKey}`;
    if (loadedKeyRef.current === fetchKey) {
      loadedKeyRef.current = null;
      return;
    }
    let cancelled = false;
    const q = new URLSearchParams(paramsKey);
    const willFetch = tab !== "postings" || !month || isMonthValid;
    const run = async () => {
      setFailed(false);
      setLoaded(null);
      setIsLoading(willFetch);
      try {
        let next: Loaded | null = null;
        if (tab === "monthly") {
          next = {
            kind: "monthly",
            data: await fetchMonthlyOverview(from, to, rawSampleSource),
          };
        } else if (tab === "cpi") {
          next = { kind: "cpi", data: await fetchCpi(from, to) };
        } else if (tab === "wage") {
          next = { kind: "wage", data: await fetchWage() };
        } else if (!month || isMonthValid) {
          next = {
            kind: "postings",
            data: await fetchPostings({
              month: month || undefined,
              sampled,
              source: q.get("source") ?? undefined,
              family: q.get("family") ?? undefined,
              company: q.get("company") ?? undefined,
              skill: q.get("skill") ?? undefined,
              disclosure: q.get("disclosure") ?? undefined,
              sort: parsePostingSort(q.get("sort")),
              page,
            }),
          };
        }
        if (cancelled) return;
        setLoaded(next);
        setIsLoading(false);
        if (next?.kind === "postings" && !month && next.data.month) {
          const sp = new URLSearchParams(paramsKey);
          sp.set("month", next.data.month);
          loadedKeyRef.current = `${sp.toString()}|${reloadKey}`;
          setParams(sp, { replace: true });
        }
      } catch (err) {
        if (cancelled) return;
        setIsLoading(false);
        if ((err as { status?: number }).status === 403) {
          setIsKeyModalOpen(true);
          return;
        }
        setFailed(true);
      }
    };
    void run();
    return () => {
      cancelled = true;
    };
  }, [
    tab,
    month,
    isMonthValid,
    from,
    to,
    rawSampleSource,
    sampled,
    page,
    paramsKey,
    reloadKey,
    setParams,
  ]);

  // 기간 입력의 기본값. 월별 현황은 서버가 정한 범위를 돌려주고, CPI 는 응답 월에서 구한다.
  const shownRange =
    loaded?.kind === "monthly"
      ? { from: loaded.data.from, to: loaded.data.to }
      : loaded?.kind === "cpi"
        ? periodRangeOf(loaded.data.months)
        : undefined;

  const openKeyModal = useCallback(() => setIsKeyModalOpen(true), []);

  const reload = () => setReloadKey((k) => k + 1);

  const switchTab = (next: ExploreTab) =>
    setParams(tabSwitchParams(next, params));

  const update = (next: Record<string, string | undefined>) => {
    const sp = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? sp.set(k, v) : sp.delete(k)));
    setParams(sp);
  };

  const periodFilter = (
    <PeriodFilter
      key={tab}
      from={from}
      to={to}
      shown={shownRange}
      onApply={(nextFrom, nextTo) => update({ from: nextFrom, to: nextTo })}
    />
  );

  return (
    <div className="min-h-screen bg-carix-bg text-carix-text">
      <ThemeToggle />
      <AdminHeader active="data" onOpenKeyModal={openKeyModal} />
      <PageFrame
        as="main"
        width="admin"
        aria-busy={isLoading}
        className="space-y-4"
      >
        <PageHeader title="수집 데이터" />
        <DataHealthStrip onAuthError={openKeyModal} refreshKey={healthKey} />
        <div
          role="tablist"
          aria-label="수집 데이터 종류"
          className="flex gap-1 border-b border-carix-border"
        >
          {TABS.map((t) => {
            const selected = tab === t.id;
            return (
              <Button
                key={t.id}
                type="button"
                variant="ghost"
                role="tab"
                id={`data-tab-${t.id}`}
                aria-selected={selected}
                aria-controls="data-panel"
                onClick={() => switchTab(t.id)}
                className={`-mb-px h-auto rounded-none border-b-2 px-4 py-2 text-sm ${
                  selected
                    ? "border-carix-info font-bold text-carix-info"
                    : "border-transparent font-medium text-carix-text/60 hover:text-carix-text"
                }`}
              >
                {t.label}
              </Button>
            );
          })}
        </div>

        <section
          role="tabpanel"
          id="data-panel"
          aria-labelledby={`data-tab-${tab}`}
          className="space-y-4"
        >
          {tab === "monthly" && (
            <div className="flex flex-wrap items-end gap-3">
              <label className={FIELD}>
                직무별 표본 원천
                <select
                  aria-label="직무별 표본 원천"
                  value={sampleSource}
                  onChange={(e) => update({ sampleSource: e.target.value })}
                  className={CONTROL}
                >
                  <option value="JOBKOREA">잡코리아</option>
                  <option value="WANTED">원티드</option>
                </select>
              </label>
              {periodFilter}
            </div>
          )}
          {tab === "cpi" && (
            <div className="flex flex-wrap items-end gap-3">{periodFilter}</div>
          )}
          {tab === "postings" && (
            <PostingsFilters
              params={params}
              onApply={update}
              onReset={() => setParams(tabSwitchParams("postings", params))}
            />
          )}

          {failed && (
            <EmptyState
              variant="plain"
              tone="danger"
              icon="⚠️"
              title="데이터를 불러오지 못했습니다"
              description="잠시 후 다시 시도해 주세요."
            >
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => reload()}
              >
                다시 시도
              </Button>
            </EmptyState>
          )}
          {tab === "postings" && month && !isMonthValid && (
            <p role="alert" className="text-xs text-carix-text/60">
              월은 YYYYMM 형식입니다
            </p>
          )}
          {isLoading && <LoadingNotice />}
          {loaded?.kind === "monthly" && tab === "monthly" && (
            <MonthlyOverviewTable
              data={loaded.data}
              sampleSource={sampleSource}
            />
          )}
          {loaded?.kind === "postings" && tab === "postings" && (
            <PostingsExplorer
              data={loaded.data}
              onPage={(p) => update({ page: String(p) })}
            />
          )}
          {loaded?.kind === "cpi" && tab === "cpi" && (
            <CpiView data={loaded.data} />
          )}
          {loaded?.kind === "wage" && tab === "wage" && (
            <WageView data={loaded.data} />
          )}
        </section>
      </PageFrame>
      <AdminKeyModal
        isOpen={isKeyModalOpen}
        onClose={() => setIsKeyModalOpen(false)}
        onKeyUpdated={() => {
          reload();
          setHealthKey((k) => k + 1);
        }}
      />
    </div>
  );
}

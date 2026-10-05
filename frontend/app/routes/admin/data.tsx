import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { ThemeToggle } from "../../components/ThemeToggle";
import { AdminHeader } from "../../components/admin/AdminHeader";
import { AdminKeyModal } from "../../components/admin/AdminKeyModal";
import { MonthlyOverviewTable } from "../../components/admin/explore/MonthlyOverviewTable";
import { PostingsExplorer } from "../../components/admin/explore/PostingsExplorer";
import { PurchasingPowerPanel } from "../../components/admin/explore/PurchasingPowerPanel";
import { Button } from "@/components/ui/button";
import {
  fetchMonthlyOverview,
  fetchPostings,
  type MonthlyOverview,
  type PostingPage,
} from "../../lib/collectorApi";
import { parseTab, TABS } from "../../lib/dataExplore";

export function meta() {
  return [{ title: "수집 데이터 | Carix 관리자" }];
}

type Loaded =
  | { kind: "monthly"; data: MonthlyOverview }
  | { kind: "postings"; data: PostingPage };

export default function DataExplorePage() {
  const [params, setParams] = useSearchParams();
  const tab = parseTab(params.get("tab"));
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [failed, setFailed] = useState(false);
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const month = params.get("month") ?? "";
  const sampledParam = params.get("sampled");
  const sampled =
    sampledParam === "true"
      ? true
      : sampledParam === "false"
        ? false
        : undefined;
  const page = Math.max(0, Math.trunc(Number(params.get("page") ?? "0") || 0));

  const from = params.get("from") ?? undefined;
  const to = params.get("to") ?? undefined;
  const isMonthValid = /^\d{6}$/.test(month);

  const paramsKey = params.toString();

  useEffect(() => {
    let cancelled = false;
    const q = new URLSearchParams(paramsKey);
    const run = async () => {
      setFailed(false);
      setLoaded(null);
      try {
        let next: Loaded | null = null;
        if (tab === "monthly") {
          next = {
            kind: "monthly",
            data: await fetchMonthlyOverview(from, to),
          };
        } else if (tab === "postings" && month && isMonthValid) {
          next = {
            kind: "postings",
            data: await fetchPostings({
              month,
              sampled,
              source: q.get("source") ?? undefined,
              family: q.get("family") ?? undefined,
              company: q.get("company") ?? undefined,
              skill: q.get("skill") ?? undefined,
              page,
            }),
          };
        }
        if (!cancelled) setLoaded(next);
      } catch (err) {
        if (cancelled) return;
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
  }, [tab, month, isMonthValid, from, to, sampled, page, paramsKey, reloadKey]);

  const reload = () => setReloadKey((k) => k + 1);

  const update = (next: Record<string, string | undefined>) => {
    const sp = new URLSearchParams(params);
    Object.entries(next).forEach(([k, v]) => (v ? sp.set(k, v) : sp.delete(k)));
    setParams(sp);
  };

  return (
    <div className="min-h-screen bg-carix-bg text-carix-text">
      <ThemeToggle />
      <AdminHeader
        active="data"
        onOpenKeyModal={() => setIsKeyModalOpen(true)}
      />
      <main className="mx-auto max-w-7xl px-6 py-8 space-y-4">
        <h1 className="text-lg font-bold">수집 데이터</h1>
        <div role="tablist" className="flex gap-2">
          {TABS.map((t) => (
            <Button
              key={t.id}
              role="tab"
              aria-selected={tab === t.id}
              variant={tab === t.id ? "default" : "outline"}
              size="sm"
              onClick={() => setParams(new URLSearchParams({ tab: t.id }))}
            >
              {t.label}
            </Button>
          ))}
        </div>

        {tab === "postings" && (
          <form
            key={paramsKey}
            className="flex flex-wrap gap-2 text-xs"
            onSubmit={(e) => {
              e.preventDefault();
              const fd = new FormData(e.currentTarget);
              update({
                month: String(fd.get("month") ?? ""),
                source: String(fd.get("source") ?? ""),
                sampled: String(fd.get("sampled") ?? ""),
                family: String(fd.get("family") ?? ""),
                company: String(fd.get("company") ?? ""),
                skill: String(fd.get("skill") ?? ""),
                page: undefined,
              });
            }}
          >
            <input
              name="month"
              defaultValue={month}
              placeholder="YYYYMM"
              aria-label="월"
              className="rounded border border-carix-border px-2 py-1"
            />
            <select
              name="source"
              defaultValue={params.get("source") ?? ""}
              aria-label="원천"
              className="rounded border border-carix-border px-2 py-1"
            >
              <option value="">전체 원천</option>
              <option value="JOBKOREA">잡코리아</option>
              <option value="SARAMIN">사람인</option>
            </select>
            <select
              name="sampled"
              defaultValue={sampledParam ?? ""}
              aria-label="표본 여부"
              className="rounded border border-carix-border px-2 py-1"
            >
              <option value="">표본·정기 모두</option>
              <option value="true">표본</option>
              <option value="false">정기 수집</option>
            </select>
            <select
              name="family"
              defaultValue={params.get("family") ?? ""}
              aria-label="직무"
              className="rounded border border-carix-border px-2 py-1"
            >
              <option value="">전체 직무</option>
              <option value="DEVELOPER">DEVELOPER</option>
              <option value="DATA">DATA</option>
              <option value="AI">AI</option>
              <option value="OTHER">OTHER</option>
            </select>
            <input
              name="company"
              defaultValue={params.get("company") ?? ""}
              placeholder="회사"
              aria-label="회사"
              className="rounded border border-carix-border px-2 py-1"
            />
            <input
              name="skill"
              defaultValue={params.get("skill") ?? ""}
              placeholder="스킬(정확히)"
              aria-label="스킬"
              className="rounded border border-carix-border px-2 py-1"
            />
            <Button type="submit" size="sm">
              조회
            </Button>
          </form>
        )}

        {failed && (
          <div
            role="alert"
            className="rounded-lg border border-carix-border p-4 text-xs space-y-2"
          >
            <p>불러오지 못했습니다</p>
            <Button variant="link" size="sm" onClick={() => reload()}>
              다시 시도
            </Button>
          </div>
        )}

        {tab === "postings" && !month && (
          <p className="text-xs text-carix-text/60">월을 입력하세요</p>
        )}
        {tab === "postings" && month && !isMonthValid && (
          <p role="alert" className="text-xs text-carix-text/60">
            월은 YYYYMM 형식입니다
          </p>
        )}
        {loaded?.kind === "monthly" && tab === "monthly" && (
          <MonthlyOverviewTable data={loaded.data} />
        )}
        {loaded?.kind === "postings" && tab === "postings" && (
          <PostingsExplorer
            data={loaded.data}
            onPage={(p) => update({ page: String(p) })}
          />
        )}
        {(tab === "cpi" || tab === "wage") && (
          <PurchasingPowerPanel
            tab={tab}
            from={from}
            to={to}
            reloadKey={reloadKey}
            onForbidden={() => setIsKeyModalOpen(true)}
          />
        )}
      </main>
      <AdminKeyModal
        isOpen={isKeyModalOpen}
        onClose={() => setIsKeyModalOpen(false)}
        onKeyUpdated={() => reload()}
      />
    </div>
  );
}

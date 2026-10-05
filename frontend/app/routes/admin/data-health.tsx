import { useCallback, useEffect, useState } from "react";
import { ThemeToggle } from "../../components/ThemeToggle";
import { AdminHeader } from "../../components/admin/AdminHeader";
import { AdminKeyModal } from "../../components/admin/AdminKeyModal";
import { DataHealthGroupCard } from "../../components/admin/DataHealthGroupCard";
import { DataHealthSummary } from "../../components/admin/DataHealthSummary";
import { fetchDataHealth, type DataHealthReport } from "../../lib/collectorApi";
import { Button } from "../../components/ui/button";

export function meta() {
  return [{ title: "데이터 상태 | Carix 관리자" }];
}

export default function DataHealthPage() {
  const [report, setReport] = useState<DataHealthReport | null>(null);
  const [failed, setFailed] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false);

  const load = useCallback(async () => {
    setIsLoading(true);
    setFailed(false);
    try {
      setReport(await fetchDataHealth());
    } catch (err) {
      if ((err as { status?: number }).status === 403) {
        setIsKeyModalOpen(true);
        return;
      }
      setFailed(true);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void load();
  }, [load]);

  return (
    <div className="min-h-screen bg-carix-bg text-carix-text">
      <ThemeToggle />
      <AdminHeader
        active="data-health"
        onOpenKeyModal={() => setIsKeyModalOpen(true)}
      />
      <main className="mx-auto max-w-7xl px-6 py-8 space-y-6">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-bold">데이터 상태</h1>
          <Button
            type="button"
            onClick={() => void load()}
            disabled={isLoading}
            variant="outline"
            size="sm"
          >
            {isLoading ? "점검 중…" : "다시 점검"}
          </Button>
        </div>
        {failed && (
          <div
            role="alert"
            className="rounded-lg border border-carix-border p-4 text-xs space-y-2"
          >
            <p>
              {report
                ? "상태를 불러오지 못했습니다 — 아래는 이전 점검 결과입니다"
                : "상태를 불러오지 못했습니다"}
            </p>
            <Button
              type="button"
              onClick={() => void load()}
              variant="link"
              size="xs"
            >
              다시 시도
            </Button>
          </div>
        )}
        {report && (
          <>
            <DataHealthSummary report={report} />
            <div className="grid gap-4 md:grid-cols-2">
              {report.groups.map((g) => (
                <DataHealthGroupCard key={g.id} group={g} />
              ))}
            </div>
          </>
        )}
      </main>
      <AdminKeyModal
        isOpen={isKeyModalOpen}
        onClose={() => setIsKeyModalOpen(false)}
        onKeyUpdated={() => void load()}
      />
    </div>
  );
}

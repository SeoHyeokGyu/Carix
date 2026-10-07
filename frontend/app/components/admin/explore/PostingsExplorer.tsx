import { useState } from "react";
import type { PostingItem, PostingPage } from "../../../lib/collectorApi";
import {
  formatCollectedDate,
  formatSalaryCell,
} from "../../../lib/dataExplore";
import { Button } from "@/components/ui/button";

const COLLECTED_VIA_LABEL: Record<string, string> = {
  JOBKOREA_LIST: "잡코리아 목록",
  JOBKOREA_BACKFILL: "잡코리아 표본",
  JOBKOREA_SEED: "잡코리아 시드",
  SARAMIN_SEARCH: "사람인 검색",
  JUMPIT: "점핏",
};

function formatCollectedVia(p: PostingItem): string {
  if (!p.collectedVia) return "-";
  return p.collectedVia
    .split(",")
    .map((v) => COLLECTED_VIA_LABEL[v] ?? v)
    .join(" · ");
}

function experience(p: PostingItem): string {
  if (p.experienceMin == null && p.experienceMax == null) return "무관/미상";
  return `${p.experienceMin ?? 0}~${p.experienceMax ?? ""}년`;
}

function Row({ p }: { p: PostingItem }) {
  const [open, setOpen] = useState(false);
  const cell = formatSalaryCell(p);
  return (
    <>
      <tr className="border-t border-carix-border">
        <td className="px-2 py-1">{p.source}</td>
        <td className="px-2 py-1">
          <Button
            variant="link"
            size="sm"
            aria-expanded={open}
            aria-label={`${p.title} 상세`}
            onClick={() => setOpen((o) => !o)}
          >
            {p.title}
          </Button>
        </td>
        <td className="px-2 py-1">{p.companyName}</td>
        <td className="px-2 py-1">{p.jobFamily}</td>
        <td className="px-2 py-1">{experience(p)}</td>
        <td className={`px-2 py-1 ${cell.muted ? "text-carix-text/50" : ""}`}>
          {cell.text}
        </td>
        <td className="px-2 py-1">{p.postedStartDate ?? "-"}</td>
        <td className="px-2 py-1">{p.sampledMonth ?? "-"}</td>
      </tr>
      {open && (
        <tr>
          <td colSpan={8} className="px-4 py-2 text-[11px] text-carix-text/80">
            <p>스킬: {p.skills.length > 0 ? p.skills.join(", ") : "없음"}</p>
            <p>
              고용형태 {p.employmentType ?? "-"} · 지역 {p.region ?? "-"} · 마감{" "}
              {p.postedEndDate ?? "-"} · 관측월 {p.firstSeenMonth ?? "-"} · 수집{" "}
              {formatCollectedDate(p.collectedAt)}
            </p>
            <p>
              확인 경로 {formatCollectedVia(p)} · 원천 직군 태그{" "}
              {p.sourceJobTags ?? "-"}
            </p>
            {p.url && (
              <a
                href={p.url}
                target="_blank"
                rel="noopener noreferrer"
                className="text-carix-info underline"
              >
                원천 공고
              </a>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

/** 공고 표. 필터 입력은 페이지가 URL 로 관리한다. */
export function PostingsExplorer({
  data,
  onPage,
}: {
  data: PostingPage;
  onPage: (page: number) => void;
}) {
  if (data.items.length === 0) {
    return (
      <p className="text-xs text-carix-text/60">
        해당 조건의 데이터가 없습니다
      </p>
    );
  }
  const lastPage = Math.max(0, Math.ceil(data.total / data.size) - 1);
  return (
    <div className="space-y-2">
      <p className="text-xs text-carix-text/60">총 {data.total}건</p>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="text-carix-text/60 text-left">
              <th className="px-2 py-1">원천</th>
              <th className="px-2 py-1">제목</th>
              <th className="px-2 py-1">회사</th>
              <th className="px-2 py-1">직무</th>
              <th className="px-2 py-1">경력</th>
              <th className="px-2 py-1">제시 연봉</th>
              <th className="px-2 py-1">게시일</th>
              <th className="px-2 py-1">표본월</th>
            </tr>
          </thead>
          <tbody>
            {data.items.map((p) => (
              <Row key={`${p.source}-${p.externalId}`} p={p} />
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex gap-2 text-xs">
        <Button
          variant="outline"
          size="sm"
          disabled={data.page <= 0}
          onClick={() => onPage(data.page - 1)}
        >
          이전
        </Button>
        <span>
          {data.page + 1} / {lastPage + 1}
        </span>
        <Button
          variant="outline"
          size="sm"
          disabled={data.page >= lastPage}
          onClick={() => onPage(data.page + 1)}
        >
          다음
        </Button>
      </div>
    </div>
  );
}

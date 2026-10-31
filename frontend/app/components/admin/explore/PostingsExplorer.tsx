import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

import type { PostingItem, PostingPage } from "../../../lib/collectorApi";
import {
  formatCollectedDate,
  formatSalaryCell,
} from "../../../lib/dataExplore";
import { familyLabel, sourceLabel } from "../../../lib/sourceLabel";
import { CELL, HEAD, META } from "./exploreStyles";

const COLLECTED_VIA_LABEL: Record<string, string> = {
  JOBKOREA_LIST: "잡코리아 목록",
  JOBKOREA_BACKFILL: "잡코리아 표본",
  JOBKOREA_SEED: "잡코리아 시드",
  SARAMIN_SEARCH: "사람인 검색",
  JUMPIT: "점핏",
  WANTED_LIST: "원티드 목록",
  WANTED_BACKFILL: "원티드 표본",
  PROGRAMMERS: "프로그래머스",
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
  if (p.experienceMax == null) return `${p.experienceMin}년 이상`;
  if (p.experienceMin == null) return `~${p.experienceMax}년`;
  return `${p.experienceMin}~${p.experienceMax}년`;
}

function Row({ p }: { p: PostingItem }) {
  const [open, setOpen] = useState(false);
  const cell = formatSalaryCell(p);
  return (
    <>
      <TableRow>
        <TableCell className={CELL}>{sourceLabel(p.source)}</TableCell>
        <TableCell className={CELL}>
          <Button
            variant="link"
            aria-expanded={open}
            aria-label={`${p.title} 상세`}
            onClick={() => setOpen((o) => !o)}
            className="h-auto p-0 text-left whitespace-normal text-carix-info"
          >
            {p.title}
          </Button>
        </TableCell>
        <TableCell className={CELL}>{p.companyName}</TableCell>
        <TableCell className={CELL}>
          {p.jobFamily ? familyLabel(p.jobFamily) : "-"}
        </TableCell>
        <TableCell className={CELL}>{experience(p)}</TableCell>
        <TableCell
          className={`${CELL} ${cell.muted ? "text-carix-text/50" : ""}`}
        >
          {cell.text}
        </TableCell>
        <TableCell className={`${CELL} tabular-nums`}>
          {p.postedStartDate ?? "-"}
        </TableCell>
        <TableCell className={`${CELL} tabular-nums`}>
          {p.sampledMonth ?? "-"}
        </TableCell>
      </TableRow>
      {open && (
        <TableRow className="bg-carix-bg/40">
          <TableCell
            colSpan={8}
            className="space-y-0.5 px-4 py-2 text-[11px] text-carix-text/80"
          >
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
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

function emptyMessage(data: PostingPage): string {
  if (data.month === null) return "수집된 공고가 없습니다";
  if (data.total > 0) return "해당 조건의 데이터가 없습니다";
  // 당월 표본은 다음 달 백필에서 생기므로 당월만 안내를 덧붙인다.
  return data.current
    ? "이 달에는 조건에 맞는 공고가 없습니다 — 표본은 다음 달 백필 뒤 생깁니다"
    : "이 달에는 조건에 맞는 공고가 없습니다";
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
    return <p className="text-xs text-carix-text/60">{emptyMessage(data)}</p>;
  }
  const lastPage = Math.max(0, Math.ceil(data.total / data.size) - 1);
  const pager = (place: "위" | "아래") => (
    <Pager
      label={`공고 페이지 (${place})`}
      page={data.page}
      lastPage={lastPage}
      onPage={onPage}
    />
  );
  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className={META}>
          <span>총 {data.total}건</span>
          {data.current && <Badge variant="secondary">수집 중</Badge>}
        </p>
        {pager("위")}
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead className={HEAD}>원천</TableHead>
            <TableHead className={HEAD}>제목</TableHead>
            <TableHead className={HEAD}>회사</TableHead>
            <TableHead className={HEAD}>직무</TableHead>
            <TableHead className={HEAD}>경력</TableHead>
            <TableHead className={HEAD}>제시 연봉</TableHead>
            <TableHead
              className={HEAD}
              title="원천이 밝힌 게시일 — 관측월과 다를 수 있음"
            >
              게시일
            </TableHead>
            <TableHead className={HEAD}>표본월</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {data.items.map((p) => (
            <Row key={`${p.source}-${p.externalId}`} p={p} />
          ))}
        </TableBody>
      </Table>
      <div className="flex justify-end">{pager("아래")}</div>
    </div>
  );
}

function Pager({
  label,
  page,
  lastPage,
  onPage,
}: {
  label: string;
  page: number;
  lastPage: number;
  onPage: (page: number) => void;
}) {
  return (
    <nav aria-label={label} className="flex items-center gap-2 text-xs">
      <Button
        variant="outline"
        size="sm"
        disabled={page <= 0}
        onClick={() => onPage(page - 1)}
      >
        이전
      </Button>
      <span className="tabular-nums">
        {page + 1} / {lastPage + 1}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={page >= lastPage}
        onClick={() => onPage(page + 1)}
      >
        다음
      </Button>
    </nav>
  );
}

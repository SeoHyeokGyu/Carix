import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { PostingsExplorer } from "./PostingsExplorer";
import type { PostingItem } from "../../../lib/collectorApi";

const item: PostingItem = {
  source: "SARAMIN",
  externalId: "55220505",
  url: null,
  title: "인프라보안",
  companyName: "회사",
  jobFamily: "DEVELOPER",
  experienceMin: 4,
  experienceMax: 10,
  employmentType: null,
  region: null,
  postedStartDate: null,
  postedEndDate: null,
  firstSeenMonth: "202610",
  sampledMonth: null,
  salaryMin: null,
  salaryMax: null,
  salaryBandMin: null,
  salaryBandMax: null,
  salaryDisclosure: "UNKNOWN",
  skills: [],
  collectedVia: "SARAMIN_SEARCH,JUMPIT",
  sourceJobTags: "정보보안 담당자",
  collectedAt: "2026-10-06T15:30:00Z",
};

describe("PostingsExplorer", () => {
  it("상세 펼침에 확인 경로와 원천 직군 태그를 보여 준다", async () => {
    render(
      <PostingsExplorer
        data={{ total: 1, page: 0, size: 20, items: [item] }}
        onPage={() => {}}
      />,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "인프라보안 상세" }),
    );
    expect(
      screen.getByText(/확인 경로 사람인 검색 · 점핏/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/원천 직군 태그 정보보안 담당자/),
    ).toBeInTheDocument();
  });

  it("상세 펼침에 수집 날짜를 보여 준다", async () => {
    render(
      <PostingsExplorer
        data={{ total: 1, page: 0, size: 20, items: [item] }}
        onPage={() => {}}
      />,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "인프라보안 상세" }),
    );
    expect(screen.getByText(/수집 2026-10-07/)).toBeInTheDocument();
  });

  it("확인 경로가 없으면 '-' 로 보여 준다", async () => {
    render(
      <PostingsExplorer
        data={{
          total: 1,
          page: 0,
          size: 20,
          items: [{ ...item, collectedVia: null }],
        }}
        onPage={() => {}}
      />,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "인프라보안 상세" }),
    );
    expect(screen.getByText(/확인 경로 -/)).toBeInTheDocument();
  });

  it("원티드 확인 경로를 표시명으로 보인다", async () => {
    render(
      <PostingsExplorer
        data={{
          total: 1,
          page: 0,
          size: 20,
          items: [{ ...item, collectedVia: "WANTED_LIST,WANTED_BACKFILL" }],
        }}
        onPage={() => {}}
      />,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "인프라보안 상세" }),
    );
    expect(
      screen.getByText(/확인 경로 원티드 목록 · 원티드 표본/),
    ).toBeInTheDocument();
  });

  it("원천·직무를 표시명으로 보이고 게시일 머리글에 툴팁을 단다", () => {
    render(
      <PostingsExplorer
        data={{
          total: 1,
          page: 0,
          size: 20,
          items: [{ ...item, source: "WANTED" }],
        }}
        onPage={() => {}}
      />,
    );
    expect(screen.getByRole("cell", { name: "원티드" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "개발" })).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: "게시일" }),
    ).toHaveAttribute("title", "원천이 밝힌 게시일 — 관측월과 다를 수 있음");
  });

  it("직무가 없으면 직무 칸에 -를 보인다", () => {
    render(
      <PostingsExplorer
        data={{
          total: 1,
          page: 0,
          size: 20,
          items: [{ ...item, jobFamily: null }],
        }}
        onPage={() => {}}
      />,
    );
    const headers = screen.getAllByRole("columnheader");
    const col = headers.indexOf(
      screen.getByRole("columnheader", { name: "직무" }),
    );
    const row = screen.getAllByRole("row")[1];
    expect(within(row).getAllByRole("cell")[col]).toHaveTextContent("-");
  });
});

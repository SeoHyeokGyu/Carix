import { render, screen } from "@testing-library/react";
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
});

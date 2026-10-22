import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { PostingItem } from "../../../lib/collectorApi";
import { PostingsExplorer } from "./PostingsExplorer";

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
  it.each([
    [null, null, "무관/미상"],
    [3, null, "3년 이상"],
    [null, 5, "~5년"],
    [3, 5, "3~5년"],
  ])("경력 %s~%s 는 '%s' 로 보인다", (min, max, text) => {
    render(
      <PostingsExplorer
        data={{
          month: "202607",
          current: false,
          total: 1,
          page: 0,
          size: 20,
          items: [{ ...item, experienceMin: min, experienceMax: max }],
        }}
        onPage={() => {}}
      />,
    );
    expect(screen.getByText(text)).toBeTruthy();
  });

  it("페이지 이동을 표 위·아래에 두고 어느 쪽이든 같은 페이지로 간다", async () => {
    const onPage = vi.fn();
    render(
      <PostingsExplorer
        data={{
          month: "202607",
          current: false,
          total: 45,
          page: 0,
          size: 20,
          items: [item],
        }}
        onPage={onPage}
      />,
    );
    const top = screen.getByRole("navigation", { name: "공고 페이지 (위)" });
    const bottom = screen.getByRole("navigation", {
      name: "공고 페이지 (아래)",
    });
    expect(within(top).getByText("1 / 3")).toBeInTheDocument();
    expect(within(top).getByRole("button", { name: "이전" })).toBeDisabled();
    await userEvent.click(within(top).getByRole("button", { name: "다음" }));
    await userEvent.click(within(bottom).getByRole("button", { name: "다음" }));
    expect(onPage.mock.calls).toEqual([[1], [1]]);
  });
});

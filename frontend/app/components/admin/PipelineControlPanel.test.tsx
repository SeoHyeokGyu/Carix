import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { PipelineControlPanel } from "./PipelineControlPanel";
import userEvent from "@testing-library/user-event";
import * as collectorApi from "../../lib/collectorApi";
import { cancelDialog, confirmDialog } from "../../test-utils/confirmDialog";

vi.mock("../../lib/collectorApi", () => ({
  triggerCrawl: vi.fn(),
  triggerSkillRefine: vi.fn(),
  triggerWageStat: vi.fn(),
  triggerCpi: vi.fn(),
  triggerDeduplication: vi.fn(),
  triggerMarketAggregate: vi.fn(),
  triggerSeedLoad: vi.fn(),
}));

describe("PipelineControlPanel", () => {
  const onJobTriggered = vi.fn();
  const onError = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("크롤링 탭 기본 선택 시 월별 크롤링 규모 고정 규칙(b21) 및 기본 5페이지를 렌더링한다", () => {
    render(
      <PipelineControlPanel
        isJobRunning={false}
        onJobTriggered={onJobTriggered}
        onError={onError}
      />,
    );

    expect(
      screen.getByText(/운영 규칙 \(크롤링 규모 고정 — b21\)/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/동일한 페이지 수\(기본 5페이지\)/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/수집 페이지 수 \(Max Pages\) — 기본 5 \(규모 고정\)/),
    ).toBeInTheDocument();
  });

  it("크롤링 작업 시작하기 버튼 클릭 시 triggerCrawl을 호출한다", async () => {
    vi.mocked(collectorApi.triggerCrawl).mockResolvedValue({
      jobId: "job-crawl-1",
      jobType: "CRAWL",
      status: "RUNNING",
      message: "Started",
      startedAt: new Date().toISOString(),
    });

    render(
      <PipelineControlPanel
        isJobRunning={false}
        onJobTriggered={onJobTriggered}
        onError={onError}
      />,
    );

    const user = userEvent.setup();
    const submitBtn = screen.getByRole("button", { name: /작업 시작하기/ });
    expect(submitBtn).toHaveAttribute("data-variant", "default");

    // 실행은 확인창을 거친다 — 취소하면 아무것도 부르지 않는다.
    await user.click(submitBtn);
    await cancelDialog(user);
    expect(collectorApi.triggerCrawl).not.toHaveBeenCalled();

    await user.click(submitBtn);
    await confirmDialog(user, "작업 시작");

    await waitFor(() => {
      expect(collectorApi.triggerCrawl).toHaveBeenCalledWith({
        portal: "ALL",
        maxPages: 5,
        delayMs: 500,
      });
      expect(onJobTriggered).toHaveBeenCalled();
    });
  });

  it("다른 작업이 진행 중이면 시작 버튼이 막힌다", () => {
    render(
      <PipelineControlPanel
        isJobRunning
        onJobTriggered={onJobTriggered}
        onError={onError}
      />,
    );
    expect(
      screen.getByRole("button", { name: /작업 시작하기/ }),
    ).toBeDisabled();
  });

  it("선택한 탭과 패널이 서로를 가리킨다", () => {
    render(
      <PipelineControlPanel
        isJobRunning={false}
        onJobTriggered={onJobTriggered}
        onError={onError}
      />,
    );
    const tab = screen.getByRole("tab", { selected: true });
    const panel = screen.getByRole("tabpanel");
    expect(tab).toHaveAttribute("aria-controls", panel.id);
    expect(panel).toHaveAttribute("aria-labelledby", tab.id);
    expect(panel).toHaveAccessibleName(/채용공고 크롤링/);
  });
});

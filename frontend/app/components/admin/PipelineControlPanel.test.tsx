import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import * as collectorApi from "../../lib/collectorApi";
import { cancelDialog, confirmDialog } from "../../test-utils/confirmDialog";
import { PipelineControlPanel } from "./PipelineControlPanel";

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
});

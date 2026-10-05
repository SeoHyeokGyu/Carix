import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PurchasingPowerPanel } from "./PurchasingPowerPanel";
import * as api from "../../../lib/collectorApi";

vi.mock("../../../lib/collectorApi", async (orig) => ({
  ...(await orig<typeof import("../../../lib/collectorApi")>()),
  fetchCpi: vi.fn(),
  fetchWage: vi.fn(),
}));

describe("PurchasingPowerPanel", () => {
  beforeEach(() => {
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: ["A", "B"],
      months: [{ month: "202609", values: { A: 118.32 } }],
      missing: [{ month: "202609", codes: ["B"] }],
    });
    vi.mocked(api.fetchWage).mockResolvedValue({
      basePeriod: "2025",
      families: ["DEVELOPER", "DATA"],
      brackets: ["MID"],
      cells: [
        {
          jobFamily: "DEVELOPER",
          experienceBracket: "MID",
          sources: ["KOSIS"],
        },
      ],
      missing: ["DATA/MID"],
    });
  });

  it("CPI 결측 칸을 강조한다", async () => {
    render(<PurchasingPowerPanel tab="cpi" />);
    expect(await screen.findByText("118.32")).toBeInTheDocument();
    expect(screen.getByTestId("cpi-202609-B")).toHaveAttribute(
      "data-missing",
      "true",
    );
  });

  it("임금통계는 출처와 빈 칸만 보인다", async () => {
    render(<PurchasingPowerPanel tab="wage" />);
    expect(await screen.findByText("KOSIS")).toBeInTheDocument();
    expect(screen.getByTestId("wage-DATA-MID")).toHaveAttribute(
      "data-missing",
      "true",
    );
    expect(screen.getByText("기준 연도 2025")).toBeInTheDocument();
  });

  it("데이터가 없으면 안내한다", async () => {
    vi.mocked(api.fetchWage).mockResolvedValue({
      basePeriod: null,
      families: [],
      brackets: [],
      cells: [],
      missing: [],
    });
    render(<PurchasingPowerPanel tab="wage" />);
    expect(
      await screen.findByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
  });

  it("탭이 바뀐 뒤 늦게 도착한 이전 탭 응답은 무시한다", async () => {
    let resolveCpi: (v: api.CpiTable) => void = () => {};
    vi.mocked(api.fetchCpi).mockReturnValue(
      new Promise((r) => {
        resolveCpi = r;
      }),
    );
    const { rerender } = render(<PurchasingPowerPanel tab="cpi" />);
    rerender(<PurchasingPowerPanel tab="wage" />);
    expect(await screen.findByText("KOSIS")).toBeInTheDocument();
    resolveCpi({
      codes: ["A"],
      months: [{ month: "202601", values: { A: 1 } }],
      missing: [],
    });
    await Promise.resolve();
    expect(screen.queryByTestId("cpi-202601-A")).not.toBeInTheDocument();
    expect(screen.getByText("KOSIS")).toBeInTheDocument();
  });

  it("실패하면 다시 시도를 보인다", async () => {
    vi.mocked(api.fetchCpi).mockRejectedValue(new Error("x"));
    render(<PurchasingPowerPanel tab="cpi" />);
    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });
});

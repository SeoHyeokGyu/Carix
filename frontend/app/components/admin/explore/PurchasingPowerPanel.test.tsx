import { act, render, screen, waitFor } from "@testing-library/react";
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
    vi.clearAllMocks();
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: ["A", "B"],
      months: [{ month: "202609", values: { A: 118.3 } }],
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
          values: [
            {
              wageDefinition: "ANNUAL_GROSS_MEDIAN",
              source: "KOSIS",
              statVintage: "202508",
              wageMedian: 61_234_567,
            },
            {
              wageDefinition: "ANNUAL_BASE_MEDIAN",
              source: "KOSIS",
              statVintage: "202508",
              wageMedian: 51_130_000,
            },
          ],
        },
      ],
      missing: ["DATA/MID"],
      engineBaselines: [
        {
          jobFamily: "DEVELOPER",
          experienceBracket: "MID",
          region: "ALL",
          basePeriod: "2025",
          wageDefinition: "ANNUAL_GROSS_MEDIAN",
          source: "KOSIS",
          statVintage: "202508",
          wageMedian: 61_234_567,
        },
        {
          jobFamily: "DATA",
          experienceBracket: "MID",
          region: "ALL",
          basePeriod: "2024",
          wageDefinition: "ANNUAL_GROSS_MEDIAN",
          source: "KOSIS",
          statVintage: "202408",
          wageMedian: 48_000_000,
        },
      ],
    });
  });

  it("CPI 결측 칸을 강조한다", async () => {
    render(<PurchasingPowerPanel tab="cpi" />);
    expect(await screen.findByText("118.30")).toBeInTheDocument();
    expect(screen.getByTestId("cpi-202609-B")).toHaveAttribute(
      "data-missing",
      "true",
    );
  });

  it("임금통계는 세전 총액 중위값·정액급여 중위값·출처와 빈 칸을 보인다", async () => {
    render(<PurchasingPowerPanel tab="wage" />);
    const cell = await screen.findByTestId("wage-DEVELOPER-MID");
    expect(cell).toHaveTextContent("6,123만원");
    expect(cell).toHaveTextContent("정액 5,113만원");
    expect(cell).toHaveTextContent("KOSIS 202508");
    expect(cell).toHaveAttribute("data-engine-differs", "false");
  });

  it("엔진 기준선이 최신 연도 값과 다르면 그 값을 보이고 강조한다", async () => {
    render(<PurchasingPowerPanel tab="wage" />);
    const cell = await screen.findByTestId("wage-DATA-MID");
    expect(cell).toHaveTextContent("엔진 기준선 4,800만원 (2024 KOSIS)");
    expect(cell).toHaveAttribute("data-engine-differs", "true");
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
      engineBaselines: [],
    });
    render(<PurchasingPowerPanel tab="wage" />);
    expect(
      await screen.findByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
  });

  it("늦게 도착한 이전 요청 응답이 최신 응답을 덮어쓰지 않는다", async () => {
    let resolveStale: (v: api.CpiTable) => void = () => {};
    vi.mocked(api.fetchCpi)
      .mockReturnValueOnce(
        new Promise((r) => {
          resolveStale = r;
        }),
      )
      .mockResolvedValueOnce({
        codes: ["A"],
        months: [{ month: "202609", values: { A: 118.3 } }],
        missing: [],
      });
    const { rerender } = render(<PurchasingPowerPanel tab="cpi" />);
    rerender(<PurchasingPowerPanel tab="wage" />);
    rerender(<PurchasingPowerPanel tab="cpi" />);
    expect(await screen.findByText("118.30")).toBeInTheDocument();
    await act(async () => {
      resolveStale({
        codes: ["A"],
        months: [{ month: "202601", values: { A: 1 } }],
        missing: [],
      });
    });
    expect(screen.getByText("118.30")).toBeInTheDocument();
    expect(screen.queryByTestId("cpi-202601-A")).not.toBeInTheDocument();
  });

  it("403 이면 콜백을 부르고 실패 알림은 보이지 않는다", async () => {
    vi.mocked(api.fetchCpi).mockRejectedValue({ status: 403 });
    const onForbidden = vi.fn();
    render(<PurchasingPowerPanel tab="cpi" onForbidden={onForbidden} />);
    await waitFor(() => expect(onForbidden).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("reloadKey 가 바뀌면 다시 불러온다", async () => {
    const { rerender } = render(
      <PurchasingPowerPanel tab="cpi" reloadKey={0} />,
    );
    await screen.findByText("118.30");
    rerender(<PurchasingPowerPanel tab="cpi" reloadKey={1} />);
    await waitFor(() => expect(api.fetchCpi).toHaveBeenCalledTimes(2));
  });

  it("CPI 데이터가 없으면 안내한다", async () => {
    vi.mocked(api.fetchCpi).mockResolvedValue({
      codes: [],
      months: [],
      missing: [],
    });
    render(<PurchasingPowerPanel tab="cpi" />);
    expect(
      await screen.findByText("해당 조건의 데이터가 없습니다"),
    ).toBeInTheDocument();
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

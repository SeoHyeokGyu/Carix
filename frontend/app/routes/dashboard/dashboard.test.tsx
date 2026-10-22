import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as dashboardApi from "../../lib/api/dashboard";
import * as expenseApi from "../../lib/api/expense";
import * as incomeApi from "../../lib/api/income";
import { UnauthorizedError } from "../../lib/coreApi";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import { resetSession, setSession } from "../../lib/session";
import DashboardHome, {
  clientLoader,
  FIRST_CALCULATION_MAX_POLLS,
  FIRST_CALCULATION_POLL_MS,
} from "./index";
import { clientLoader as dashboardLayoutLoader } from "./layout";

const OK_INDEX = {
  value: 101.2,
  changeFromLastMonth: 0.4,
  status: "INCREASED",
  indexStatus: "OK" as const,
};

const EMPTY_INDEX = {
  value: null,
  changeFromLastMonth: null,
  status: null,
  indexStatus: "NOT_AVAILABLE" as const,
};

describe("대시보드 라우트 및 레이아웃 (Dashboard)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // 세션 캐시는 모듈 전역이라 앞 테스트 값을 물려받을 수 있다 — 매번 지우고 각 테스트가
    // setSession 으로 필요한 값을 명시적으로 채운다.
    resetSession();
  });

  describe("DashboardHome (지표 메인)", () => {
    beforeEach(() => {
      vi.spyOn(dashboardMarketApi, "fetchSnapshotHistory").mockResolvedValue({
        snapshots: [],
      });
      // 입력값 한 줄은 아래 전용 테스트만 본다 — 나머지 테스트에서는 조회 실패로 줄이 없다.
      vi.spyOn(incomeApi, "listIncome").mockRejectedValue(new Error("down"));
      vi.spyOn(expenseApi, "getAppliedExpenses").mockRejectedValue(
        new Error("down"),
      );
    });

    function renderScreen() {
      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          Component: DashboardHome,
          loader: clientLoader,
          HydrateFallback: () => null,
        },
        {
          path: "/dashboard/history",
          Component: () => <div>월별 이력 화면</div>,
        },
        { path: "/login", Component: () => <div>로그인 화면</div> },
      ]);
      return render(<Stub initialEntries={["/dashboard"]} />);
    }

    // 브리프 시나리오 1 — 지수 둘과 판정 히어로를 그린다.
    it("지수 둘과 판정 히어로를 그린다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: {
            value: 94.2,
            changeFromLastMonth: -1.2,
            status: "DECREASED",
            indexStatus: "OK",
          },
          marketValueIndex: {
            value: 117.5,
            changeFromLastMonth: 3.8,
            status: "INCREASED",
            indexStatus: "OK",
          },
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: {
            code: "SEEK_JOB",
            label: "이직 탐색",
            rulesApplied: ["연봉 격차 15% 이상"],
            rationale: "공식 임금통계 중앙값보다 15% 이상 낮습니다.",
          },
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("94.2")).toBeInTheDocument();
      expect(screen.getByText("117.5")).toBeInTheDocument();
      expect(screen.getByText("이직 탐색")).toBeInTheDocument();
      expect(
        screen.getByRole("link", { name: "온보딩 검토 및 재시작" }),
      ).toHaveAttribute("href", "/onboarding/review");
      expect(screen.getByTestId("verdict-statement")).toHaveTextContent(
        "연봉 격차가 크고 스킬 수요도 올라 있습니다. 적극적인 이직 탐색 구간입니다.",
      );
    });

    describe("성과급 포함 연간 실소득 한 줄", () => {
      function summaryWith(bonusInclusiveIncome?: unknown) {
        return {
          ok: true,
          data: {
            purchasingPowerIndex: OK_INDEX,
            marketValueIndex: OK_INDEX,
            openingWindowIndex: EMPTY_INDEX,
            decisionSignal: null,
            bonusInclusiveIncome,
            metadata: { baseDate: "2026-09-01", updateDelayed: false },
          },
        } as never;
      }

      it("성과급 포함 줄은 필드가 있을 때만 보인다", async () => {
        vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue(
          summaryWith({
            realChangePercent: 3.1,
            bonusCount: 2,
          }),
        );

        renderScreen();

        expect(
          await screen.findByText(
            "성과급 포함 연간 실소득 +3.1% (최근 12개월 성과급 2건 반영)",
          ),
        ).toBeInTheDocument();
      });

      it("실질 변화를 계산하지 못하면 줄이 없다", async () => {
        vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue(
          summaryWith({
            realChangePercent: null,
            bonusCount: 1,
          }),
        );

        renderScreen();

        await screen.findByText("구매력 지수");
        expect(screen.queryByText(/성과급 포함 연간 실소득/)).toBeNull();
      });

      it("음수 변화는 부호를 그대로 보인다", async () => {
        vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue(
          summaryWith({
            realChangePercent: -4.2,
            bonusCount: 0,
          }),
        );

        renderScreen();

        expect(
          await screen.findByText(
            "성과급 포함 연간 실소득 -4.2% (최근 12개월 성과급 0건 반영)",
          ),
        ).toBeInTheDocument();
      });
    });

    describe("구매력 카드 입력값 한 줄", () => {
      const SUMMARY = {
        ok: true,
        data: {
          purchasingPowerIndex: OK_INDEX,
          marketValueIndex: OK_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never;

      beforeEach(() => {
        vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue(SUMMARY);
        vi.spyOn(incomeApi, "listIncome").mockResolvedValue([
          {
            id: "a",
            grossSalary: 52_000_000,
            netSalary: 3_600_000,
            effectiveDate: "2026-03-01",
            changeReason: "INITIAL",
            selectedChangeReason: null,
            bonusIncluded: true,
            fixedBonusAnnual: null,
          },
        ]);
      });

      it("판정 기준월의 적용 소비로 연봉·생활비를 보이고 입력 관리로 보낸다", async () => {
        const applied = vi
          .spyOn(expenseApi, "getAppliedExpenses")
          .mockResolvedValue({
            baseYearMonth: "202609",
            weightVersion: 1,
            weightBasePeriod: "2025",
            amountBasis: "USER_INPUT",
            categories: [
              {
                category: "HOUSING",
                amount: 2_300_000,
                weight: 1,
                weightStatus: "USER_DEFINED",
              },
            ],
          });

        renderScreen();

        expect(
          await screen.findByText("세전 연봉 5,200만원 · 월 생활비 230만원"),
        ).toBeInTheDocument();
        expect(applied).toHaveBeenCalledWith("202609");
        expect(
          screen.getByRole("link", { name: "연봉·생활비 수정" }),
        ).toHaveAttribute("href", "/mypage/records");
      });

      it("금액 기준이 없으면 생활비를 0 이 아니라 미입력으로 보인다", async () => {
        vi.spyOn(expenseApi, "getAppliedExpenses").mockResolvedValue({
          baseYearMonth: "202609",
          weightVersion: 1,
          weightBasePeriod: "2025",
          amountBasis: "NONE",
          categories: [
            {
              category: "HOUSING",
              amount: null,
              weight: 0.2,
              weightStatus: "DEFAULT_CPI",
            },
          ],
        });

        renderScreen();

        expect(
          await screen.findByText("세전 연봉 5,200만원 · 월 생활비 미입력"),
        ).toBeInTheDocument();
      });

      it("입력값 조회가 실패하면 줄만 없고 지수는 뜬다", async () => {
        vi.spyOn(incomeApi, "listIncome").mockRejectedValue(new Error("down"));

        renderScreen();

        expect(await screen.findByText("구매력 지수")).toBeInTheDocument();
        expect(
          screen.queryByRole("link", { name: "연봉·생활비 수정" }),
        ).not.toBeInTheDocument();
      });
    });

    // 브리프 시나리오 2 — 둘 다 계산 전이면 첫 계산 안내를 띄운다.
    it("두 지수가 모두 계산 전이면 첫 계산 안내를 띄운다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(
        await screen.findByText("첫 지수를 계산하고 있습니다"),
      ).toBeInTheDocument();
    });

    describe("첫 계산 대기 중 다시 조회", () => {
      const PENDING = {
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never;
      const READY = {
        ok: true,
        data: {
          purchasingPowerIndex: OK_INDEX,
          marketValueIndex: OK_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never;

      beforeEach(() => {
        vi.useFakeTimers({ shouldAdvanceTime: true });
      });

      afterEach(() => {
        vi.useRealTimers();
      });

      it("계산이 끝나면 새로고침 없이 지표를 보여준다", async () => {
        vi.spyOn(dashboardApi, "fetchSummary")
          .mockResolvedValueOnce(PENDING)
          .mockResolvedValue(READY);

        renderScreen();
        expect(
          await screen.findByText("첫 지수를 계산하고 있습니다"),
        ).toBeInTheDocument();

        await vi.advanceTimersByTimeAsync(FIRST_CALCULATION_POLL_MS);

        await waitFor(() =>
          expect(
            screen.queryByText("첫 지수를 계산하고 있습니다"),
          ).not.toBeInTheDocument(),
        );
        expect(screen.getByText("구매력 지수")).toBeInTheDocument();
      });

      it("다시 조회해도 계산이 끝나지 않으면 멈추고 새로고침을 안내한다", async () => {
        const fetchSummary = vi
          .spyOn(dashboardApi, "fetchSummary")
          .mockResolvedValue(PENDING);

        renderScreen();
        await screen.findByText("첫 지수를 계산하고 있습니다");

        for (let i = 0; i < FIRST_CALCULATION_MAX_POLLS + 2; i++) {
          await vi.advanceTimersByTimeAsync(FIRST_CALCULATION_POLL_MS);
        }

        expect(
          await screen.findByText(/계산이 늦어지고 있습니다/),
        ).toBeInTheDocument();
        expect(fetchSummary).toHaveBeenCalledTimes(
          FIRST_CALCULATION_MAX_POLLS + 1,
        );
      });
    });

    // 브리프 시나리오 3 — 조회 실패는 예외가 아니라 재시도 UI 로 남는다.
    it("조회에 실패하면 재시도 버튼을 보여주고 화면을 비우지 않는다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: false,
        error: {
          code: "UNKNOWN",
          message: "요청을 처리하지 못했습니다.",
          status: 502,
        },
      } as never);

      renderScreen();

      expect(
        await screen.findByRole("button", { name: "다시 시도" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText("요청을 처리하지 못했습니다."),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { level: 1, name: "대시보드" }),
      ).toBeInTheDocument();
      // code·status 는 절대 화면에 노출하지 않는다.
      expect(screen.queryByText(/502/)).not.toBeInTheDocument();
      expect(screen.queryByText(/UNKNOWN/)).not.toBeInTheDocument();
      expect(screen.getByRole("alert")).toContainElement(
        screen.getByRole("button", { name: "다시 시도" }),
      );
      expect(screen.getByText(/요청을 처리하지 못했습니다/)).toHaveClass(
        "text-carix-danger",
      );
    });

    it("지표 재계산 버튼을 누르면 recalculateDashboard 를 호출하고 성공 안내를 띄운다", async () => {
      const user = userEvent.setup();
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      const recalcSpy = vi
        .spyOn(dashboardApi, "recalculateDashboard")
        .mockResolvedValue({
          ok: true,
          data: {
            recalculatedMonths: 8,
            fromYearMonth: "202601",
            summary: {
              purchasingPowerIndex: {
                value: 100.0,
                changeFromLastMonth: 0.0,
                status: "SAME",
                indexStatus: "OK",
              },
              marketValueIndex: EMPTY_INDEX,
              openingWindowIndex: EMPTY_INDEX,
              decisionSignal: null,
              metadata: { baseDate: "2026-09-01", updateDelayed: false },
            },
          },
        } as never);

      renderScreen();

      const button = await screen.findByRole("button", { name: "지표 재계산" });
      expect(button).toBeInTheDocument();

      await user.click(button);

      expect(recalcSpy).toHaveBeenCalledTimes(1);
      expect(
        await screen.findByText("8개월치 지수를 다시 계산했습니다."),
      ).toBeInTheDocument();
      expect(screen.getByText("100.0")).toBeInTheDocument();
    });

    it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockRejectedValue(
        new UnauthorizedError(),
      );

      renderScreen();

      expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    });

    it("채용 활발도 카드에 값과 근거 뱃지 둘을 그린다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: {
            value: 118.4,
            changeFromLastMonth: 3.1,
            status: "INCREASED",
            indexStatus: "OK",
          },
          decisionSignal: null,
          metadata: {
            baseDate: "2026-09-01",
            updateDelayed: false,
            openingWindow: {
              recentSharePercent: 24.5,
              baselineSharePercent: 20.7,
              peerSampleSize: 86,
            },
          },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("채용 활발도")).toBeInTheDocument();
      expect(screen.getByText("118.4")).toBeInTheDocument();
      expect(screen.getByText("최근 비중 24.5%")).toBeInTheDocument();
      expect(screen.getByText("이전 비중 20.7%")).toBeInTheDocument();
    });

    it("채용 활발도 표본이 부족하면 수치 대신 표본 부족을 두고 최근 비중 뱃지만 남긴다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: {
            value: null,
            changeFromLastMonth: null,
            status: null,
            indexStatus: "INSUFFICIENT_SAMPLE",
          },
          decisionSignal: null,
          metadata: {
            baseDate: "2026-09-01",
            updateDelayed: false,
            openingWindow: {
              recentSharePercent: 24.5,
              baselineSharePercent: null,
              peerSampleSize: 4,
            },
          },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("표본 부족")).toBeInTheDocument();
      expect(screen.getByText("최근 비중 24.5%")).toBeInTheDocument();
      expect(screen.queryByText(/이전 비중 .*%/)).not.toBeInTheDocument();
    });

    /** D4 회귀 — 부분 열화를 첫 계산 전으로 오판하지 않는다. */
    it("시장가치가 표본 부족이면 첫 계산 전 화면이 아니다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: {
            value: null,
            changeFromLastMonth: null,
            status: null,
            indexStatus: "INSUFFICIENT_SAMPLE",
          },
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("시장가치 지수")).toBeInTheDocument();
      expect(
        screen.queryByText("첫 지수를 계산하고 있습니다"),
      ).not.toBeInTheDocument();
    });

    const OK_DATA = {
      purchasingPowerIndex: {
        ...EMPTY_INDEX,
        value: 100,
        indexStatus: "OK" as const,
      },
      marketValueIndex: {
        ...EMPTY_INDEX,
        value: 104.6,
        indexStatus: "OK" as const,
      },
      openingWindowIndex: {
        ...EMPTY_INDEX,
        value: 103.5,
        indexStatus: "OK" as const,
      },
      decisionSignal: {
        code: "STAY",
        label: "잔류",
        rulesApplied: ["연봉이 시장 수준에 근접"],
        rationale: "공식 임금통계 중앙값과 비슷한 수준입니다.",
      },
      metadata: {
        baseDate: "2026-08-01",
        updateDelayed: false,
        peerGroup: {
          fallbackLevel: 1,
          appliedConditions: "서울 전체 / MID (4~7년) / LARGE / FULL_TIME",
        },
      },
    };

    it("이력 조회가 실패해도 지수와 판정과 비교 조건이 뜬다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      vi.spyOn(dashboardMarketApi, "fetchSnapshotHistory").mockRejectedValue(
        new Error("500"),
      );
      renderScreen();

      expect(await screen.findByText("104.6")).toBeInTheDocument();
      expect(screen.getByTestId("verdict-statement")).toBeInTheDocument();
      expect(
        screen.getByText("서울 전체 / 미들 (4~7년) / 대기업 / 정규직"),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("조건을 넓혀 비교했습니다"),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByText("이 판정이 이어진 기간"),
      ).not.toBeInTheDocument();
    });

    it("이력 조회가 401 이면 로그인 화면으로 보낸다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      vi.spyOn(dashboardMarketApi, "fetchSnapshotHistory").mockRejectedValue(
        new UnauthorizedError(),
      );
      renderScreen();
      expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    });
  });

  describe("DashboardLayout (대시보드 공통 레이아웃)", () => {
    it("비로그인 상태면 랜딩 페이지로 보낸다", async () => {
      setSession({ status: "anonymous" });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>대시보드</div>,
        },
        { path: "/", Component: () => <div>랜딩 페이지</div> },
      ]);

      render(<Stub initialEntries={["/dashboard"]} />);

      expect(await screen.findByText("랜딩 페이지")).toBeInTheDocument();
    });

    it("온보딩 미완료 사용자면 남은 단계 화면으로 보낸다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "PROFILE",
      });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: () => <div>대시보드</div>,
        },
        {
          path: "/onboarding/profile",
          Component: () => <div>프로필 온보딩으로 리디렉트됨</div>,
        },
      ]);

      render(<Stub initialEntries={["/dashboard"]} />);

      expect(
        await screen.findByText("프로필 온보딩으로 리디렉트됨"),
      ).toBeInTheDocument();
    });
  });
});

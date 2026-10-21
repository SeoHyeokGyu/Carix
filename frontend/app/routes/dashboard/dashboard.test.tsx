import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub, MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import * as dashboardApi from "../../lib/api/dashboard";
import * as expenseApi from "../../lib/api/expense";
import * as incomeApi from "../../lib/api/income";
import { UnauthorizedError } from "../../lib/coreApi";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import { resetSession, setSession } from "../../lib/session";
import * as skillApi from "../../lib/skillApi";
import { expectHeadingOutline } from "../../test-utils/headingOutline";
import DashboardHistory from "./history";
import DashboardHome, {
  clientLoader,
  FIRST_CALCULATION_MAX_POLLS,
  FIRST_CALCULATION_POLL_MS,
} from "./index";
import DashboardLayout, {
  clientLoader as dashboardLayoutLoader,
} from "./layout";
import DashboardTrends from "./trends";

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

  describe("개별 화면 (Pages)", () => {
    // 두 화면은 세션 만료 시 `useNavigate` 로 로그인으로 보낸다 — 라우터 문맥이 없으면
    // 훅 자체가 던진다.
    it("DashboardHistory(월별 이력) 화면을 정상 렌더링한다", () => {
      render(
        <MemoryRouter>
          <DashboardHistory />
        </MemoryRouter>,
      );
      expect(screen.queryByText("S-11")).not.toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "지난 판정" }),
      ).toBeInTheDocument();
    });

    it("DashboardTrends(추이 분석) 화면을 정상 렌더링한다", () => {
      render(
        <MemoryRouter>
          <DashboardTrends />
        </MemoryRouter>,
      );
      expect(screen.queryByText("S-14")).not.toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "추이 분석" }),
      ).toBeInTheDocument();
    });
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

    // 판별력 보강 — "둘 다 null" 만으로는 조건을 못 가른다. 하나만 NOT_AVAILABLE 이고
    // 다른 하나는 다른 열화(표본 부족)면 "첫 계산 전" 이 아니라 각 카드 뱃지로 갈라져야 한다.
    // indexStatus 조건을 빼고 value 만 보면 이 테스트가 깨진다(뮤테이션으로 직접 확인함).
    it("한 지수만 계산 전이면 첫 계산 안내 대신 각 카드의 상태 뱃지를 보여준다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: {
            ...EMPTY_INDEX,
            indexStatus: "INSUFFICIENT_SAMPLE",
          },
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("표본 부족")).toBeInTheDocument();
      // OWI 카드도 스텁상 NOT_AVAILABLE 이라 "계산 대기" 가 PPI·OWI 두 카드에 겹친다 —
      // 이 테스트의 주장은 PPI 카드 하나에 대한 것이므로 그 카드로 좁혀 확인한다.
      const ppiCard = screen
        .getByText("구매력 지수")
        .closest('[data-slot="card"]') as HTMLElement;
      expect(within(ppiCard).getByText("계산 대기")).toBeInTheDocument();
      expect(
        screen.queryByText("첫 지수를 계산하고 있습니다"),
      ).not.toBeInTheDocument();
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

    // "다시 시도" 는 실패 상태의 어포던스다 — 정상 조회에는 뜨지 않는다. 재시도가
    // 성공하면 오류·버튼 모두 사라지고 새 데이터로 바뀐다. "마지막 성공 데이터 유지"
    // 자체(재조회가 실패했을 때 이전 성공 데이터가 남는지)는 이 화면에서 클릭만으로
    // 재현할 트리거가 없다(성공하면 버튼이 사라진다) — 컴포넌트 단위로
    // `index.retention.test.tsx` 에서 따로 검증한다.
    it("재시도가 성공하면 오류 배너와 재시도 버튼이 사라지고 새 데이터로 바뀐다", async () => {
      const user = userEvent.setup();
      const spy = vi.spyOn(dashboardApi, "fetchSummary");
      spy.mockResolvedValueOnce({
        ok: false,
        error: {
          code: "UNKNOWN",
          message: "요청을 처리하지 못했습니다.",
          status: 502,
        },
      } as never);

      renderScreen();
      expect(
        await screen.findByText("요청을 처리하지 못했습니다."),
      ).toBeInTheDocument();
      expect(screen.queryByText("94.2")).not.toBeInTheDocument();

      spy.mockResolvedValueOnce({
        ok: true,
        data: {
          purchasingPowerIndex: {
            value: 94.2,
            changeFromLastMonth: -1.2,
            status: "DECREASED",
            indexStatus: "OK",
          },
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);
      await user.click(screen.getByRole("button", { name: "다시 시도" }));

      expect(await screen.findByText("94.2")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "다시 시도" }),
      ).not.toBeInTheDocument();
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

    // profile.tsx 의 isSubmitting 가드(FIX 7)와 같은 부류 — 진행 중인 재조회 위에
    // 클릭이 새 GET 을 또 쌓지 않는지, 응답이 오기 전 시점을 직접 붙잡아 확인한다.
    it("재조회 중에는 다시 시도 버튼이 비활성화된다", async () => {
      const user = userEvent.setup();
      const spy = vi.spyOn(dashboardApi, "fetchSummary");
      spy.mockResolvedValueOnce({
        ok: false,
        error: {
          code: "UNKNOWN",
          message: "요청을 처리하지 못했습니다.",
          status: 502,
        },
      } as never);

      renderScreen();
      const retryButton = await screen.findByRole("button", {
        name: "다시 시도",
      });
      expect(retryButton).not.toBeDisabled();

      let resolveRetry!: (
        value: Awaited<ReturnType<typeof dashboardApi.fetchSummary>>,
      ) => void;
      spy.mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveRetry = resolve;
          }),
      );

      await user.click(retryButton);
      expect(retryButton).toBeDisabled();

      resolveRetry({
        ok: false,
        error: {
          code: "UNKNOWN",
          message: "요청을 처리하지 못했습니다.",
          status: 502,
        },
      });

      await waitFor(() => expect(retryButton).not.toBeDisabled());
    });

    // 브리프 시나리오 4 — 근거 층에 적용 규칙과 넓힌 비교 조건이 보인다.
    it("근거 층에 적용 규칙과 넓힌 비교 조건이 보인다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: { ...EMPTY_INDEX, value: 117.5, indexStatus: "OK" },
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: {
            code: "SEEK_JOB",
            label: "이직 탐색",
            rulesApplied: ["연봉 격차 15% 이상"],
            rationale: "공식 임금통계 중앙값보다 15% 이상 낮습니다.",
          },
          metadata: {
            baseDate: "2026-09-01",
            updateDelayed: false,
            peerGroup: {
              fallbackLevel: 3,
              appliedConditions: "서울 전체 / 경력 MID / 회사규모 무관",
            },
          },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("연봉 격차 15% 이상")).toBeInTheDocument();
      expect(
        screen.getByText("서울 전체 / 경력 미들 / 회사규모 무관"),
      ).toBeInTheDocument();
      expect(screen.getByText("조건을 넓혀 비교했습니다")).toBeInTheDocument();
    });

    // 기능 명세 US-D1 두 번째 bullet — 근거에는 규칙 버전도 함께 노출해야 한다.
    it("신뢰 층에 규칙 버전이 보인다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: {
            ...EMPTY_INDEX,
            value: 94.2,
            indexStatus: "OK",
          },
          marketValueIndex: EMPTY_INDEX,
          openingWindowIndex: EMPTY_INDEX,
          decisionSignal: {
            code: "STAY",
            label: "잔류",
            rulesApplied: ["연봉 격차 5% 미만"],
            rationale: "공식 임금통계 중앙값과 비슷한 수준입니다.",
          },
          metadata: {
            baseDate: "2026-09-01",
            updateDelayed: false,
            ruleVersion: "1.2.0",
          },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText(/1\.2\.0/)).toBeInTheDocument();
    });

    // 도움말은 상단 메뉴가, 지난 판정(S-11)은 판정 히어로·시장가치 카드의 링크가 갖는다. 화면 안 바로가기는 상단에 없는
    // 온보딩 검토 하나만 남긴다.
    it("시장가치 추세는 판정 히어로가 아니라 시장가치 카드 안에 있다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: OK_INDEX,
          marketValueIndex: OK_INDEX,
          openingWindowIndex: OK_INDEX,
          // 판정이 있어야 히어로(VerdictPreview)가 그려진다 — 없으면 아래 단언이 공짜로 참이 된다.
          decisionSignal: {
            code: "STAY",
            label: "현 상태 유지",
            rulesApplied: ["연봉이 시장 수준에 근접"],
            rationale: null,
          },
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);
      vi.spyOn(dashboardMarketApi, "fetchSnapshotHistory").mockResolvedValue({
        snapshots: [
          {
            baseYearMonth: "202609",
            purchasingPowerIndex: null,
            marketValueIndex: 104.6,
            decisionSignal: "STAY",
            label: "",
            dataVersion: "",
            ruleVersion: "",
            rationale: null,
            recorded: null,
            changed: false,
          },
          {
            baseYearMonth: "202608",
            purchasingPowerIndex: null,
            marketValueIndex: 100,
            decisionSignal: "STAY",
            label: "",
            dataVersion: "",
            ruleVersion: "",
            rationale: null,
            recorded: null,
            changed: false,
          },
        ],
      });

      renderScreen();

      const card = (
        await screen.findByRole("heading", { name: "시장가치 지수" })
      ).closest("[data-slot='card']") as HTMLElement;
      expect(within(card).getByText("100.0 → 104.6")).toBeInTheDocument();
      expect(within(card).getByTestId("sparkline")).toBeInTheDocument();
      expect(screen.getAllByTestId("sparkline")).toHaveLength(1);
      // 히어로는 그려졌고(판정 지속 기간), 그래프는 거기 없다
      expect(screen.getByText("2개월째")).toBeInTheDocument();
    });

    it("구매력 카드는 추이 분석 탭으로, 시장가치 카드는 스킬 분석으로 링크하고 채용 활발도 카드는 링크하지 않는다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: OK_INDEX,
          marketValueIndex: OK_INDEX,
          openingWindowIndex: OK_INDEX,
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(
        await screen.findByRole("link", { name: "구매력 지수 자세히 보기" }),
      ).toHaveAttribute("href", "/dashboard/trends?tab=income");
      expect(
        screen.getByRole("link", { name: "시장가치 지수 자세히 보기" }),
      ).toHaveAttribute("href", "/dashboard/skills");
      // 추이 분석에 채용 활발도 탭이 없다 — 지난 판정 표로 보내면 「자세히」가 아니다.
      expect(
        screen.queryByRole("link", { name: "채용 활발도 자세히 보기" }),
      ).not.toBeInTheDocument();
    });

    it("온보딩 검토 링크는 제목 줄에 있고 별도 바로가기 줄은 없다", async () => {
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
        await screen.findByRole("link", { name: "온보딩 검토 및 재시작" }),
      ).toHaveAttribute("href", "/onboarding/review");
      expect(
        screen.queryByRole("navigation", { name: "대시보드 바로가기" }),
      ).toBeNull();
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

    it("지수 셋이 모두 계산 대기면 첫 계산 전 화면을 그린다", async () => {
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

    // OWI 절 자체를 조준한다 — PPI·MVI 만으로는 이미 이 케이스를 "첫 계산 전 아님"으로
    // 가르므로, OWI 절을 지워도 이 하나만으로는 안 잡힌다. OWI 를 다른 열화(표본 부족)로
    // 둬 게이트의 OWI 조건이 실제로 살아있는지(값 0/100 근사가 아니라 indexStatus 를
    // 보는지) 확인한다.
    it("채용 활발도만 표본 부족이어도 첫 계산 전 화면이 아니다", async () => {
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
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("채용 활발도")).toBeInTheDocument();
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

    it("헤딩이 h1 하나에서 시작해 단계를 건너뛰지 않는다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      renderScreen();
      await screen.findByRole("heading", {
        level: 2,
        name: "이 판정을 만든 지표",
      });

      expectHeadingOutline();
      expect(
        screen.getByRole("heading", { level: 2, name: "이 판정을 만든 지표" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", {
          level: 2,
          name: "이 판정은 이렇게 나왔습니다",
        }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { level: 3, name: "구매력 지수" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { level: 1, name: "대시보드" }),
      ).toBeInTheDocument();
      expect(
        screen.getByText("2026년 8월 기준 · 내 커리어 판정"),
      ).toBeInTheDocument();
    });

    // 프로필로 칩을 만들면 집계에 없는 지역·규모를 비교한 것처럼 보인다(서버 문구가 실제 조회 조건).
    it("비교 조건은 프로필 칩이 아니라 서버 문구 그대로다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      renderScreen();
      expect(
        await screen.findByText("서울 전체 / 미들 (4~7년) / 대기업 / 정규직"),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("list", { name: "비교 조건" }),
      ).not.toBeInTheDocument();
    });

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

    it("이력이 있으면 같은 판정이 이어진 개월 수를 보인다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      vi.spyOn(dashboardMarketApi, "fetchSnapshotHistory").mockResolvedValue({
        snapshots: ["202608", "202607"].map((baseYearMonth) => ({
          baseYearMonth,
          purchasingPowerIndex: 100,
          marketValueIndex: 104,
          decisionSignal: "STAY",
          label: "현 상태 유지",
          dataVersion: "v1",
          ruleVersion: "1.2.0",
          rationale: null,
          recorded: null,
          changed: false,
        })),
      });
      renderScreen();
      expect(await screen.findByText("2개월째")).toBeInTheDocument();
    });

    it("판정이 UPSKILL 이면 처방 블록을 로드하여 화면에 표시한다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          ...OK_DATA,
          decisionSignal: {
            code: "UPSKILL",
            label: "스킬 보강 우선",
            rulesApplied: ["MVI < 95", "PPI < 95"],
            rationale: "시장가치와 구매력이 모두 낮습니다.",
          },
        },
      } as never);
      vi.spyOn(skillApi, "fetchSkillRecommendations").mockResolvedValue({
        peerGroup: { appliedConditions: "서울 전체 / MID" },
        recommendations: [
          {
            standardName: "Kubernetes",
            category: "DEVOPS",
            demandRate: 0.245,
            growthTrend: "UP",
            coOccurrence: { withSkill: "Spring Boot", rate: 0.62 },
            reason: "보유하신 Spring Boot 공고의 62%가 함께 요구합니다.",
          },
        ],
      });

      renderScreen();

      expect(
        await screen.findByTestId("skill-prescription-block"),
      ).toBeInTheDocument();
      expect(screen.getByText("Kubernetes")).toBeInTheDocument();
      expect(screen.getByText("수요율 24.5%")).toBeInTheDocument();
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

    it("온보딩 완료 사용자인 경우 대시보드 탭 네비게이션이 포함된 AppShell을 렌더링한다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: DashboardLayout,
          children: [
            { index: true, Component: () => <div>대시보드 콘텐츠</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/dashboard"]} />);

      expect(
        await screen.findByRole("navigation", { name: "주 메뉴" }),
      ).toBeInTheDocument();
      expect(screen.getByText("대시보드 콘텐츠")).toBeInTheDocument();
    });

    it("지난 판정에서는 주 메뉴 「대시보드」가 선택된 채 보인다", async () => {
      setSession({
        status: "authenticated",
        userId: "user-1",
        email: "user@carix.kr",
        onboardingStep: "COMPLETED",
      });

      const Stub = createRoutesStub([
        {
          path: "/dashboard",
          loader: dashboardLayoutLoader,
          HydrateFallback: () => null,
          Component: DashboardLayout,
          children: [
            { path: "history", Component: () => <div>지난 판정</div> },
          ],
        },
      ]);

      render(<Stub initialEntries={["/dashboard/history"]} />);

      const nav = await screen.findByRole("navigation", { name: "주 메뉴" });
      expect(
        within(nav).getByRole("link", { name: "대시보드" }),
      ).toHaveAttribute("aria-current", "true");
    });
  });
});

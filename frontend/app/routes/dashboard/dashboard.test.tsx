import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub, MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as dashboardApi from "../../lib/api/dashboard";
import * as profileApi from "../../lib/api/profile";
import { UnauthorizedError } from "../../lib/coreApi";
import * as dashboardMarketApi from "../../lib/dashboardMarketApi";
import { resetSession, setSession } from "../../lib/session";
import * as skillApi from "../../lib/skillApi";
import DashboardHistory from "./history";
import DashboardHome, { clientLoader } from "./index";
import DashboardLayout, {
  clientLoader as dashboardLayoutLoader,
} from "./layout";
import DashboardTrends from "./trends";

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
      expect(screen.getByText("S-11")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: /월별 (스냅샷 )?이력/ }),
      ).toBeInTheDocument();
    });

    it("DashboardTrends(추이 분석) 화면을 정상 렌더링한다", () => {
      render(
        <MemoryRouter>
          <DashboardTrends />
        </MemoryRouter>,
      );
      expect(screen.getByText("S-14")).toBeInTheDocument();
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
      vi.spyOn(profileApi, "getProfile").mockResolvedValue({
        jobFamily: "DEVELOPER",
        yearsOfExperience: 5,
        region: "서울",
        employmentType: "FULL_TIME",
        companySize: "LARGE",
      });
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
      expect(
        screen.getByRole("heading", {
          level: 2,
          name: "연봉 격차가 크고 스킬 수요도 올라 있습니다. 적극적인 이직 탐색 구간입니다.",
        }),
      ).toBeInTheDocument();
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
        screen.getByRole("heading", { level: 1, name: "내 커리어 판정" }),
      ).toBeInTheDocument();
      // code·status 는 절대 화면에 노출하지 않는다.
      expect(screen.queryByText(/502/)).not.toBeInTheDocument();
      expect(screen.queryByText(/UNKNOWN/)).not.toBeInTheDocument();
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
        await screen.findByText("8개월치 지수가 최신 정보로 재계산되었습니다."),
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
        screen.getByText("서울 전체 / 경력 MID / 회사규모 무관"),
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

    // 스킬 수요율 펼치기(GET /dashboard/skills, B 소관) 대신 S-11 링크로 대체하기로 한
    // 팀 리드 지시를 배선으로 고정한다.
    it("월별 스냅샷 이력 보기 링크가 S-11 로 이동한다", async () => {
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
        await screen.findByRole("link", { name: "월별 스냅샷 이력 보기" }),
      ).toHaveAttribute("href", "/dashboard/history");
    });

    it("조회 중 401 을 받으면 로그인 화면으로 보낸다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockRejectedValue(
        new UnauthorizedError(),
      );

      renderScreen();

      expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
    });

    it("개방창 지수 카드에 값과 근거 뱃지 둘을 그린다", async () => {
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
              medianWindowDays: 34,
              openEndedRate: 21.5,
              deadlineSampleSize: 86,
            },
          },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("개방창 지수")).toBeInTheDocument();
      expect(screen.getByText("118.4")).toBeInTheDocument();
      expect(screen.getByText("중앙 34일")).toBeInTheDocument();
      expect(screen.getByText("상시 21.5%")).toBeInTheDocument();
    });

    it("개방창 표본이 부족하면 수치 대신 표본 부족을 두고 상시 뱃지만 남긴다", async () => {
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
              medianWindowDays: null,
              openEndedRate: 18.0,
              deadlineSampleSize: 4,
            },
          },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("표본 부족")).toBeInTheDocument();
      expect(screen.getByText("상시 18.0%")).toBeInTheDocument();
      expect(screen.queryByText(/중앙 .*일/)).not.toBeInTheDocument();
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
    it("개방창 지수만 표본 부족이어도 첫 계산 전 화면이 아니다", async () => {
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

      expect(await screen.findByText("개방창 지수")).toBeInTheDocument();
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
      await screen.findByRole("heading", { level: 2 });

      const levels = screen
        .getAllByRole("heading")
        .map((heading) => Number(heading.tagName.slice(1)));
      expect(levels.filter((level) => level === 1)).toHaveLength(1);
      expect(levels[0]).toBe(1);
      levels.slice(1).forEach((level, i) => {
        expect(level - levels[i]).toBeLessThanOrEqual(1);
      });
      expect(
        screen.getByRole("heading", {
          level: 1,
          name: "2026년 8월 기준 · 내 커리어 판정",
        }),
      ).toBeInTheDocument();
    });

    it("완화가 없으면 비교 조건을 프로필 한글 칩으로 보인다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      renderScreen();
      const chips = await screen.findByRole("list", { name: "비교 조건" });
      expect(within(chips).getByText("대기업")).toBeInTheDocument();
      expect(screen.queryByText(/FULL_TIME/)).not.toBeInTheDocument();
    });

    it("이력·프로필 조회가 실패해도 지수와 판정은 뜨고 비교 조건은 서버 문자열로 물러난다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: OK_DATA,
      } as never);
      vi.spyOn(dashboardMarketApi, "fetchSnapshotHistory").mockRejectedValue(
        new Error("500"),
      );
      vi.spyOn(profileApi, "getProfile").mockRejectedValue(new Error("500"));
      renderScreen();

      expect(await screen.findByText("104.6")).toBeInTheDocument();
      expect(screen.getByRole("heading", { level: 2 })).toBeInTheDocument();
      expect(
        screen.getByText("서울 전체 / MID (4~7년) / LARGE / FULL_TIME"),
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
        await screen.findByRole("navigation", { name: "대시보드" }),
      ).toBeInTheDocument();
      expect(screen.getByText("대시보드 콘텐츠")).toBeInTheDocument();
    });
  });
});

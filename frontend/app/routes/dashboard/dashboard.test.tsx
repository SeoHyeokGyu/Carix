import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createRoutesStub } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as dashboardApi from "../../lib/api/dashboard";
import { UnauthorizedError } from "../../lib/coreApi";
import { resetSession, setSession } from "../../lib/session";
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
    it("DashboardHistory(월별 이력) 화면을 정상 렌더링한다", () => {
      render(<DashboardHistory />);
      expect(screen.getByText("S-11")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: /월별 (스냅샷 )?이력/ }),
      ).toBeInTheDocument();
    });

    it("DashboardTrends(추이 분석) 화면을 정상 렌더링한다", () => {
      render(<DashboardTrends />);
      expect(screen.getByText("S-14")).toBeInTheDocument();
      expect(
        screen.getByRole("heading", { name: "추이 분석" }),
      ).toBeInTheDocument();
    });
  });

  describe("DashboardHome (지표 메인)", () => {
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

    // 브리프 시나리오 1 — 지수 둘과 결정 신호 카드를 그린다.
    it("지수 둘과 결정 신호 카드를 그린다", async () => {
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
    });

    // 브리프 시나리오 2 — 둘 다 계산 전이면 첫 계산 안내를 띄운다.
    it("두 지수가 모두 계산 전이면 첫 계산 안내를 띄운다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
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
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);

      renderScreen();

      expect(await screen.findByText("표본 부족")).toBeInTheDocument();
      expect(screen.getByText("계산 대기")).toBeInTheDocument();
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
        screen.getByRole("heading", { name: "핵심 지표" }),
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
          decisionSignal: null,
          metadata: { baseDate: "2026-09-01", updateDelayed: false },
        },
      } as never);
      await user.click(screen.getByRole("button", { name: "다시 시도" }));

      expect(await screen.findByText("94.2")).toBeInTheDocument();
      expect(
        screen.queryByText("요청을 처리하지 못했습니다."),
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "다시 시도" }),
      ).not.toBeInTheDocument();
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

    // 브리프 시나리오 4 — 근거를 펼치면 적용 규칙과 동료집단 조건이 보인다.
    it("근거를 펼치면 적용 규칙과 동료집단 조건이 보인다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: { ...EMPTY_INDEX, value: 117.5, indexStatus: "OK" },
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
      await userEvent
        .setup()
        .click(await screen.findByRole("button", { name: "근거 보기" }));

      expect(screen.getByText("연봉 격차 15% 이상")).toBeInTheDocument();
      expect(
        screen.getByText("서울 전체 / 경력 MID / 회사규모 무관"),
      ).toBeInTheDocument();
    });

    // 기능 명세 US-D1 두 번째 bullet — 근거에는 규칙 버전도 함께 노출해야 한다.
    it("근거를 펼치면 규칙 버전도 함께 보인다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: {
            ...EMPTY_INDEX,
            value: 94.2,
            indexStatus: "OK",
          },
          marketValueIndex: EMPTY_INDEX,
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
      await userEvent
        .setup()
        .click(await screen.findByRole("button", { name: "근거 보기" }));

      expect(screen.getByText(/1\.2\.0/)).toBeInTheDocument();
    });

    // 스킬 수요율 펼치기(GET /dashboard/skills, B 소관) 대신 S-11 링크로 대체하기로 한
    // 팀 리드 지시를 배선으로 고정한다.
    it("월별 스냅샷 이력 보기 링크가 S-11 로 이동한다", async () => {
      vi.spyOn(dashboardApi, "fetchSummary").mockResolvedValue({
        ok: true,
        data: {
          purchasingPowerIndex: EMPTY_INDEX,
          marketValueIndex: EMPTY_INDEX,
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

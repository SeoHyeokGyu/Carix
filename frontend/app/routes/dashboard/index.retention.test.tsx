import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";

/**
 * "마지막 성공 데이터 유지"(설계 §10)는 성공 → (같은 마운트에서) 재조회 실패 순서로만
 * 관찰할 수 있다. 하지만 화면의 "다시 시도" 버튼은 실패 상태의 어포던스로 확정됐다(팀
 * 리드 판정 — 정상 화면에 오류 버튼을 상시 띄우지 않는다) — 성공하는 순간 버튼이
 * 사라지므로, 클릭만으로는 "성공 다음에 오는 실패"를 재현할 트리거가 없다.
 *
 * 이 화면엔 그 전환을 일으킬 다른 UI(폼·다른 버튼)도 없다 — review.tsx 가 완비 게이트
 * 재검증을, fieldErrors.test.ts 가 반복 필드 오류 병합을 화면 밖에서 검증한 것과 같은
 * 이유로(task-7 보고 "이 화면에서는 실제로 트리거할 방법이 없기 때문이다"), 같은 마운트
 * 인스턴스 위에서 `useLoaderData` 가 반환하는 값만 바꿔(RTL 의 `rerender` 는 같은
 * 컴포넌트를 그 자리에서 다시 그릴 뿐 언마운트하지 않는다) 컴포넌트 단위로 이 상태 기계
 * 자체를 검증한다. `login.test.tsx`/`root.test.tsx` 가 쓰는 `vi.mock("react-router", ...)`
 * 부분 목킹과 같은 패턴이다.
 *
 * **이 테스트가 증명하지 못하는 것.** `rerender` 는 같은 파이버를 재사용한다는 전제
 * 위에서 "재검증은 언마운트 없이 새 loaderData 를 준다"는 실제 동작을 흉내 낸다 —
 * 지금은 맞는 전제다. 하지만 나중에 이 라우트에 `key` 가 붙거나 라우트 트리가
 * 바뀌어 재검증이 실제로 리마운트를 일으키게 되면, `lastGood` 은 조용히 초기화되고
 * 실제 화면의 유지 기능은 깨지는데 이 테스트는 그 변화를 반영할 방법이 없어 계속
 * 초록불로 남는다 — `rerender` 자체가 "같은 인스턴스"를 가정하고 있기 때문이다.
 */
const mockUseLoaderData = vi.fn();
const mockRevalidate = vi.fn();

vi.mock("react-router", async () => {
  const actual = await vi.importActual("react-router");
  return {
    ...actual,
    useLoaderData: () => mockUseLoaderData(),
    useRevalidator: () => ({
      revalidate: mockRevalidate,
      state: "idle" as const,
    }),
  };
});

// vi.mock 은 파일 안의 import 보다 먼저 호이스팅되지만, 실제 컴포넌트 import 는 목 선언
// 아래에 둬 "무엇을 위해 목을 까는지"가 읽는 순서 그대로 드러나게 한다.
import DashboardHome from "./index";

const OK_SUMMARY = {
  ok: true as const,
  data: {
    purchasingPowerIndex: {
      value: 94.2,
      changeFromLastMonth: -1.2,
      status: "DECREASED",
      indexStatus: "OK" as const,
    },
    marketValueIndex: {
      value: null,
      changeFromLastMonth: null,
      status: null,
      indexStatus: "NOT_AVAILABLE" as const,
    },
    openingWindowIndex: {
      value: null,
      changeFromLastMonth: null,
      status: null,
      indexStatus: "NOT_AVAILABLE" as const,
    },
    decisionSignal: null,
    metadata: { baseDate: "2026-09-01", updateDelayed: false },
  },
};

const FAIL_SUMMARY = {
  ok: false as const,
  error: {
    code: "UNKNOWN",
    message: "요청을 처리하지 못했습니다.",
    status: 502,
  },
};

function renderWithLoaderData(
  summary: typeof OK_SUMMARY | typeof FAIL_SUMMARY,
) {
  mockUseLoaderData.mockReturnValue({ summary });
  return render(
    <MemoryRouter>
      <DashboardHome />
    </MemoryRouter>,
  );
}

describe("DashboardHome — 마지막 성공 데이터 유지 (컴포넌트 단위)", () => {
  // 실패 → 성공 → 실패 순서로 검증한다. 성공에서 시작하면 `lastGood` 의 useState
  // 초기값 자체가 이미 정답을 담고 있어, 뒤이은 갱신 로직을 지워도 우연히 통과한다
  // (실제로 그렇게 짰다가 뮤테이션을 못 잡는 것을 먼저 확인한 뒤 순서를 바꿨다) — 이
  // 순서라야 "성공 이후 갱신"이 실제로 일어나는지를 본다.
  it("성공 이후 같은 마운트에서 재조회가 실패해도 그 성공 데이터를 화면에 남긴다", () => {
    const { rerender } = renderWithLoaderData(FAIL_SUMMARY);
    expect(screen.getByText("요청을 처리하지 못했습니다.")).toBeInTheDocument();
    expect(screen.queryByText("94.2")).not.toBeInTheDocument();

    mockUseLoaderData.mockReturnValue({ summary: OK_SUMMARY });
    rerender(
      <MemoryRouter>
        <DashboardHome />
      </MemoryRouter>,
    );
    expect(screen.getByText("94.2")).toBeInTheDocument();

    mockUseLoaderData.mockReturnValue({ summary: FAIL_SUMMARY });
    rerender(
      <MemoryRouter>
        <DashboardHome />
      </MemoryRouter>,
    );

    expect(screen.getByText("요청을 처리하지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByText("94.2")).toBeInTheDocument();
  });
});

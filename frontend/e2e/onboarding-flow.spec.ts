import { expect, type Page, test } from "@playwright/test";

/**
 * 설계 §11 — 온보딩 → 대시보드 → 시뮬레이터 한 줄기의 E2E 골격. 본 점검은 7주차다.
 *
 * 상시로(백엔드 계정 없이) 도는 것은 진입 가드뿐이다 — Task 4 의 loader 가드가 실제
 * 빌드 산출물(`pnpm build && pnpm start`)에서도 동작해, 세션 없는 방문자를 전부
 * 랜딩으로 돌려보내는지 확인한다. 로그인 후 한 줄기는 계정 환경변수가 있을 때만 돈다.
 */

const PROTECTED = [
  "/onboarding/profile",
  "/onboarding/income",
  "/onboarding/expense",
  // "/onboarding/skills" (S-08) 는 다른 갈래가 아직 자리표시자만 채워둔 화면이지만,
  // 가드는 그 화면 내용이 아니라 onboarding/layout.tsx 의 공통 loader 가 건다 —
  // protected.tsx → onboarding/layout.tsx 순으로 requireSession 을 거치므로 세션
  // 없는 방문자는 Skills 컴포넌트가 그려지기도 전에 랜딩으로 튕긴다. 화면 자체의
  // 미완성과 무관하게 결정적으로 도는 검증이라 목록에 포함한다.
  "/onboarding/skills",
  "/onboarding/review",
  "/dashboard",
  "/dashboard/simulator",
];

for (const path of PROTECTED) {
  test(`세션 없이 ${path} 를 열면 랜딩으로 돌아온다`, async ({ page }) => {
    await page.goto(path);
    await expect(page).toHaveURL("/");
  });
}

/**
 * 로그인 후 한 줄기 — 프로필부터 시뮬레이터까지.
 *
 * CARIX_E2E_EMAIL·CARIX_E2E_PASSWORD 가 없으면 이 describe 전체가 스킵된다(로컬·
 * 일반 CI 기본값). 7주차 통합 점검에서 실제 계정을 이 두 환경변수로 주입하면 돈다.
 */
test.describe("로그인 후 온보딩 한 줄기", () => {
  test.skip(
    !process.env.CARIX_E2E_EMAIL,
    "CARIX_E2E_EMAIL·CARIX_E2E_PASSWORD 가 있을 때만 돈다 — 7주차 통합 점검용",
  );

  async function loginAsE2eUser(page: Page) {
    await page.goto("/login");
    await page.getByLabel("이메일").fill(process.env.CARIX_E2E_EMAIL!);
    await page.getByLabel("비밀번호").fill(process.env.CARIX_E2E_PASSWORD!);
    await page.getByRole("button", { name: "로그인" }).click();
    // click() 은 이벤트를 보내자마자 끝난다 — handleLogin 은 로그인 API 응답을 받고
    // 세션 쿠키가 심긴 뒤에야 이동한다. 여기서 기다리지 않으면 바로 이어지는
    // page.goto 가 로그인 요청과 경합해, 그 요청을 끊어버릴 수 있다.
    await expect(page).toHaveURL(/\/onboarding\/|\/dashboard/);
  }

  test("프로필부터 시뮬레이터까지 진행한다", async ({ page }) => {
    await loginAsE2eUser(page);
    await expect(page).toHaveURL(/\/onboarding\/|\/dashboard/);
  });

  /**
   * jsdom 은 레이아웃을 계산하지 않아 DOM 순서만 볼 수 있다 — profile.test.tsx 의
   * "Tab 순서가 시각 순서와 일치한다" 테스트가 그 한계 안에서 최선이다. flex-row-reverse
   * 로 시각 순서만 뒤집는 회귀(WCAG 2.4.3)는 그 단위 테스트를 통과한 채로 숨을 수
   * 있다 — 진짜 브라우저가 그린 좌표라야 잡힌다. 소득 화면의 "이전"·"다음" 을 실제
   * 화면 좌표와 포커스 이동 순서 양쪽으로 검증한다.
   *
   * 전제: CARIX_E2E 계정의 온보딩 단계가 INCOME 이상 REVIEW 이하(완료 전)여야 한다 —
   * guardOnboarding 은 COMPLETED 이후에는 review 화면만 재진입을 허용하고 income 은
   * 대시보드로 되돌린다(routeGuards.ts).
   */
  test("소득 화면의 '이전'·'다음' 이 시각 순서와 포커스 순서가 일치한다 (WCAG 2.4.3)", async ({
    page,
  }) => {
    await loginAsE2eUser(page);
    await page.goto("/onboarding/income");

    const prevLink = page.getByRole("link", { name: "이전" });
    const nextButton = page.getByRole("button", { name: "다음" });
    await expect(prevLink).toBeVisible();
    await expect(nextButton).toBeVisible();

    const prevBox = await prevLink.boundingBox();
    const nextBox = await nextButton.boundingBox();
    if (!prevBox || !nextBox) {
      throw new Error("이전/다음 버튼의 화면 좌표를 가져오지 못했습니다.");
    }
    // 시각 순서 — "이전" 이 왼쪽, "다음" 이 오른쪽에 그려져야 한다.
    expect(prevBox.x).toBeLessThan(nextBox.x);

    // 포커스 순서 — 시각 순서와 같은 방향(이전 → 다음)으로 Tab 이 움직여야 한다.
    await prevLink.focus();
    await expect(prevLink).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(nextButton).toBeFocused();
  });
});

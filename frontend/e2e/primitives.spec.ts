import { expect, test } from "@playwright/test";

/**
 * 프리미티브 시각 회귀 베이스라인.
 *
 * UI 통일 계획 P3 이 `Card`·`Button`·`Badge`·`Table` 의 기본값을 B 시각 언어로 바꾼다. 그 변경이
 * 의도한 것만 바꾸는지 여기서 diff 로 본다.
 *
 * 인증이 필요한 화면 7개는 이 그물에 들어오지 않는다 — `CARIX_E2E_EMAIL` 계정이 서는 7주차에
 * 별도로 붙인다 (`onboarding-flow.spec.ts` 참고).
 */
const SECTIONS = [
  "primitives-buttons",
  "primitives-badges",
  "primitives-cards",
  "primitives-composed",
  "primitives-table",
];

test.beforeEach(async ({ page }) => {
  await page.goto("/dev/primitives");
});

for (const section of SECTIONS) {
  test(`${section} 절이 베이스라인과 같다`, async ({ page }) => {
    // 라이트·다크 두 벌이 같은 testid 로 렌더된다.
    const blocks = page.getByTestId(section);
    await expect(blocks).toHaveCount(2);

    await expect(blocks.nth(0)).toHaveScreenshot(`${section}-light.png`);
    await expect(blocks.nth(1)).toHaveScreenshot(`${section}-dark.png`);
  });
}

test("버튼 hover 상태가 베이스라인과 같다", async ({ page }) => {
  const target = page.getByTestId("button-focus-target").first();
  await target.hover();
  await expect(target).toHaveScreenshot("button-hover.png");
});

test("버튼 focus-visible 링이 베이스라인과 같다", async ({ page }) => {
  const target = page.getByTestId("button-focus-target").first();
  await target.focus();
  await expect(target).toHaveScreenshot("button-focus.png");
});

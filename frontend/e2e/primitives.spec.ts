import { expect, test, type Page } from "@playwright/test";

/**
 * 프리미티브 시각 회귀 베이스라인.
 *
 * UI 통일 계획 P3 이 `Card`·`Button`·`Badge`·`Table` 의 기본값을 B 시각 언어로 바꾼다. 그 변경이
 * 의도한 것만 바꾸는지 여기서 diff 로 본다.
 *
 * 인증이 필요한 화면 7개는 이 그물에 들어오지 않는다 — `CARIX_E2E_EMAIL` 계정이 서는 7주차에
 * 별도로 붙인다 (`onboarding-flow.spec.ts` 참고).
 *
 * 라이트/다크는 `ThemeToggle` 이 실제로 쓰는 메커니즘 그대로 `document.documentElement` 에
 * `light`/`dark` 클래스를 넣고 뺀다 — `tokens.css` 의 `.dark`/`.light` 는 `:root` 에서만
 * `--carix-*` 를 재정의하기 때문에, 중첩된 div 에 클래스를 넣는 방식은 다크 베이스라인을 못 만든다.
 */
const SECTIONS = [
  "primitives-buttons",
  "primitives-badges",
  "primitives-cards",
  "primitives-composed",
  "primitives-table",
];

async function setTheme(page: Page, theme: "light" | "dark") {
  await page.evaluate((t) => {
    document.documentElement.classList.remove("light", "dark");
    document.documentElement.classList.add(t);
  }, theme);
}

test.beforeEach(async ({ page }) => {
  await page.goto("/dev/primitives");
});

for (const section of SECTIONS) {
  test(`${section} 절이 베이스라인과 같다`, async ({ page }) => {
    const block = page.getByTestId(section);

    await setTheme(page, "light");
    await expect(block).toHaveScreenshot(`${section}-light.png`);

    await setTheme(page, "dark");
    await expect(block).toHaveScreenshot(`${section}-dark.png`);
  });
}

test("버튼 hover 상태가 베이스라인과 같다", async ({ page }) => {
  const target = page.getByTestId("button-focus-target").first();
  await target.hover();

  await setTheme(page, "light");
  await expect(target).toHaveScreenshot("button-hover-light.png");

  await setTheme(page, "dark");
  await expect(target).toHaveScreenshot("button-hover-dark.png");
});

test("버튼 focus-visible 링이 베이스라인과 같다", async ({ page }) => {
  const target = page.getByTestId("button-focus-target").first();
  await target.focus();

  await setTheme(page, "light");
  await expect(target).toHaveScreenshot("button-focus-light.png");

  await setTheme(page, "dark");
  await expect(target).toHaveScreenshot("button-focus-dark.png");
});

for (const theme of ["light", "dark"] as const) {
  for (const section of [
    "gallery-tone-badge",
    "gallery-empty-state",
    "gallery-loading-state",
  ]) {
    test(`${section} — ${theme}`, async ({ page }) => {
      await page.goto("/dev/primitives");
      await setTheme(page, theme);
      await expect(page.getByTestId(section)).toHaveScreenshot(
        `${section}-${theme}.png`,
      );
    });
  }
}

test("톤마다 실제로 다른 색을 쓴다 — 스냅샷이 못 잡는 근접색 교체 방어", async ({
  page,
}) => {
  await page.goto("/dev/primitives");
  await setTheme(page, "light");

  const colors = await page
    .getByTestId("gallery-tone-badge")
    .locator("[data-tone]")
    .evaluateAll((nodes) =>
      nodes.map((n) => ({
        tone: n.getAttribute("data-tone"),
        color: getComputedStyle(n).color,
      })),
    );

  expect(colors).toHaveLength(6);
  const distinct = new Set(colors.map((c) => c.color));
  expect(distinct.size).toBe(6);
});

test("테마를 바꾸면 토큰 값이 실제로 바뀐다", async ({ page }) => {
  await page.goto("/dev/primitives");

  const read = () =>
    page
      .getByTestId("gallery-tone-badge")
      .locator('[data-tone="action"]')
      .evaluate((n) => getComputedStyle(n).color);

  await setTheme(page, "light");
  const light = await read();
  await setTheme(page, "dark");
  const dark = await read();

  expect(light).not.toBe(dark);
});

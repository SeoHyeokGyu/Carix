import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

const BREAKPOINTS = [320, 768, 1024, 1440];

for (const width of BREAKPOINTS) {
  test(`${width}px에서 가로 오버플로 없이 렌더링된다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/");
    const bodyWidth = await page.evaluate(
      () => document.documentElement.scrollWidth,
    );
    expect(bodyWidth).toBeLessThanOrEqual(width + 1);
    await page.screenshot({
      path: `e2e/screenshots/landing-${width}.png`,
      fullPage: true,
    });
  });
}

test("자동 접근성 검사를 통과한다", async ({ page }) => {
  await page.goto("/");
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});

test("CTA 버튼이나 링크가 없다", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("button")).toHaveCount(0);
  await expect(page.getByRole("link")).toHaveCount(0);
});

test("prefers-reduced-motion에서 crossover 라인이 애니메이션 없이 최종 상태로 표시된다", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  const svg = page.getByTestId("index-crossover-line");
  await expect(svg).toHaveAttribute("data-reduced-motion", "true");
});

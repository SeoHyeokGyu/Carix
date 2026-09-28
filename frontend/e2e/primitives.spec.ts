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
  "gallery-inputs",
  // gallery-chart-marker 는 스크린샷하지 않는다 — B 소유 차트 전체를 C 베이스라인에 묶게 된다.
  // 마커 링은 아래 computed-style 단언이 지킨다.
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

for (const theme of ["light", "dark"] as const) {
  test(`톤마다 실제로 다른 색을 쓴다 — 스냅샷이 못 잡는 근접색 교체 방어 (${theme})`, async ({
    page,
  }) => {
    await page.goto("/dev/primitives");
    await setTheme(page, theme);

    const tones = page.getByTestId("gallery-tone-badge").locator("[data-tone]");
    // evaluateAll 은 기다리지 않는다 — 클라이언트 렌더 전에 읽으면 빈 배열이 나와 아래 단언이
    // 늘 실패했다. 첫 뱃지가 붙을 때까지 기다린다.
    await expect(tones.first()).toBeAttached();
    const colors = await tones.evaluateAll((nodes) =>
      nodes.map((n) => ({
        tone: n.getAttribute("data-tone"),
        color: getComputedStyle(n).color,
      })),
    );

    // 톤 종류가 늘어도 (>= 6) 통과해야 하고, 셀렉터가 빈 배열을 돌려주는 사고로
    // 검사가 공허하게 통과하지도 않아야 한다.
    expect(colors.length).toBeGreaterThanOrEqual(6);
    const distinct = new Set(colors.map((c) => c.color));
    expect(distinct.size).toBe(colors.length);
  });
}

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

/**
 * 프리미티브가 **어느 토큰을** 쓰는지 잰다. 스냅샷은 허용오차 안의 재색상화를 못 잡고,
 * `--update-snapshots` 후 png diff 가 없다는 것도 "색 변화 없음" 의 증거가 아니다.
 *
 * 기대값은 rgb 를 박지 않는다. 같은 테마에서 탐침 요소에 같은 CSS 값을 칠해 계산값을 얻는다 —
 * 단언의 뜻이 "Button default 는 `--color-primary` 를 쓴다" 가 되어 토큰 값 조정에는 안 깨지고
 * 컴포넌트가 다른 토큰으로 옮겨 갈 때만 깨진다. P3 는 이 표를 고쳐 쓰는 것으로 무엇을
 * 바꿨는지 적는다.
 */
interface StyleExpectation {
  name: string;
  selector: string;
  property: string;
  /**
   * 탐침에 칠할 CSS 값. 컴포넌트가 쓰는 Tailwind 유틸이 풀리는 값과 같아야 한다.
   * `currentColor` 는 탐침이 아니라 대상 요소 자신의 `color` 와 비교한다.
   */
  value: string;
  themes?: readonly ("light" | "dark")[];
}

const BUTTON = (variant: string) =>
  `[data-testid="primitives-buttons"] [data-slot="button"][data-variant="${variant}"][data-size="default"]:not(:disabled)`;
const BADGE = (variant: string) =>
  `[data-testid="primitives-badges"] [data-slot="badge"][data-variant="${variant}"]`;
const COMPOSED_CARD = '[data-testid="primitives-composed"] [data-slot="card"]';
const TABLE_HEAD = '[data-testid="primitives-table"] thead';
const INPUT = '[data-testid="input-default"]';

const STYLE_EXPECTATIONS: StyleExpectation[] = [
  // Button — 색
  {
    name: "Button default 배경",
    selector: BUTTON("default"),
    property: "background-color",
    value: "var(--color-primary)",
  },
  {
    name: "Button default 글자",
    selector: BUTTON("default"),
    property: "color",
    value: "var(--color-primary-foreground)",
  },
  {
    name: "Button destructive 배경",
    selector: BUTTON("destructive"),
    property: "background-color",
    value: "var(--color-destructive)",
    themes: ["light"],
  },
  {
    name: "Button destructive 글자",
    selector: BUTTON("destructive"),
    property: "color",
    value: "white",
  },
  {
    name: "Button outline 배경",
    selector: BUTTON("outline"),
    property: "background-color",
    value: "var(--color-background)",
    themes: ["light"],
  },
  {
    name: "Button outline 테두리",
    selector: BUTTON("outline"),
    property: "border-top-color",
    value: "var(--color-border)",
    themes: ["light"],
  },
  {
    name: "Button outline 테두리(다크)",
    selector: BUTTON("outline"),
    property: "border-top-color",
    value: "var(--color-input)",
    themes: ["dark"],
  },
  {
    name: "Button secondary 배경",
    selector: BUTTON("secondary"),
    property: "background-color",
    value: "var(--color-secondary)",
  },
  {
    name: "Button secondary 글자",
    selector: BUTTON("secondary"),
    property: "color",
    value: "var(--color-secondary-foreground)",
  },
  {
    name: "Button ghost 배경",
    selector: BUTTON("ghost"),
    property: "background-color",
    value: "transparent",
  },
  {
    name: "Button link 글자",
    selector: BUTTON("link"),
    property: "color",
    value: "var(--color-primary)",
  },
  // Button — 모양
  {
    name: "Button 모서리",
    selector: BUTTON("default"),
    property: "border-top-left-radius",
    value: "var(--radius-lg)",
  },
  {
    name: "Button 글자 크기",
    selector: BUTTON("default"),
    property: "font-size",
    value: "var(--text-xs)",
  },
  {
    name: "Button 좌우 여백",
    selector: BUTTON("default"),
    property: "padding-left",
    value: "calc(var(--spacing) * 4)",
  },
  {
    name: "Button 높이",
    selector: BUTTON("default"),
    property: "height",
    value: "calc(var(--spacing) * 8)",
  },
  {
    name: "Button disabled 불투명도",
    selector:
      '[data-testid="primitives-buttons"] [data-slot="button"][data-variant="default"]:disabled',
    property: "opacity",
    value: "0.5",
  },
  // Badge — 중립 칩 전용. 상태·의미 색은 ToneBadge
  {
    name: "Badge default 배경",
    selector: BADGE("default"),
    property: "background-color",
    value: "color-mix(in oklab, var(--color-carix-info) 10%, transparent)",
  },
  {
    name: "Badge default 글자",
    selector: BADGE("default"),
    property: "color",
    value: "var(--color-carix-info)",
  },
  {
    name: "Badge secondary 배경",
    selector: BADGE("secondary"),
    property: "background-color",
    value: "color-mix(in oklab, var(--color-carix-border) 30%, transparent)",
  },
  {
    name: "Badge outline 테두리",
    selector: BADGE("outline"),
    property: "border-top-color",
    value: "var(--color-carix-border)",
  },
  {
    name: "Badge 모서리",
    selector: BADGE("default"),
    property: "border-top-left-radius",
    value: "calc(infinity * 1px)",
  },
  {
    name: "Badge 글자 크기",
    selector: BADGE("default"),
    property: "font-size",
    value: "11px",
  },
  // Card
  {
    name: "Card 배경",
    selector: COMPOSED_CARD,
    property: "background-color",
    value: "var(--color-carix-surface)",
  },
  {
    name: "Card 테두리",
    selector: COMPOSED_CARD,
    property: "border-top-color",
    value: "var(--color-carix-border)",
  },
  {
    name: "Card 모서리",
    selector: COMPOSED_CARD,
    property: "border-top-left-radius",
    value: "var(--radius-xl)",
  },
  {
    name: "Card 위 여백",
    selector: COMPOSED_CARD,
    property: "padding-top",
    value: "calc(var(--spacing) * 6)",
  },
  {
    name: "Card 좌 여백",
    selector: COMPOSED_CARD,
    property: "padding-left",
    value: "calc(var(--spacing) * 6)",
  },
  {
    name: "Card 자식 간격",
    selector: COMPOSED_CARD,
    property: "row-gap",
    value: "calc(var(--spacing) * 4)",
  },
  // Table
  {
    name: "Table thead 아래 테두리",
    selector: TABLE_HEAD,
    property: "border-bottom-color",
    value: "var(--color-carix-border)",
  },
  {
    name: "Table thead 글자 크기",
    selector: TABLE_HEAD,
    property: "font-size",
    value: "var(--text-xs)",
  },
  {
    name: "Table 틀 모서리",
    selector: '[data-testid="primitives-table"] [data-slot="table-container"]',
    property: "border-top-left-radius",
    value: "var(--radius-xl)",
  },
  {
    name: "Table 칸 여백",
    selector: '[data-testid="primitives-table"] [data-slot="table-cell"]',
    property: "padding-left",
    value: "calc(var(--spacing) * 4)",
  },
  // Input
  {
    name: "Input 테두리",
    selector: INPUT,
    property: "border-top-color",
    value: "var(--color-input)",
  },
  {
    name: "Input 배경",
    selector: INPUT,
    property: "background-color",
    value: "transparent",
    themes: ["light"],
  },
  {
    name: "Input 모서리",
    selector: INPUT,
    property: "border-top-left-radius",
    value: "var(--radius-md)",
  },
  {
    name: "CardContent 좌 여백 없음",
    selector: '[data-testid="primitives-composed"] [data-slot="card-content"]',
    property: "padding-left",
    value: "0px",
  },
];

/** 탐침 요소에 값을 칠해 현재 테마에서의 계산값을 얻는다. */
async function resolveCss(page: Page, property: string, value: string) {
  return page.evaluate(
    ([prop, val]) => {
      const probe = document.createElement("div");
      probe.style.setProperty(prop, val);
      // 테두리 색은 테두리 스타일이 있어야 계산값이 의미를 갖는다.
      probe.style.borderStyle = "solid";
      document.body.appendChild(probe);
      const resolved = getComputedStyle(probe).getPropertyValue(prop);
      probe.remove();
      return resolved;
    },
    [property, value] as const,
  );
}

for (const theme of ["light", "dark"] as const) {
  test(`프리미티브가 기대한 토큰을 쓴다 (${theme})`, async ({ page }) => {
    await page.goto("/dev/primitives");
    await setTheme(page, theme);

    const mismatches: string[] = [];
    for (const expectation of STYLE_EXPECTATIONS) {
      if (expectation.themes && !expectation.themes.includes(theme)) continue;

      const target = page.locator(expectation.selector).first();
      // 셀렉터가 아무것도 못 찾아 단언이 공허하게 통과하는 사고를 막는다.
      await expect(target, expectation.name).toBeAttached();

      const actual = await target.evaluate(
        (node, prop) => getComputedStyle(node).getPropertyValue(prop),
        expectation.property,
      );
      const expected =
        expectation.value === "currentColor"
          ? await target.evaluate((node) => getComputedStyle(node).color)
          : await resolveCss(page, expectation.property, expectation.value);
      if (actual !== expected) {
        mismatches.push(
          `${expectation.name}: ${expectation.property} = ${actual}, 기대 ${expectation.value} → ${expected}`,
        );
      }
    }

    // 하나씩 실패시키지 않고 모아서 보인다 — P3 에서 무엇이 바뀌었는지 한 번에 읽힌다.
    expect(mismatches).toEqual([]);
  });

  test(`연봉 분포 마커 링이 테마의 카드 면색을 쓴다 (${theme})`, async ({
    page,
  }) => {
    await page.goto("/dev/primitives");
    await setTheme(page, theme);

    const markers = page
      .getByTestId("gallery-chart-marker")
      .locator(".rounded-full.ring-2, .rounded-full.ring-4");
    // 마커는 차트가 위치를 잰 뒤에 그려진다 — 셋이 다 뜰 때까지 기다린다.
    await expect.poll(() => markers.count()).toBeGreaterThanOrEqual(3);
    const shadows = await markers.evaluateAll((nodes) =>
      nodes.map((n) => getComputedStyle(n).boxShadow),
    );

    // 라이트는 흰 링, 다크는 카드 면색 링 — `dark:` 변형이 실제로 걸리는지를 잰다.
    const ringColor = await resolveCss(
      page,
      "color",
      theme === "light" ? "white" : "var(--color-carix-surface)",
    );
    for (const shadow of shadows) {
      expect(shadow).toContain(ringColor);
    }
  });
}

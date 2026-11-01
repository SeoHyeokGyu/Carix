import { expect, type Page, test } from "@playwright/test";

/**
 * 게스트 모드 한 줄기 — 랜딩에서 체험을 시작해 온보딩을 마치고, 시뮬레이터 저장에서
 * 가입한 뒤 입력이 같은 계정에 그대로 남는지 본다.
 *
 * 이 흐름은 실제 백엔드에서 게스트와 정식 계정을 새로 만든다. CARIX_E2E_GUEST 가 없으면
 * 스킵된다(onboarding-flow.spec.ts 의 CARIX_E2E_EMAIL 과 같은 규약, API 목 없음).
 * 스킬 단계는 표준 스킬 사전에 "Java" 가 있다고 본다.
 */

const SALARY_MAN = "6500";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

async function fillOnboarding(page: Page) {
  await page.getByLabel("주 직무").selectOption({ label: "개발" });
  await page.getByLabel("총 경력 (년)").fill("5");
  await page.getByLabel("희망 근무 지역").selectOption({ label: "서울" });
  await page.getByLabel("고용 형태").selectOption({ label: "정규직" });
  await page.getByRole("button", { name: "다음", exact: true }).click();

  await expect(page).toHaveURL(/\/onboarding\/income$/);
  await page.getByLabel("세전 연봉 (만원)").fill(SALARY_MAN);
  await page.getByLabel("월 실수령액 (만원)").fill("450");
  await page.getByLabel("연봉 적용 시작일").fill("2024-01-01");
  await page.getByRole("button", { name: "추가", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "2024-01-01", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "다음", exact: true }).click();

  // 소비는 비워 두면 서버가 평균으로 채운다.
  await expect(page).toHaveURL(/\/onboarding\/expense$/);
  await page.getByRole("button", { name: "다음", exact: true }).click();

  await expect(page).toHaveURL(/\/onboarding\/skills$/);
  await page.getByLabel("스킬 검색", { exact: true }).fill("Java");
  await page.getByRole("option").first().click();
  await page.getByLabel(/실무 사용 기간/).fill("24");
  await page.getByLabel("마지막 사용일").fill(today());
  await page.getByRole("button", { name: "+ 스킬 추가" }).click();
  await expect(page.getByText(/스킬이 등록되었습니다/)).toBeVisible();
  await page.getByRole("button", { name: "다음", exact: true }).click();

  await expect(page).toHaveURL(/\/onboarding\/review$/);
}

/**
 * 검토 화면 소득 카드의 "세전 연봉 X · 월 실수령액 Y" 에서 X 만 읽는다. 대시보드의 같은
 * 줄은 첫 지수 계산이 끝나야 그려져 새 계정에서는 1~2분 걸리므로 쓰지 않는다.
 */
async function readReviewSalary(page: Page): Promise<string> {
  const line = page.getByText(/세전 연봉 .+ · 월 실수령액/);
  await expect(line).toBeVisible();
  const text = (await line.textContent()) ?? "";
  const match = text.match(/세전 연봉 (.+?) · 월 실수령액/);
  if (!match) throw new Error(`연봉 줄을 읽지 못했습니다: ${text}`);
  return match[1];
}

test.describe("게스트 체험부터 가입까지", () => {
  test.skip(
    !process.env.CARIX_E2E_GUEST,
    "게스트 흐름은 실제 백엔드에서 계정을 만든다",
  );

  test("게스트 — 체험부터 가입까지 입력이 이어진다", async ({ page }) => {
    // 실제 백엔드에서 온보딩 다섯 단계와 가입까지 한 번에 돈다.
    test.setTimeout(180_000);
    const scenarioName = `E2E-${Date.now()}`;
    const email = `e2e-guest-${Date.now()}@example.com`;

    await page.goto("/");
    await page.getByRole("button", { name: "가입 없이 시작하기" }).click();
    await expect(page).toHaveURL(/\/onboarding\/profile$/);

    await fillOnboarding(page);
    const salaryAsGuest = await readReviewSalary(page);
    await page.getByRole("button", { name: "내 판정 보기" }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByText(/체험 중입니다\./)).toBeVisible();

    await page.goto("/dashboard/simulator");
    await page.getByLabel("올해 예상 인상률 (%)").fill("3");
    // 상단과 결과 아래 두 곳에 같은 저장 버튼이 있다 — 상단 것을 누른다.
    await page.getByRole("button", { name: "시나리오 저장" }).first().click();
    await page.getByLabel("시나리오 이름 (선택)").fill(scenarioName);
    await page.getByRole("button", { name: "저장", exact: true }).click();
    await expect(page).toHaveURL(/\/register\?next=/);

    await page.getByLabel("이메일").fill(email);
    await page.getByLabel("비밀번호").fill("e2e-guest-password-1");
    await page.getByRole("button", { name: "계정 만들기" }).click();

    await expect(page).toHaveURL(/\/dashboard\/simulator$/);
    await expect(
      page.getByRole("status").filter({ hasText: "시나리오를 저장했습니다." }),
    ).toBeVisible();
    await page.getByRole("button", { name: /저장된 시나리오/ }).click();
    await expect(page.getByText(scenarioName)).toBeVisible();

    await page.goto("/dashboard");
    await expect(page.getByText(/체험 중입니다\./)).toHaveCount(0);

    await page.goto("/onboarding/review");
    expect(await readReviewSalary(page)).toBe(salaryAsGuest);
  });
});

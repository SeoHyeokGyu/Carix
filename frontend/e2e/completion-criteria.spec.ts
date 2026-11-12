import { expect, type Page, test } from "@playwright/test";

/**
 * 기능 명세 §7 MVP 완료 기준 — 정식 계정 한 줄기로 1~6·9 를 실제 백엔드에서 확인한다.
 *
 * 매 실행마다 새 계정을 만들고 마지막에 탈퇴로 지운다. 실제 계정을 만들므로
 * CARIX_E2E_CRITERIA 가 있을 때만 돈다(guest-flow.spec.ts 와 같은 규약, API 목 없음).
 *
 * 브라우저로 못 보는 것은 실행 뒤 운영에서 따로 본다 — 탈퇴한 userId 의 DB 잔존 행(9),
 * 컨테이너 로그에 아래 연봉 숫자가 남았는지(10). 그래서 userId 를 annotation 으로 남긴다.
 * 점검 절차와 결과는 verification/ 의 완료 기준 점검 기록이 갖는다.
 */

// 로그 grep 에 걸리도록 다른 수치와 겹치지 않는 값을 쓴다(만원 단위 6543 → 원 단위 65430000).
const SALARY_MAN = "6543";
const TAKE_HOME_MAN = "452";
const OFFER_SALARY_MAN = "7400";
const PASSWORD = "e2e-criteria-password-1";

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

async function login(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("이메일").fill(email);
  await page.getByLabel("비밀번호").fill(PASSWORD);
  await page.getByRole("button", { name: "로그인" }).click();
  // 로그인 응답과 쿠키가 심긴 뒤에야 이동한다 — 기다리지 않으면 다음 goto 와 경합한다.
  await expect(page).toHaveURL(/\/onboarding\/|\/dashboard/);
}

async function fillProfileAndIncome(page: Page) {
  await page.getByLabel("주 직무").selectOption({ label: "개발" });
  await page.getByLabel("총 경력 (년)").fill("5");
  await page.getByLabel("희망 근무 지역").selectOption({ label: "서울" });
  await page.getByLabel("고용 형태").selectOption({ label: "정규직" });
  await page.getByLabel("입사일", { exact: true }).fill("2022-03-02");
  await page.getByRole("button", { name: "다음", exact: true }).click();

  await expect(page).toHaveURL(/\/onboarding\/income$/);
  await page.getByLabel("세전 연봉 (만원)").fill(SALARY_MAN);
  await page.getByLabel("월 실수령액 (만원)").fill(TAKE_HOME_MAN);
  await page.getByLabel("연봉 적용 시작일").fill("2024-01-01");
  await page.getByRole("button", { name: "추가", exact: true }).click();
  await expect(
    page.getByRole("cell", { name: "2024-01-01", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "다음", exact: true }).click();
  await expect(page).toHaveURL(/\/onboarding\/expense$/);
}

async function finishOnboarding(page: Page) {
  // 소비는 비워 둔다 — 기준 4 의 「기본값 추정」 상태를 대시보드에서 보려는 것이다.
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
  await page.getByRole("button", { name: "내 판정 보기" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

test.describe("MVP 완료 기준 — 정식 계정 한 줄기", () => {
  test.skip(
    !process.env.CARIX_E2E_CRITERIA,
    "실제 백엔드에 계정을 만들고 지운다 — 완료 기준 점검 때만 돈다",
  );

  test("완료 기준 — 가입부터 탈퇴까지", async ({ page, browser }) => {
    // 온보딩 두 번, 첫 지수 계산 대기(최대 2분), 시뮬레이터, 탈퇴까지 한 번에 돈다.
    test.setTimeout(300_000);
    const email = `e2e-criteria-${Date.now()}@example.com`;

    // 기준 1 — 가입
    await page.goto("/register");
    await page.getByLabel("이메일").fill(email);
    await page.getByLabel("비밀번호").fill(PASSWORD);
    await page.getByRole("button", { name: "계정 만들기" }).click();
    await expect(page).toHaveURL(/\/onboarding\/profile$/);

    const session = await page.request.get("/api/v1/auth/session");
    expect(session.ok()).toBe(true);
    const { userId } = (await session.json()) as { userId: string };
    test.info().annotations.push({ type: "userId", description: userId });
    test.info().annotations.push({ type: "salary", description: SALARY_MAN });

    // 기준 2 — 소득까지 넣고 중단한 뒤, 새 브라우저에서 로그인하면 같은 지점(소비)에서 재개한다
    await fillProfileAndIncome(page);
    const resumed = await browser.newPage();
    await login(resumed, email);
    await expect(resumed).toHaveURL(/\/onboarding\/expense$/);
    await finishOnboarding(resumed);

    // 기준 3·4·5 — 판정·적용 규칙·근거·기준일·입력 출처. 첫 계산은 화면이 폴링한다.
    const rules = resumed.getByRole("list", { name: "적용 규칙" });
    await expect(rules).toBeVisible({ timeout: 150_000 });
    await expect(rules.getByRole("listitem").first()).toBeVisible();
    // 소비를 비웠으니 구매력 카드는 통계청 평균 가중치로 계산했다고 밝혀야 한다.
    await expect(resumed.getByText("통계청 평균 기준")).toBeVisible();
    // 기준월은 머리말에 늘 보이고, 일 단위 기준일·규칙 버전은 신뢰 층 안에 있다.
    await expect(
      resumed.getByText(/\d{4}년 \d{1,2}월 데이터 기준/),
    ).toBeVisible();
    await resumed.getByRole("button", { name: "근거 펼치기" }).click();
    await expect(resumed.getByText(/기준일 \d{4}-\d{2}-\d{2}/)).toBeVisible();
    await expect(resumed.getByText(/규칙 버전 \d+\.\d+\.\d+/)).toBeVisible();

    // 기준 6 — 잔류·이직을 4년 누적으로 비교한다
    await resumed.goto("/dashboard/simulator");
    await resumed.getByLabel("올해 예상 인상률 (%)").fill("3");
    await resumed.getByRole("button", { name: "오퍼 넣기" }).click();
    await resumed
      .getByLabel("새 회사 연봉 (세전, 만원)")
      .fill(OFFER_SALARY_MAN);
    await resumed.getByLabel("못 받음").check();
    await expect(resumed.getByText("4년 누적 차이")).toBeVisible({
      timeout: 30_000,
    });
    await expect(resumed.getByText("따라잡는 시점")).toBeVisible();

    // 기준 1 — 로그아웃 후 다시 로그인
    await resumed.goto("/dashboard");
    await resumed.getByRole("button", { name: "내 정보" }).click();
    await resumed.getByRole("menuitem", { name: "로그아웃" }).click();
    await expect(resumed).toHaveURL(/\/login/);
    await login(resumed, email);
    await expect(resumed).toHaveURL(/\/dashboard/);

    // 기준 9 — 탈퇴하면 같은 자격 증명으로 다시 로그인할 수 없다
    await resumed.goto("/mypage/profile");
    await resumed
      .getByLabel("탈퇴 확인을 위해 이메일 주소를 입력해 주세요:")
      .fill(email);
    await resumed
      .getByLabel(
        "안내 사항을 모두 확인하였으며, 데이터 영구 삭제에 동의합니다.",
      )
      .check();
    await resumed
      .getByRole("button", { name: "회원 탈퇴 및 모든 데이터 삭제" })
      .click();
    await expect(resumed).toHaveURL(/\/login\?deleted=true/);

    const relogin = await resumed.request.post("/api/v1/auth/login", {
      data: { email, password: PASSWORD },
    });
    expect(relogin.status()).toBe(401);
  });
});

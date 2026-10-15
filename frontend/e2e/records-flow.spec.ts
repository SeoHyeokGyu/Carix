import { expect, type Page, test } from "@playwright/test";

/**
 * S-12 이력 관리 — 진입 가드와 인증 후 한 줄기.
 *
 * 상시로 도는 것은 가드뿐이다. 세 탭 전부 `protected.tsx` → `mypage/layout.tsx` 순으로
 * requireSession 을 거치므로, 세션 없는 방문자는 탭 본문이 그려지기 전에 랜딩으로 튕긴다.
 * CRUD 한 줄기는 계정 환경변수가 있을 때만 돈다 — onboarding-flow.spec.ts 와 같은 규약.
 */

const PROTECTED = [
  "/mypage/records",
  "/mypage/records/expense",
  "/mypage/records/skills",
];

for (const path of PROTECTED) {
  // "이력 관리 —" 접두어: `--grep "이력 관리"` 가 게이트 안 describe 뿐 아니라 이
  // 상시 가드 루프도 선택하게 한다. 접두어 없이는 계정 없는 일반 체크아웃에서
  // 이 검증 명령이 상시 커버리지를 하나도 실행하지 않는 채로 조용히 통과해버린다.
  test(`이력 관리 — 세션 없이 ${path} 를 열면 랜딩으로 돌아온다`, async ({
    page,
  }) => {
    await page.goto(path);
    await expect(page).toHaveURL("/");
  });
}

/**
 * 로그인 후 한 줄기 — 이력 관리 세 탭.
 *
 * CARIX_E2E_EMAIL·CARIX_E2E_PASSWORD 가 없으면 이 describe 전체가 스킵된다(로컬·
 * 일반 CI 기본값). 7주차 통합 점검에서 실제 계정을 이 두 환경변수로 주입하면 돈다.
 */
test.describe("로그인 후 이력 관리 한 줄기", () => {
  test.skip(
    !process.env.CARIX_E2E_EMAIL,
    "CARIX_E2E_EMAIL·CARIX_E2E_PASSWORD 가 있을 때만 돈다 — 7주차 통합 점검용",
  );

  async function loginAsE2eUser(page: Page) {
    await page.goto("/login");
    await page.getByLabel("이메일").fill(process.env.CARIX_E2E_EMAIL!);
    await page.getByLabel("비밀번호").fill(process.env.CARIX_E2E_PASSWORD!);
    await page.getByRole("button", { name: "로그인" }).click();
    // click() 은 이벤트를 보내자마자 끝난다 — 세션 쿠키가 심기기 전에 goto 가 나가면
    // 로그인 요청과 경합해 그 요청을 끊어버릴 수 있다.
    await expect(page).toHaveURL(/\/onboarding\/|\/dashboard/);
  }

  test("세 탭을 오가며 이력을 고친다", async ({ page }) => {
    await loginAsE2eUser(page);
    await page.goto("/mypage/records");

    await page.getByLabel(/세전 연봉/).fill("6500");
    await page.getByLabel(/월 실수령액/).fill("420");
    await page.getByLabel(/연봉 적용 시작일/).fill("2024-01-01");
    await page.getByRole("button", { name: "저장" }).click();
    await expect(page.getByText("2024-01-01")).toBeVisible();

    await page.getByRole("link", { name: "소비" }).click();
    await expect(page).toHaveURL(/\/mypage\/records\/expense$/);

    await page.getByRole("link", { name: "스킬" }).click();
    await expect(page).toHaveURL(/\/mypage\/records\/skills$/);
  });

  test("탭 딥링크가 새로고침 후에도 유지된다", async ({ page }) => {
    await loginAsE2eUser(page);
    await page.goto("/mypage/records/skills");
    await page.reload();
    await expect(page.getByRole("link", { name: "스킬" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });
});

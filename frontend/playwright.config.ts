import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  expect: {
    // Playwright 픽셀 컷오프는 35215 × threshold² 다. 기본 0.2(1408.6)는 물론 0.1(352.15)도
    // 이 그물이 잡아야 할 재색상화 YIQ 델타(268.43 / 102.67)보다 커서 무효다. 0.05 는 88.04.
    //
    // maxDiffPixels 는 그물을 조이는 노브가 아니라 느슨하게만 만든다(미설정이 가장 엄격한 0).
    // 0.05 에서 라틴 글자 안티에일리어싱 잡음이 스크린샷당 1~2% 확률로 최대 63px 나와 22장
    // 묶음이 셋 중 하나꼴로 빨개졌다 — 그 잡음 위, 이 그물이 처음 잡은 표류(P1 뒤 베이스라인이
    // 못 따라간 NEGOTIATE 뱃지 재색상화 261px) 아래로 둔다. 그보다 작은 재색상화는 primitives.spec.ts 의
    // computed-style 단언이 막는다.
    toHaveScreenshot: { threshold: 0.05, maxDiffPixels: 100 },
  },
  webServer: {
    command: "pnpm build && pnpm start",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  use: {
    baseURL: "http://localhost:3000",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});

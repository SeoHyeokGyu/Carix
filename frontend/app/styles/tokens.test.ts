import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const css = readFileSync(
  resolve(process.cwd(), "app/styles/tokens.css"),
  "utf-8",
);

describe("design tokens", () => {
  it("배경/표면/보더/텍스트 hex를 spec 값 그대로 포함한다", () => {
    expect(css).toContain("#020617");
    expect(css).toContain("#0f172a");
    expect(css).toContain("#1e293b");
    expect(css).toContain("#f8fafc");
  });

  it("강조 색상 4종 hex를 spec 값 그대로 포함한다", () => {
    expect(css).toContain("#34d399");
    expect(css).toContain("#fbbf24");
    expect(css).toContain("#fb7185");
    expect(css).toContain("#818cf8");
  });

  it("Outfit과 Pretendard 서체 토큰을 정의한다", () => {
    expect(css).toMatch(/--font-display:.*Outfit/);
    expect(css).toMatch(/--font-body:.*Pretendard/);
  });
});

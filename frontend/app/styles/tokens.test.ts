import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath, URL as NodeURL } from "node:url";

const css = readFileSync(
  fileURLToPath(new NodeURL("./tokens.css", import.meta.url)),
  "utf-8",
);

describe("design tokens", () => {
  it("배경/표면/보더/텍스트 hex를 spec 값 그대로 포함한다", () => {
    expect(css).toContain("#111827");
    expect(css).toContain("#1f2937");
    expect(css).toContain("#374151");
    expect(css).toContain("#f3f4f6");
    expect(css).toContain("#ffffff");
    expect(css).toContain("#e5e7eb");
  });

  it("강조 색상 5종 hex를 spec 값 그대로 포함한다", () => {
    expect(css).toContain("#10b981");
    expect(css).toContain("#f97316");
    expect(css).toContain("#ef4444");
    expect(css).toContain("#4f46e5");
    expect(css).toContain("#a78bfa");
    expect(css).toContain("#6d28d9");
  });

  it("--carix-action 을 @theme 등록 1곳과 값 정의 4곳(:root·미디어·.dark·.light)에 전부 유지한다", () => {
    expect(css).toContain("--color-carix-action: var(--carix-action);");
    const valueDefinitions =
      css.match(/--carix-action:\s*#[0-9a-f]{6};/g) ?? [];
    expect(valueDefinitions).toHaveLength(4);
  });

  it("Outfit과 Pretendard 서체 토큰을 정의한다", () => {
    expect(css).toMatch(/--font-display:.*Outfit/);
    expect(css).toMatch(/--font-body:.*Pretendard/);
  });

  it("reduced-motion 상태에서 crossover-line 애니메이션을 비활성화한다", () => {
    expect(css).toMatch(
      /\[data-reduced-motion="true"\][^}]*\.crossover-line[^{]*\{[^}]*animation:\s*none/,
    );
  });
});

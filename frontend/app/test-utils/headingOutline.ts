import { screen, within } from "@testing-library/react";
import { expect } from "vitest";

/** 화면 제목 단계 — h1 하나로 시작하고, 건너뛰지 않으며, h3 보다 깊지 않다. */
export function expectHeadingOutline(root?: HTMLElement): void {
  const scope = root ? within(root) : screen;
  const levels = scope
    .queryAllByRole("heading")
    .map((h) => Number(h.tagName.slice(1)));
  // heading 이 하나도 없으면 「h1 이 없다」로 실패해야 원인이 보인다.
  expect(levels[0]).toBe(1);
  expect(levels.filter((l) => l === 1)).toHaveLength(1);
  expect(Math.max(...levels)).toBeLessThanOrEqual(3);
  levels.slice(1).forEach((level, i) => {
    expect(level - levels[i]).toBeLessThanOrEqual(1);
  });
}

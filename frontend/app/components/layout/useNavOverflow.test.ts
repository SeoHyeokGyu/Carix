import { describe, expect, it } from "vitest";

import { measureOverflow, navMask, revealCurrent } from "./useNavOverflow";

function box(scrollLeft: number, clientWidth = 100, scrollWidth = 315) {
  return { scrollLeft, clientWidth, scrollWidth };
}

describe("measureOverflow", () => {
  it("중간이면 양쪽이 가려졌다", () => {
    expect(measureOverflow(box(100))).toEqual({ start: true, end: true });
  });

  it("다 들어가면 어느 쪽도 가려지지 않았다", () => {
    expect(measureOverflow(box(0, 315, 315))).toEqual({
      start: false,
      end: false,
    });
  });
});

describe("revealCurrent", () => {
  it("오른쪽 밖에 걸친 선택 메뉴는 띠 가운데로 민다", () => {
    const nav = box(0);
    revealCurrent(nav, { offsetLeft: 230, offsetWidth: 80 });
    expect(nav.scrollLeft).toBe(230 + 40 - 50);
  });

  it("가운데로 밀 자리가 앞쪽에 없으면 맨 앞에 둔다", () => {
    const nav = box(200);
    revealCurrent(nav, { offsetLeft: 0, offsetWidth: 70 });
    expect(nav.scrollLeft).toBe(0);
  });
});

describe("navMask", () => {
  it("가려진 쪽 끝만 투명하게 흐린다", () => {
    expect(navMask({ start: false, end: true })).toMatch(
      /^linear-gradient\(to right, #000, .*, transparent\)$/,
    );
    expect(navMask({ start: true, end: false })).toMatch(
      /^linear-gradient\(to right, transparent, .*, #000\)$/,
    );
  });
});

import { type RefObject, useEffect, useState } from "react";

/** 띠 끝을 흐리는 폭. */
const FADE_PX = 24;

export interface NavOverflow {
  /** 왼쪽으로 가려진 항목이 있다. */
  start: boolean;
  /** 오른쪽으로 가려진 항목이 있다. */
  end: boolean;
}

type ScrollBox = Pick<
  HTMLElement,
  "scrollLeft" | "clientWidth" | "scrollWidth"
>;

export function measureOverflow(nav: ScrollBox): NavOverflow {
  return {
    start: nav.scrollLeft > 1,
    end: nav.scrollLeft + nav.clientWidth < nav.scrollWidth - 1,
  };
}

/**
 * 선택 메뉴가 띠 밖에 걸치면 띠 가운데로 민다. 띠는 `relative` 라 offsetLeft 가 띠 기준이다.
 *
 * 끝에 붙이지 않고 가운데로 두는 이유 — 375px 에서 띠(약 92px)가 메뉴 하나(약 71px)와 양끝
 * 흐림을 다 담지 못해, 끝에 붙이면 반대쪽 글자가 잘리거나 흐림에 묻힌다.
 */
export function revealCurrent(
  nav: ScrollBox,
  current: Pick<HTMLElement, "offsetLeft" | "offsetWidth">,
): void {
  const left = current.offsetLeft;
  const right = left + current.offsetWidth;
  if (left >= nav.scrollLeft && right <= nav.scrollLeft + nav.clientWidth) {
    return;
  }
  const centered = left + current.offsetWidth / 2 - nav.clientWidth / 2;
  nav.scrollLeft = Math.max(0, centered);
}

export function navMask({ start, end }: NavOverflow): string | undefined {
  if (!start && !end) return undefined;
  const from = start ? "transparent" : "#000";
  const to = end ? "transparent" : "#000";
  return `linear-gradient(to right, ${from}, #000 ${FADE_PX}px, #000 calc(100% - ${FADE_PX}px), ${to})`;
}

/**
 * 좁은 화면의 주 메뉴 띠 — 가려진 쪽을 알리고, 화면이 바뀌면 선택 메뉴를 드러낸다.
 *
 * 화면 안 링크(「보유 스킬 관리」, 시뮬레이터 안내 등)로 들어오면 선택 메뉴가 띠 밖에 있어
 * 지금 어디인지 안 보였다.
 */
export function useNavOverflow(
  ref: RefObject<HTMLElement | null>,
  pathname: string,
): NavOverflow {
  const [overflow, setOverflow] = useState<NavOverflow>({
    start: false,
    end: false,
  });

  useEffect(() => {
    const nav = ref.current;
    if (!nav) return;
    const current = nav.querySelector<HTMLElement>("[aria-current]");
    if (current) revealCurrent(nav, current);

    const update = () => setOverflow(measureOverflow(nav));
    update();
    nav.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    return () => {
      nav.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
    };
  }, [ref, pathname]);

  return overflow;
}

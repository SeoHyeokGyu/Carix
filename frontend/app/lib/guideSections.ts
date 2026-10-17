/** 도움말 화면(`/dashboard/guide`) 섹션 앵커. 대시보드 ⓘ 툴팁이 이 자리로 바로 보낸다. */
export const GUIDE_SECTION = {
  ppi: "guide-ppi",
  mvi: "guide-mvi",
  owi: "guide-owi",
  signal: "guide-signal",
} as const;

export type GuideSection = keyof typeof GUIDE_SECTION;

export function guideHref(section: GuideSection): string {
  return `/dashboard/guide#${GUIDE_SECTION[section]}`;
}

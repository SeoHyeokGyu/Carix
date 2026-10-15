/**
 * 밑줄 탭 한 칸. 화면 안 전환(role=tab)과 화면 이동(NavLink)이 같은 모양을 쓴다.
 * 화면 안 전환은 Button ghost 위에 덮으므로 마우스 올림 배경(다크 포함)과 글자색을 여기서 지운다.
 */
export const TAB_ITEM =
  "-mb-px whitespace-nowrap border-b-2 px-4 py-2 text-sm font-medium transition-colors hover:bg-transparent dark:hover:bg-transparent";
export const TAB_ACTIVE =
  "border-carix-info text-carix-info font-semibold hover:text-carix-info";
export const TAB_IDLE =
  "border-transparent text-carix-text/70 hover:text-carix-text";
/** 탭 줄. 자기 밑줄을 가진다 — 바로 위 PageHeader 는 divider 를 켜지 않는다. */
export const TAB_LIST = "flex items-center gap-1 border-b border-carix-border";

/**
 * 수집 데이터 네 탭이 같은 모양을 쓰게 하는 클래스 묶음. 공용 `Table` 의 기본 칸은 사용자 화면용이라
 * 넓고, 관리자 표는 열이 많아 촘촘하게 줄인다.
 */
export const HEAD = "h-auto px-3 py-2 text-[11px]";
export const CELL = "px-3 py-1.5 text-xs";
export const NUM = "text-right tabular-nums";

/** 조회 조건 입력 하나의 틀 — 위에 작은 라벨, 아래에 입력. */
export const FIELD = "flex flex-col gap-1 text-[11px] text-carix-text/60";
export const CONTROL =
  "h-8 rounded-md border border-carix-border bg-carix-surface px-2 text-xs text-carix-text";

/** 표 바로 위의 안내 줄(범례·건수·상태). */
export const META =
  "flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-carix-text/60";

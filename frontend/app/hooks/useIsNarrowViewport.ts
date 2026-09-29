import { useSyncExternalStore } from "react";

/** Tailwind `md`(768px) 바로 아래. */
export const NARROW_VIEWPORT_QUERY = "(max-width: 767px)";

function subscribe(callback: () => void) {
  const mediaQueryList = window.matchMedia(NARROW_VIEWPORT_QUERY);
  mediaQueryList.addEventListener("change", callback);
  return () => mediaQueryList.removeEventListener("change", callback);
}

function getSnapshot() {
  return window.matchMedia(NARROW_VIEWPORT_QUERY).matches;
}

function getServerSnapshot() {
  return false;
}

export function useIsNarrowViewport(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

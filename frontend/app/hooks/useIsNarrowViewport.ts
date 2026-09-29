import { useMediaQuery } from "./useMediaQuery";

/** Tailwind `md`(768px) 바로 아래. */
export const NARROW_VIEWPORT_QUERY = "(max-width: 767px)";

export function useIsNarrowViewport(): boolean {
  return useMediaQuery(NARROW_VIEWPORT_QUERY);
}

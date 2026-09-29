import { useCallback, useSyncExternalStore } from "react";

function subscribe(query: string, callback: () => void) {
  const mediaQueryList = window.matchMedia(query);
  mediaQueryList.addEventListener("change", callback);
  return () => mediaQueryList.removeEventListener("change", callback);
}

function getSnapshot(query: string) {
  return window.matchMedia(query).matches;
}

function getServerSnapshot() {
  return false;
}

export function useMediaQuery(query: string): boolean {
  const subscribeToQuery = useCallback(
    (callback: () => void) => subscribe(query, callback),
    [query],
  );
  const getQuerySnapshot = useCallback(() => getSnapshot(query), [query]);

  return useSyncExternalStore(
    subscribeToQuery,
    getQuerySnapshot,
    getServerSnapshot,
  );
}

import { useEffect, useRef, useState } from "react";

type FocusTarget<K> = { to: "group" | "chip"; key: K };

/**
 * 선택 항목 그룹의 펼침 상태. 처음에는 값이 있는 그룹만 펼친다.
 *
 * <p>포커스는 누른 동작에서만 옮긴다 — 마운트(불러오기·템플릿)에서 옮기면 제목으로 보낸 포커스를 빼앗는다.
 * 추가하면 그룹의 첫 칸으로, 빼면 다시 나타난 칩으로 보낸다(누른 `빼기` 가 사라지므로).
 */
export function useOptionalGroups<K extends string>(initial: readonly K[]) {
  const [added, setAdded] = useState<ReadonlySet<K>>(() => new Set(initial));
  const pending = useRef<FocusTarget<K> | null>(null);
  const nodes = useRef(new Map<string, HTMLElement | null>());

  useEffect(() => {
    const target = pending.current;
    if (!target) return;
    pending.current = null;
    const node = nodes.current.get(`${target.to}:${target.key}`);
    const focusable =
      target.to === "group"
        ? node?.querySelector<HTMLElement>("input, select, textarea")
        : node;
    focusable?.focus();
  });

  const add = (key: K) => {
    pending.current = { to: "group", key };
    setAdded((prev) => new Set([...prev, key]));
  };

  const remove = (key: K) => {
    pending.current = { to: "chip", key };
    setAdded((prev) => new Set([...prev].filter((k) => k !== key)));
  };

  const register =
    (to: "group" | "chip", key: K) => (node: HTMLElement | null) => {
      nodes.current.set(`${to}:${key}`, node);
    };

  return { isAdded: (key: K) => added.has(key), add, remove, register };
}

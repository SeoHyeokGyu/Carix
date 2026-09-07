export interface Option {
  value: string;
  label: string;
}

/**
 * 벡터의 `value` 리터럴 유니온을 그대로 살려 돌려준다. `Option[]` 로 넓혀
 * 받으면 zod enum 이 `string` 으로만 추론돼 오타를 컴파일 타임에 못 잡는다 —
 * 호출부는 `[...] as const satisfies Option[]` 로 선언해야 이 제네릭이
 * 리터럴을 본다.
 */
export const values = <T extends readonly { value: string }[]>(
  options: T,
): [T[number]["value"], ...T[number]["value"][]] =>
  options.map((o) => o.value) as [T[number]["value"], ...T[number]["value"][]];

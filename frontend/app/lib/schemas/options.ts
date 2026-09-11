export interface Option {
  value: string;
  label: string;
}

/**
 * `income.ts` 의 `grossSalary` 와 `scenario.ts` 의 `moveTargetGrossSalary` 가
 * 같은 범위를 쓴다(둘 다 `z.number().int().min(10_000_000).max(3_000_000_000)`)
 * — 범위를 설명하는 문구 조각을 한 곳에 모아 두 화면이 같은 규칙을 각자 다른
 * 말로 적다가 한쪽만 고쳐 어긋나는 일을 막는다. 각 화면은 이 조각 앞에 자기
 * 필드 이름만 붙인다("세전 연봉은 …" / "이직 목표 세전 연봉은 …").
 */
export const GROSS_SALARY_RANGE_TEXT = "1천만 원~30억 원 사이로 입력해 주세요.";

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

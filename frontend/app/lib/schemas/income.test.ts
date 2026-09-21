import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  INCOME_CHANGE_REASONS,
  incomeChangeReasonCell,
  incomeChangeReasonLabel,
  incomeSchema,
} from "./income";

describe("incomeSchema", () => {
  const valid = {
    grossSalary: 50_000_000,
    netSalary: 3_500_000,
    effectiveDate: "2026-01-15",
  };

  beforeEach(() => {
    // 2026-09-10 을 오늘로 고정한다. 아래 "당월 이내" 케이스가 실행일에 따라
    // 흔들리지 않도록 하기 위함이다.
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 8, 10));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("정상 입력을 통과시킨다", () => {
    expect(incomeSchema.safeParse(valid).success).toBe(true);
  });

  it("세전 연봉은 1,000만~30억 원이다", () => {
    expect(
      incomeSchema.safeParse({ ...valid, grossSalary: 9_999_999 }).success,
    ).toBe(false);
    expect(
      incomeSchema.safeParse({ ...valid, grossSalary: 10_000_000 }).success,
    ).toBe(true);
    expect(
      incomeSchema.safeParse({ ...valid, grossSalary: 3_000_000_000 }).success,
    ).toBe(true);
    expect(
      incomeSchema.safeParse({ ...valid, grossSalary: 3_000_000_001 }).success,
    ).toBe(false);
  });

  it("월 실수령액은 50만~2억 원이고 필수다", () => {
    expect(
      incomeSchema.safeParse({ ...valid, netSalary: 499_999 }).success,
    ).toBe(false);
    expect(
      incomeSchema.safeParse({ ...valid, netSalary: 500_000 }).success,
    ).toBe(true);
    expect(
      incomeSchema.safeParse({ ...valid, netSalary: 200_000_000 }).success,
    ).toBe(true);
    expect(
      incomeSchema.safeParse({ ...valid, netSalary: 200_000_001 }).success,
    ).toBe(false);

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { netSalary: _drop, ...withoutNetSalary } = valid;
    expect(incomeSchema.safeParse(withoutNetSalary).success).toBe(false);
  });

  it("유효 시작일은 과거~당월 말일까지 허용하고 다음달부터 거부한다", () => {
    // 오늘(9/10)보다 늦지만 같은 달인 9/25 — "당월 이내"와 "오늘까지"를 가르는 케이스.
    expect(
      incomeSchema.safeParse({ ...valid, effectiveDate: "2026-09-25" }).success,
    ).toBe(true);
    // 당월 말일
    expect(
      incomeSchema.safeParse({ ...valid, effectiveDate: "2026-09-30" }).success,
    ).toBe(true);
    // 다음달 첫날은 거부
    expect(
      incomeSchema.safeParse({ ...valid, effectiveDate: "2026-10-01" }).success,
    ).toBe(false);
    // 과거 날짜는 허용
    expect(
      incomeSchema.safeParse({ ...valid, effectiveDate: "2020-01-01" }).success,
    ).toBe(true);
    // 형식이 틀리면 거부
    expect(
      incomeSchema.safeParse({ ...valid, effectiveDate: "2026/09/10" }).success,
    ).toBe(false);
  });
});

describe("changeReason", () => {
  const base = {
    grossSalary: 50_000_000,
    netSalary: 3_500_000,
    effectiveDate: "2024-01-01",
  };

  it("미지정을 허용한다", () => {
    expect(incomeSchema.safeParse(base).success).toBe(true);
  });

  it("RAISE 와 JOB_CHANGE 를 허용한다", () => {
    expect(
      incomeSchema.safeParse({ ...base, changeReason: "RAISE" }).success,
    ).toBe(true);
    expect(
      incomeSchema.safeParse({ ...base, changeReason: "JOB_CHANGE" }).success,
    ).toBe(true);
  });

  it("INITIAL 은 거부한다 - 서버가 판정하는 값이라 폼이 보낼 수 없다", () => {
    expect(
      incomeSchema.safeParse({ ...base, changeReason: "INITIAL" }).success,
    ).toBe(false);
  });

  it("표시 라벨은 값 목록과 같은 문구를 쓴다", () => {
    expect(incomeChangeReasonLabel("INITIAL")).toBe("최초 입력");
    expect(incomeChangeReasonLabel("RAISE")).toBe(
      INCOME_CHANGE_REASONS[0].label,
    );
    expect(incomeChangeReasonLabel(null)).toBe("—");
  });

  describe("incomeChangeReasonCell", () => {
    it("최소 유효일 행에 고른 값이 있으면 둘 다 낸다", () => {
      // INITIAL 은 파생 표시값이고 JOB_CHANGE 는 사용자가 실제로 고른 값이다.
      // 파생값만 내면 사용자 선택이 화면 어디에도 안 남는다.
      expect(incomeChangeReasonCell("INITIAL", "JOB_CHANGE")).toBe(
        "최초 입력 · 이직",
      );
    });

    it("최소 유효일 행이어도 고른 값이 없으면 최초 입력만 낸다", () => {
      expect(incomeChangeReasonCell("INITIAL", null)).toBe("최초 입력");
    });

    it("최소 유효일 행이 아니면 고른 값 하나만 낸다", () => {
      // 이 경우 두 필드는 같은 값이라 붙일 것이 없다.
      expect(incomeChangeReasonCell("RAISE", "RAISE")).toBe("연봉 인상");
    });

    it("아무것도 안 골랐으면 대시를 낸다", () => {
      expect(incomeChangeReasonCell(null, null)).toBe("—");
    });
  });
});

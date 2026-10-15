import { describe, expect, it } from "vitest";

import { toFieldErrors } from "./fieldErrors";

describe("toFieldErrors", () => {
  const messages = {
    jobFamily: "주 직무를 선택해 주세요.",
    yearsOfExperience: "총 경력은 0~50 사이의 정수로 입력해 주세요.",
  };

  it("매핑된 경로는 필드별 메시지로 옮긴다", () => {
    const errors = toFieldErrors(
      [{ path: ["jobFamily"] }, { path: ["yearsOfExperience"] }],
      messages,
    );

    expect(errors).toEqual({
      jobFamily: "주 직무를 선택해 주세요.",
      yearsOfExperience: "총 경력은 0~50 사이의 정수로 입력해 주세요.",
    });
  });

  it("같은 필드의 두 번째 이슈는 버린다(첫 번째 매핑만 대표로 남긴다)", () => {
    const errors = toFieldErrors(
      [{ path: ["jobFamily"] }, { path: ["jobFamily"] }],
      messages,
    );

    expect(Object.keys(errors)).toEqual(["jobFamily"]);
  });

  // FIX 3 회귀 테스트 — 매핑에 없는 경로(반복 필드를 가진 화면의 `items.0.amount`
  // 같은 경우)를 조용히 버리면 저장 버튼이 죽은 채로 남는다. 이 프로필 화면 자체는
  // 스키마 키와 메시지 맵 키가 정확히 일치해 이 경로를 절대 타지 않으므로, 여기서
  // 직접 단위 테스트로 검증한다.
  it("매핑에 없는 경로는 조용히 버리지 않고 _form 오류로 올린다", () => {
    const errors = toFieldErrors([{ path: ["items", 0, "amount"] }], messages);

    expect(errors._form).toBeTruthy();
    expect(errors.items).toBeUndefined();
  });

  it("매핑에 없는 경로가 여러 개여도 _form 은 한 번만 채운다", () => {
    const errors = toFieldErrors(
      [{ path: ["items", 0, "amount"] }, { path: ["items", 1, "amount"] }],
      messages,
    );

    expect(errors._form).toBeTruthy();
  });

  // 경로가 없는 이슈(예: 여러 필드를 가로지르는 zod .refine() 실패)는 특정
  // 필드에 붙일 자리가 없다 — 조용히 버리면 매핑 안 된 경로와 똑같이 저장
  // 버튼만 죽은 채로 남으므로 이것도 _form 으로 올린다.
  it("경로가 없는(전체 폼) 이슈는 _form 오류로 올린다", () => {
    const errors = toFieldErrors([{ path: [] }], messages);

    expect(errors).toEqual({ _form: expect.any(String) });
  });

  // FIX A 회귀 테스트 — path[0] 만 보면 ["items", 0, "amount"] 와
  // ["items", 1, "amount"] 를 구분하지 못해 둘 다 "items" 라는 같은 키로
  // 뭉개지거나(둘 다 unmapped 라 _form 으로) 첫 이슈만 남고 나머지는 버려졌다.
  // 전체 경로를 합친 키(`items.0.amount`)로 매핑 테이블에 있는 메시지를 찾을 수
  // 있어야 반복 필드(소득·소비 화면의 행별 입력)에 인라인 오류를 붙일 수 있다.
  it("중첩 경로는 합쳐진 키로 매핑되고 _form 으로 뭉개지지 않는다", () => {
    const errors = toFieldErrors([{ path: ["items", 0, "amount"] }], {
      ...messages,
      "items.0.amount": "금액을 입력해 주세요.",
    });

    expect(errors).toEqual({ "items.0.amount": "금액을 입력해 주세요." });
    expect(errors._form).toBeUndefined();
  });

  it("서로 다른 행의 같은 필드는 각자의 키로 구분된다", () => {
    const rowMessages = {
      "items.0.amount": "0번째 금액을 입력해 주세요.",
      "items.1.amount": "1번째 금액을 입력해 주세요.",
    };
    const errors = toFieldErrors(
      [{ path: ["items", 0, "amount"] }, { path: ["items", 1, "amount"] }],
      rowMessages,
    );

    expect(errors).toEqual({
      "items.0.amount": "0번째 금액을 입력해 주세요.",
      "items.1.amount": "1번째 금액을 입력해 주세요.",
    });
  });

  // 단일 세그먼트 경로(이 프로필 화면이 쓰는 모양)는 이전과 똑같은 평평한 키를
  // 만들어야 한다 — join(".") 으로 바뀌었어도 회귀가 없어야 한다.
  it("단일 세그먼트 경로는 이전과 같은 평평한 키를 만든다", () => {
    const errors = toFieldErrors([{ path: ["jobFamily"] }], messages);

    expect(errors).toEqual({ jobFamily: "주 직무를 선택해 주세요." });
  });

  it("symbol 세그먼트가 섞인 경로는 키를 만들지 않고 _form 으로 떨어진다", () => {
    const errors = toFieldErrors([{ path: [Symbol("weird")] }], messages);

    expect(errors).toEqual({ _form: expect.any(String) });
  });
});

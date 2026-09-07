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

  it("경로가 없는(전체 폼) 이슈는 무시한다", () => {
    const errors = toFieldErrors([{ path: [] }], messages);

    expect(errors).toEqual({});
  });
});

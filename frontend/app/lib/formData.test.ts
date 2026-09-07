import { describe, expect, it } from "vitest";
import { formDataToObject } from "./formData";

function toFormData(entries: [string, string][]): FormData {
  const formData = new FormData();
  for (const [key, value] of entries) formData.append(key, value);
  return formData;
}

describe("formDataToObject", () => {
  it("빈 문자열은 키 자체를 만들지 않는다", () => {
    const formData = toFormData([
      ["jobFamily", "DEVELOPER"],
      ["region", ""],
    ]);

    expect(formDataToObject(formData)).toEqual({ jobFamily: "DEVELOPER" });
  });

  it("intent 는 기본으로 제외한다", () => {
    const formData = toFormData([
      ["intent", "next"],
      ["jobFamily", "DEVELOPER"],
    ]);

    expect(formDataToObject(formData)).toEqual({ jobFamily: "DEVELOPER" });
  });

  it("numeric 목록의 필드는 숫자로 파싱한다", () => {
    const formData = toFormData([["yearsOfExperience", "5"]]);

    expect(
      formDataToObject(formData, { numeric: ["yearsOfExperience"] }),
    ).toEqual({ yearsOfExperience: 5 });
  });

  it("numeric 필드가 숫자로 파싱되지 않으면(NaN) 키를 만들지 않는다", () => {
    const formData = toFormData([["yearsOfExperience", "abc"]]);

    expect(
      formDataToObject(formData, { numeric: ["yearsOfExperience"] }),
    ).toEqual({});
  });

  it("반복 필드(동적 이름)도 그대로 옮긴다 — 스키마 키를 미리 몰라도 된다", () => {
    const formData = toFormData([
      ["items.0.amount", "10000"],
      ["items.1.amount", "20000"],
    ]);

    expect(
      formDataToObject(formData, {
        numeric: ["items.0.amount", "items.1.amount"],
      }),
    ).toEqual({ "items.0.amount": 10000, "items.1.amount": 20000 });
  });

  it("exclude 를 직접 지정하면 기본값(intent) 대신 그것만 제외한다", () => {
    const formData = toFormData([
      ["intent", "next"],
      ["token", "secret"],
    ]);

    expect(formDataToObject(formData, { exclude: ["token"] })).toEqual({
      intent: "next",
    });
  });
});

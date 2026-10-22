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
});
